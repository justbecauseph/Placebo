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

    /** Sorted by priority at load, so application does no work per drop beyond the conditions. */
    private static List<LootModifier> active = List.of();

    public static void install() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SimplePreparableReloadListener<List<LootModifier>>() {

            @Override
            protected List<LootModifier> prepare(ResourceManager manager, ProfilerFiller profiler) {
                return load(manager);
            }

            @Override
            protected void apply(List<LootModifier> loaded, ResourceManager manager, ProfilerFiller profiler) {
                active = loaded;
            }
        }, ID);

        LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
            if (active.isEmpty()) {
                return;
            }
            // The event hands over a List and NeoForge's contract is an ObjectArrayList that modifiers may
            // replace wholesale, so the round trip through one is what keeps the two APIs honest with each
            // other rather than an optimisation.
            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(drops);
            for (LootModifier modifier : active) {
                loot = modifier.apply(loot, context);
            }
            drops.clear();
            drops.addAll(loot);
        });
    }

    private static List<LootModifier> load(ResourceManager manager) {
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

    private static Identifier typeOf(JsonElement json, Identifier file) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
            Placebo.LOGGER.error("Loot modifier {} has no type field.", file);
            return null;
        }
        return Identifier.tryParse(json.getAsJsonObject().get("type").getAsString());
    }

}
