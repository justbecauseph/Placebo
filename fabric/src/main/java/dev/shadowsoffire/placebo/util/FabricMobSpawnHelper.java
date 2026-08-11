package dev.shadowsoffire.placebo.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * {@link MobSpawnHelper.Impl} for Fabric. The finalize call is plain vanilla -- there is no platform event to
 * fire, and {@code PlaceboEvents.FINALIZE_SPAWN} is fired from a mixin on the vanilla method itself, so routing
 * through here would fire it twice.
 * <p>
 * The spawn-cancelled flag is backed by {@link SpawnCancelable}, which the mixin adds to {@code Mob}.
 */
public class FabricMobSpawnHelper implements MobSpawnHelper.Impl {

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
        EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        return mob.finalizeSpawn(level, difficulty, reason, data);
    }

    /**
     * Always defers. Fabric has no equivalent of NeoForge's {@code PositionCheck}, so there is nothing that
     * could have decided -- returning null sends the caller to its own checks, which is what NeoForge's
     * {@code DEFAULT} does.
     */
    @Override
    @Nullable
    public Boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason, BaseSpawner spawner) {
        return null;
    }

    @Override
    public boolean isSpawnCancelled(Mob mob) {
        return ((SpawnCancelable) mob).placebo$isSpawnCancelled();
    }

    @Override
    public void setSpawnCancelled(Mob mob, boolean cancelled) {
        ((SpawnCancelable) mob).placebo$setSpawnCancelled(cancelled);
    }

}
