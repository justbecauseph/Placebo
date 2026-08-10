package dev.shadowsoffire.placebo.datamap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Extra data attached to the entries of a registry, loaded from datapacks.
 * <p>
 * NeoForge calls this a data map and Fabric has no equivalent at all, so unlike most seams in this stack
 * this one has a real implementation behind it on the Fabric side rather than a rename. The file format is
 * NeoForge's, deliberately -- {@code data/<namespace>/data_maps/<registry>/<name>.json} holding
 * {@code {"values": {...}}} -- because the data ships once and both loaders read it. Diverging the format
 * would mean maintaining two copies of the same three files.
 * <p>
 * The whole stack uses three of these, with one read site each, and the interface is sized to those three:
 * a lookup-and-key form for when the caller is already holding a registry lookup, and a holder form for
 * when it is not.
 */
public interface DataMap<K, V> {

    /**
     * The value for {@code key}, or null.
     *
     * @param lookup The registry the key belongs to. Needed because a dynamic registry's data lives with
     *               the {@code RegistryAccess} it was loaded into, and callers with a level already hold
     *               one; {@link Registry} implements this interface, so a static registry passes directly.
     */
    @Nullable
    V get(HolderLookup.RegistryLookup<K> lookup, ResourceKey<K> key);

    /**
     * The value for {@code holder}, or null -- including when the holder is unbound, since an unbound
     * holder names no registry entry to attach data to.
     */
    @Nullable
    V get(Holder<K> holder);

}
