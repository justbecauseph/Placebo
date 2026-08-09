package dev.shadowsoffire.placebo.util;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Finalizes a mob spawn, loader-neutrally.
 * <p>
 * Vanilla's {@code Mob#finalizeSpawn} exists on both loaders, but calling it directly on NeoForge would skip
 * {@code FinalizeSpawnEvent}, which other mods listen to -- so NeoForge has to keep going through
 * {@code EventHooks.finalizeMobSpawn}, whose signature is vanilla's with the mob moved to the front.
 * <p>
 * <b>Fabric fires no event here, deliberately.</b> The obvious next step would be to add a
 * {@code FINALIZE_SPAWN} to {@code PlaceboEvents} and fire it from the Fabric side, but that would produce an
 * event that fires for this stack's three call sites and not for any of vanilla's own spawns -- worse than no
 * event, because it would look complete. Covering {@code FinalizeSpawnEvent} properly means a mixin on the
 * vanilla method, and that belongs with the rest of the gap-event work.
 */
public class MobSpawnHelper {

    private static Impl impl;

    /**
     * Installed by the platform entrypoint.
     */
    public static void setImpl(Impl impl) {
        MobSpawnHelper.impl = Objects.requireNonNull(impl);
    }

    /**
     * Runs the mob's spawn finalization -- the pass that gives it equipment, difficulty scaling and so on.
     *
     * @return the spawn group data to carry to the next mob in the group, as vanilla does.
     */
    @Nullable
    public static SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
        EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        if (impl == null) {
            throw new IllegalStateException("No MobSpawnHelper implementation has been installed. "
                + "The platform entrypoint must call MobSpawnHelper.setImpl before a mob spawn is finalized.");
        }
        return impl.finalizeSpawn(mob, level, difficulty, reason, data);
    }

    public interface Impl {

        @Nullable
        SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data);
    }

}
