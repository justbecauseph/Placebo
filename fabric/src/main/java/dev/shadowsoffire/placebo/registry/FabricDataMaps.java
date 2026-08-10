package dev.shadowsoffire.placebo.registry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.datamap.DataMap;
import dev.shadowsoffire.placebo.datamap.DataMapSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import dev.architectury.registry.ReloadListenerRegistry;

/**
 * {@link DeferredHelper.DataMapFactory} for Fabric, which has no data maps at all -- so unlike most of this
 * stack's platform halves, this one is an implementation rather than an adapter.
 * <p>
 * It reads NeoForge's file layout and format deliberately:
 * {@code data/<namespace>/data_maps/<registry>/<name>.json} holding {@code {"replace": ?, "values": {...},
 * "remove": [...]}}. The alternative is a second copy of the same three files in a Placebo-shaped format,
 * which is more work and one more thing to keep in step.
 * <p>
 * <b>What is implemented:</b> loading, replace, and the list form of remove. NeoForge's advanced data maps
 * -- the object form of {@code remove} that runs a custom remover, and per-entry merge behaviour -- are
 * not, because nothing in this stack declares one. They fail loudly rather than being ignored.
 * <p>
 * <b>Not implemented: sync.</b> One of this stack's three maps is declared {@code synced}, and on a
 * dedicated server a Fabric client will read no value for it and fall back. See the class-level note on
 * {@link #SYNCED_UNSUPPORTED}.
 */
public class FabricDataMaps implements DeferredHelper.DataMapFactory {

    /**
     * A datapack is server-side, so a client connected to a dedicated server has no copy of these files.
     * NeoForge sends declared-synced maps during configuration; nothing equivalent is wired here yet, so a
     * synced map reads empty on such a client and every lookup falls back.
     * <p>
     * Recorded as a field rather than a comment because the consequence is invisible in single-player,
     * where the client shares the integrated server's data and everything reads correctly.
     */
    public static final String SYNCED_UNSUPPORTED =
        "Data map {} is declared synced; Placebo's Fabric implementation does not sync yet, so clients "
            + "connected to a dedicated server will read no value for it (P2-B-31).";

    /** Every map declared so far, so the reload listener can fill them. */
    private static final List<Loaded<?, ?>> DECLARED = new ArrayList<>();

    @Override
    public <K, V> DataMap<K, V> create(DeferredHelper owner, Identifier id, DataMapSpec<K, V> spec) {
        if (spec.networkCodec().isPresent()) {
            Placebo.LOGGER.warn(SYNCED_UNSUPPORTED, id);
        }
        Loaded<K, V> map = new Loaded<>(id, spec);
        DECLARED.add(map);
        return map;
    }

    /**
     * Hooks the loader into datapack reloads. Called once from the entrypoint.
     * <p>
     * The dynamic registries declare a dependency on this listener, so anything deserialized there sees
     * loaded values rather than an empty map -- see {@code FabricDynReg#registerReloadListener}.
     */
    public static void registerReloadListener() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SimplePreparableReloadListener<Void>() {

            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                for (Loaded<?, ?> map : DECLARED) {
                    map.reload(manager);
                }
            }
        }, ID);
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Placebo.MODID, "data_maps");

    /**
     * NeoForge's folder rule: the registry's path, prefixed by its namespace unless that is `minecraft`.
     */
    private static String folder(ResourceKey<? extends Registry<?>> registry) {
        Identifier id = registry.identifier();
        return (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? "" : id.getNamespace() + "/") + id.getPath();
    }

    private static class Loaded<K, V> implements DataMap<K, V> {

        private final Identifier id;
        private final DataMapSpec<K, V> spec;
        private final Codec<Map<Identifier, V>> valuesCodec;
        private volatile Map<Identifier, V> values = Map.of();

        Loaded(Identifier id, DataMapSpec<K, V> spec) {
            this.id = id;
            this.spec = spec;
            this.valuesCodec = Codec.unboundedMap(Identifier.CODEC, spec.codec());
        }

        @Override
        @Nullable
        public V get(HolderLookup.RegistryLookup<K> lookup, ResourceKey<K> key) {
            // The lookup is what NeoForge needs to find its per-registry storage; here the values are
            // already ours, so the key alone answers it.
            return this.values.get(key.identifier());
        }

        @Override
        @Nullable
        public V get(Holder<K> holder) {
            return holder.unwrapKey().map(k -> this.values.get(k.identifier())).orElse(null);
        }

        void reload(ResourceManager manager) {
            var converter = FileToIdConverter.json("data_maps/" + folder(this.spec.registry()));
            Map<Identifier, V> merged = new ConcurrentHashMap<>();

            // Every pack that declares this map, lowest priority first, so later packs win.
            for (var entry : converter.listMatchingResourceStacks(manager).entrySet()) {
                if (!converter.fileToId(entry.getKey()).equals(this.id)) continue;
                for (Resource resource : entry.getValue()) {
                    try (Reader reader = resource.openAsReader()) {
                        this.readOne(JsonParser.parseReader(reader), merged);
                    }
                    catch (Exception ex) {
                        Placebo.LOGGER.error("Failed to read data map {} from {}: {}",
                            this.id, resource.sourcePackId(), ex.toString());
                    }
                }
            }

            this.values = Map.copyOf(merged);
            Placebo.LOGGER.debug("Loaded {} value(s) for data map {}.", this.values.size(), this.id);
        }

        private void readOne(JsonElement json, Map<Identifier, V> into) {
            var obj = json.getAsJsonObject();

            if (obj.has("replace") && obj.get("replace").getAsBoolean()) {
                into.clear();
            }

            if (obj.has("values")) {
                into.putAll(this.valuesCodec.parse(JsonOps.INSTANCE, obj.get("values")).getOrThrow());
            }

            if (obj.has("remove")) {
                var remove = obj.get("remove");
                if (!remove.isJsonArray()) {
                    // NeoForge's object form runs a custom remover. Nothing here declares one, and
                    // silently ignoring a removal would leave data in the map that the pack asked to
                    // take out -- worse than refusing to load it.
                    throw new UnsupportedOperationException(
                        "Data map " + this.id + " uses the object form of `remove`, which needs a custom "
                            + "remover; only the list form is implemented.");
                }
                for (JsonElement element : remove.getAsJsonArray()) {
                    into.remove(Identifier.parse(element.getAsString()));
                }
            }
        }
    }

    /** Only for tests and diagnostics: the maps declared so far. */
    public static List<Identifier> declared() {
        return DECLARED.stream().map(m -> m.id).toList();
    }


}
