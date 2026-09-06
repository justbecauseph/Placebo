package dev.shadowsoffire.placebo.loot;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import dev.architectury.registry.ReloadListenerRegistry;
import dev.shadowsoffire.placebo.Placebo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Global loot modifiers on Fabric, which has no such concept.
 * <p>
 * This is the second platform half in Placebo that is an <i>implementation</i> rather than an adapter, after
 * data maps, and it takes the same decision for the same reason: it reads <b>NeoForge's file layout and
 * format</b>, {@code data/<ns>/loot_modifiers/<name>.json}, so the shipped data serves both loaders. A
 * Placebo-shaped format would mean a second copy of the same seven files to keep in step.
 * <p>
 * Three pieces, none of which Fabric provides:
 * <ol>
 * <li><b>Loading.</b> A reload listener over the same directory NeoForge reads.
 * <li><b>Ordering.</b> NeoForge sorts by the {@code priority} field; so does this, with the same default.
 * <li><b>Application.</b> {@code LootTableEvents.MODIFY_DROPS} is the one Fabric hook that runs at drop time
 * with a {@link net.minecraft.world.level.storage.loot.LootContext} and a mutable list, which is exactly
 * NeoForge's {@code IGlobalLootModifier#apply}. The load-time {@code MODIFY} event is a different thing and
 * could not have worked -- it edits the table, not the roll.
 * </ol>
 * <p>
 * <b>Deliberately not implemented:</b> NeoForge's {@code neoforge:conditions} gating on the modifier file
 * itself. The shipped files here do not use it, and silently ignoring a condition would enable a modifier
 * somebody had disabled -- the same failure the shipped-data work went to some length to avoid. An unknown
 * {@code type} is logged rather than skipped quietly, for the same reason.
 */
public class FabricLootModifiers {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Placebo.MODID, "loot_modifiers");

    private static final String DIRECTORY = "loot_modifiers";

    /** One immutable reload publication consumed coherently by each drop callback. */
    private static volatile ActiveGeneration active = ActiveGeneration.EMPTY;

    public static void install() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SimplePreparableReloadListener<ActiveGeneration>() {

            @Override
            protected ActiveGeneration prepare(ResourceManager manager, ProfilerFiller profiler) {
                return load(manager);
            }

            @Override
            protected void apply(ActiveGeneration loaded, ResourceManager manager, ProfilerFiller profiler) {
                active = loaded;
            }
        }, ID);

        LootTableEvents.MODIFY_DROPS.register(FabricLootModifiers::applyModifiers);
    }

    /**
     * Stable named Fabric callback. Keep table-id publication at this callback's logical entry point;
     * Apotheosis's Fabric mixin relies on that ordering before any modifier condition or application runs.
     * An opt-in modifier that returns a different list is reconciled into the supplied list (the returned
     * contents win); returning {@code null} remains an error and throws, as the compatibility path does.
     */
    public static void applyModifiers(net.minecraft.core.Holder<net.minecraft.world.level.storage.loot.LootTable> holder,
        net.minecraft.world.level.storage.loot.LootContext context, List<ItemStack> drops) {
        ActiveGeneration generation = active;
        applyGeneration(generation, context, drops);
    }

    /**
     * Test seam for the application contract. It lets Fabric-side tests inject a known reload generation and
     * assert list identity and replacement behavior without inferring allocation from timing.
     */
    static void applyForTesting(List<LootModifier> modifiers, boolean allInPlace, LootContext context, List<ItemStack> drops) {
        applyGeneration(new ActiveGeneration(modifiers, allInPlace), context, drops);
    }

    private static void applyGeneration(ActiveGeneration generation, LootContext context, List<ItemStack> drops) {
        if (generation.modifiers().isEmpty()) {
            return;
        }

        if (generation.allInPlace() && drops instanceof ObjectArrayList<?>) {
            @SuppressWarnings("unchecked")
            ObjectArrayList<ItemStack> loot = (ObjectArrayList<ItemStack>) drops;
            for (LootModifier modifier : generation.modifiers()) {
                ObjectArrayList<ItemStack> result = modifier.apply(loot, context);
                if (result != loot) {
                    // The opt-in contract was violated. Snapshot before clearing so a pathological returned
                    // view cannot be invalidated by clearing the supplied list; the returned result wins,
                    // matching the normal replacement-list contract.
                    ObjectArrayList<ItemStack> replacement = new ObjectArrayList<>(java.util.Objects.requireNonNull(result,
                        "A loot modifier returned null"));
                    loot.clear();
                    loot.addAll(replacement);
                }
            }
            return;
        }

        // Compatibility path: Fabric hands us a List and modifiers may replace the ObjectArrayList wholesale.
        // Preserve the existing copy/apply/clear/add round trip whenever any modifier does not opt in
        // or the event supplies a non-ObjectArrayList implementation.
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(drops);
        for (LootModifier modifier : generation.modifiers()) {
            loot = modifier.apply(loot, context);
        }
        drops.clear();
        drops.addAll(loot);
    }

    private static ActiveGeneration load(ResourceManager manager) {
        List<LootModifier> loaded = loadModifiers(manager);
        boolean allInPlace = loaded.stream().allMatch(LootModifier::inPlaceOnly);
        return new ActiveGeneration(loaded, allInPlace);
    }

    private static List<LootModifier> loadModifiers(ResourceManager manager) {
        List<LootModifier> loaded = new ArrayList<>();
        Map<Identifier, Resource> files = manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Identifier file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                Identifier type = typeOf(json, file);
                if (type == null) {
                    continue;
                }
                MapCodec<? extends LootModifier> codec = LootModifierTypes.get(type);
                if (codec == null) {
                    Placebo.LOGGER.error("Loot modifier {} names unknown type {}; it will not apply.", file, type);
                    continue;
                }
                DataResult<? extends LootModifier> result = codec.codec().parse(JsonOps.INSTANCE, json);
                result.resultOrPartial(error -> Placebo.LOGGER.error("Failed to parse loot modifier {}: {}", file, error))
                    .ifPresent(loaded::add);
            }
            catch (Exception ex) {
                Placebo.LOGGER.error("Failed to read loot modifier {}", file, ex);
            }
        }

        loaded.sort(Comparator.comparingInt(LootModifier::priority));
        return List.copyOf(loaded);
    }

    private record ActiveGeneration(List<LootModifier> modifiers, boolean allInPlace) {

        private static final ActiveGeneration EMPTY = new ActiveGeneration(List.of(), true);

        private ActiveGeneration {
            modifiers = List.copyOf(modifiers);
        }
    }

    private static Identifier typeOf(JsonElement json, Identifier file) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
            Placebo.LOGGER.error("Loot modifier {} has no type field.", file);
            return null;
        }
        return Identifier.tryParse(json.getAsJsonObject().get("type").getAsString());
    }

}
