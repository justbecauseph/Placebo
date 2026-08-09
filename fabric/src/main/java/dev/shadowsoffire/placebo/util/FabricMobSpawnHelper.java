package dev.shadowsoffire.placebo.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * {@link MobSpawnHelper.Impl} for Fabric -- plain vanilla, since there is no event to fire. See the interface
 * for why one is not invented here.
 */
public class FabricMobSpawnHelper implements MobSpawnHelper.Impl {

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
        EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        return mob.finalizeSpawn(level, difficulty, reason, data);
    }

}
