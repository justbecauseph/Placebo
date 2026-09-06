package dev.shadowsoffire.placebo.util;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

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
 * The returned tag is live on both loaders. A full-stack Fabric GameTest mutates this tag in place, serializes
 * its entity, loads a new entity from that data and reads the mutation back successfully. Entity persistence
 * therefore needs neither a wrapper nor a follow-up {@code setAttached} call.
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

    /**
     * Reads the entity's persistent tag without allocating or attaching an empty tag when it is absent.
     * This is the hot-path counterpart to {@link #of(Entity)} for optional marker checks.
     */
    @Nullable
    public static CompoundTag peek(Entity entity) {
        if (impl == null) {
            throw new IllegalStateException("No PersistentData implementation has been installed. "
                + "The platform entrypoint must call PersistentData.setImpl before any entity data is read.");
        }
        return impl.peek(entity);
    }

    public interface Impl {

        CompoundTag of(Entity entity);

        /**
         * Reads optional entity data without creating it when the platform can provide a non-creating lookup.
         * <p>Third-party implementations compiled against the original two-method contract remain source
         * compatible: their default is necessarily the allocating {@link #of(Entity)} path. Fabric overrides
         * this method with its attachment lookup, which is the guarantee used by hot-path callers.
         */
        @Nullable
        default CompoundTag peek(Entity entity) {
            return of(entity);
        }
    }

}
