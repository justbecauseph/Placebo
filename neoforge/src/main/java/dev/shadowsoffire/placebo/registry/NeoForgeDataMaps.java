package dev.shadowsoffire.placebo.registry;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.placebo.datamap.DataMap;
import dev.shadowsoffire.placebo.datamap.DataMapSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * {@link DeferredHelper.DataMapFactory} over NeoForge's data maps, which is the system the format and the
 * shipped files come from -- so this is a pass-through, and the Fabric side is where the real work is.
 */
public class NeoForgeDataMaps implements DeferredHelper.DataMapFactory {

    @Override
    @SuppressWarnings("unchecked") // DataMapType asks for ResourceKey<Registry<K>> where ? extends Registry would do.
    public <K, V> DataMap<K, V> create(DeferredHelper owner, Identifier id, DataMapSpec<K, V> spec) {
        var builder = DataMapType.builder(id, (ResourceKey<Registry<K>>) spec.registry(), spec.codec());
        spec.networkCodec().ifPresent(net -> builder.synced(net, spec.mandatorySync()));
        DataMapType<K, V> type = builder.build();
        // A helper created during FML's parallel construction may predate the platform subtype factory.
        if (owner instanceof NeoForgeDeferredHelper helper) {
            helper.registerDataMap(ResourceKey.create(NeoForgeDeferredHelper.DATA_MAP_KEY, id), type);
        }
        else {
            NeoForgeRegistrationQueues.registerDataMap(owner, type);
        }
        return new Delegating<>(type);
    }

    /**
     * The NeoForge type behind a {@link DataMap}, for datagen.
     * <p>
     * Datagen is NeoForge-only by decision, and {@code DataMapProvider} is built around NeoForge's own
     * type, so a provider genuinely needs this. Nothing else should: reading a data map goes through
     * {@link DataMap} and works on both loaders.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> DataMapType<K, V> unwrap(DataMap<K, V> map) {
        if (map instanceof Delegating<K, V> delegating) {
            return delegating.type();
        }
        throw new IllegalArgumentException("Not a NeoForge-backed data map: " + map.getClass().getName());
    }

    private record Delegating<K, V>(DataMapType<K, V> type) implements DataMap<K, V> {

        @Override
        @Nullable
        public V get(HolderLookup.RegistryLookup<K> lookup, ResourceKey<K> key) {
            return lookup.getData(this.type, key);
        }

        @Override
        @Nullable
        public V get(Holder<K> holder) {
            // Only a bound holder names a registry entry; NeoForge exposes getData on Reference alone.
            return holder instanceof Holder.Reference<K> ref ? ref.getData(this.type) : null;
        }
    }

}
