package dev.shadowsoffire.placebo.util;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * A per-entity NBT scratchpad that survives saving, loader-neutrally.
 * <p>
 * NeoForge adds {@code Entity#getPersistentData()} to vanilla, and this stack leans on it in 56 places for
 * ad-hoc flags -- {@code apotheosis:movable}, {@code apoth.no_pinata}, and so on. Being a NeoForge addition to
 * a vanilla class it takes no import, which is why every import-based scan in this project called those files
 * clean and only moving one into {@code :common} surfaced it.
 * <p>
 * The two loaders model this differently and the gap is real:
 * <ul>
 * <li><b>NeoForge</b> hands out one untyped {@link CompoundTag} per entity, shared by every mod.
 * <li><b>Fabric</b> has {@code fabric-data-attachment-api-v1}: typed, per-key, per-mod attachments.
 * </ul>
 * Porting 56 ad-hoc keys to typed attachments would be the better end state and a much larger change. This
 * adapter instead gives Fabric a single persistent attachment holding a {@code CompoundTag}, so the existing
 * call sites keep working verbatim. Note the consequence: on Fabric the bag is <i>Placebo's</i>, not a
 * cross-mod one, so another mod writing to its own persistent data will not be visible here. Nothing in this
 * stack reads another mod's keys.
 * <p>
 * <b>Unverified at runtime:</b> the returned tag is live on both loaders and mutating it in place is expected
 * to persist -- NeoForge saves its own tag, and Fabric serializes the attached instance on save. Fabric's docs
 * say in-place mutation of an attachment needs the target marked changed, which entities do not expose. If
 * persistence turns out to be lost across a save, the fix is to re-{@code setAttached} after each mutation,
 * which would mean this returning a wrapper rather than the raw tag.
 */
public class PersistentData {

    private static Impl impl;

    /**
     * Installed by the platform entrypoint.
     */
    public static void setImpl(Impl impl) {
        PersistentData.impl = Objects.requireNonNull(impl);
    }

    /**
     * The entity's persistent tag: live, mutable, and written out with the entity.
     */
    public static CompoundTag of(Entity entity) {
        if (impl == null) {
            throw new IllegalStateException("No PersistentData implementation has been installed. "
                + "The platform entrypoint must call PersistentData.setImpl before any entity data is read.");
        }
        return impl.of(entity);
    }

    public interface Impl {

        CompoundTag of(Entity entity);
    }

}
