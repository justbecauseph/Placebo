package dev.shadowsoffire.placebo.datamap;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * How one {@link DataMap} is declared: what registry it attaches to, how its values are read, and whether
 * they are sent to clients.
 * <p>
 * Mutable and configured through a {@link java.util.function.UnaryOperator} at declaration, matching the
 * shape {@code RegistrySpec} already uses in this codebase.
 */
public class DataMapSpec<K, V> {

    private final ResourceKey<? extends Registry<K>> registry;
    private final Codec<V> codec;

    @Nullable
    private Codec<V> networkCodec;
    private boolean mandatorySync;

    public DataMapSpec(ResourceKey<? extends Registry<K>> registry, Codec<V> codec) {
        this.registry = registry;
        this.codec = codec;
    }

    /**
     * Sends this map's values to clients on join.
     * <p>
     * Needed by anything a client reads: datapack data is loaded on the server, and a client connected to a
     * dedicated server has no copy of it. On an integrated server the client shares the server's, so an
     * unsynced map still reads correctly there -- which is exactly why forgetting to sync one is easy to
     * miss in single-player.
     *
     * @param networkCodec  How values cross the wire. Usually the same codec.
     * @param mandatory     Whether a client that cannot read this map should be refused the connection.
     */
    public DataMapSpec<K, V> synced(Codec<V> networkCodec, boolean mandatory) {
        this.networkCodec = networkCodec;
        this.mandatorySync = mandatory;
        return this;
    }

    public ResourceKey<? extends Registry<K>> registry() {
        return this.registry;
    }

    public Codec<V> codec() {
        return this.codec;
    }

    public Optional<Codec<V>> networkCodec() {
        return Optional.ofNullable(this.networkCodec);
    }

    public boolean mandatorySync() {
        return this.mandatorySync;
    }

}
