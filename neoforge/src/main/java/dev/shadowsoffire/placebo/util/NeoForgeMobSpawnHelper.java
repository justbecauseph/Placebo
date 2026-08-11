package dev.shadowsoffire.placebo.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.BaseSpawner;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;

/**
 * {@link MobSpawnHelper.Impl} for NeoForge. Goes through {@code EventHooks} rather than calling vanilla, so
 * {@code FinalizeSpawnEvent} still fires for everyone listening to it.
 */
public class NeoForgeMobSpawnHelper implements MobSpawnHelper.Impl {

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
        EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        return EventHooks.finalizeMobSpawn(mob, level, difficulty, reason, data);
    }

    /**
     * Posts {@code PositionCheck} so other mods can force or deny the spawn, mapping its tri-state onto the
     * nullable Boolean the common seam uses.
     */
    @Override
    @Nullable
    public Boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason, BaseSpawner spawner) {
        PositionCheck event = new PositionCheck(mob, level, reason, spawner);
        NeoForge.EVENT_BUS.post(event);
        return switch (event.getResult()) {
            case DEFAULT -> null;
            case SUCCEED -> Boolean.TRUE;
            case FAIL -> Boolean.FALSE;
        };
    }

    @Override
    public boolean isSpawnCancelled(Mob mob) {
        return mob.isSpawnCancelled();
    }

    /**
     * NeoForge throws if the mob is already in the world, since the flag is only read on the way in. Left to
     * throw rather than swallowed: a listener setting this too late has a bug either way, and on Fabric the
     * same call would silently do nothing.
     */
    @Override
    public void setSpawnCancelled(Mob mob, boolean cancelled) {
        mob.setSpawnCancelled(cancelled);
    }

}
