package dev.shadowsoffire.placebo.util;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Finalizes a mob spawn, loader-neutrally.
 * <p>
 * Vanilla's {@code Mob#finalizeSpawn} exists on both loaders, but calling it directly on NeoForge would skip
 * {@code FinalizeSpawnEvent}, which other mods listen to -- so NeoForge has to keep going through
 * {@code EventHooks.finalizeMobSpawn}, whose signature is vanilla's with the mob moved to the front.
 * <p>
 * <b>{@code PlaceboEvents.FINALIZE_SPAWN} now covers the event half</b>, from a mixin on the vanilla method on
 * <i>both</i> loaders -- NeoForge's own event turned out never to fire for natural spawns. This class is still
 * the right way to finalize a spawn you initiate yourself, because on NeoForge it also posts
 * {@code FinalizeSpawnEvent} for other mods listening to it.
 * <p>
 * It also owns the <b>spawn-cancelled flag</b>, which is a platform difference rather than an event: NeoForge
 * patches a field onto {@code Mob} that its own spawn paths honour, and Placebo supplies the equivalent on
 * Fabric.
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
        return impl().finalizeSpawn(mob, level, difficulty, reason, data);
    }

    /**
     * Asks the platform whether a spawner's placement check has been overridden.
     * <p>
     * NeoForge posts {@code MobSpawnEvent.PositionCheck}, which lets other mods force or deny a spawn.
     * Fabric has no equivalent and no listeners to lose, so it always defers. Callers run their own checks
     * when this returns null, which is exactly what NeoForge's {@code Result.DEFAULT} means.
     *
     * @return TRUE to force the spawn, FALSE to deny it, or null if nothing decided.
     */
    @Nullable
    public static Boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason, BaseSpawner spawner) {
        return impl().checkSpawnPosition(mob, level, reason, spawner);
    }

    /**
     * Whether {@code mob} has been marked as not allowed to reach the world.
     */
    public static boolean isSpawnCancelled(Mob mob) {
        return impl().isSpawnCancelled(mob);
    }

    /**
     * Marks {@code mob} as allowed or not allowed to reach the world. Used by listeners on
     * {@code PlaceboEvents.FINALIZE_SPAWN} that replace a mob with something else.
     */
    public static void setSpawnCancelled(Mob mob, boolean cancelled) {
        impl().setSpawnCancelled(mob, cancelled);
    }

    private static Impl impl() {
        if (impl == null) {
            throw new IllegalStateException("No MobSpawnHelper implementation has been installed. "
                + "The platform entrypoint must call MobSpawnHelper.setImpl before a mob spawn is finalized.");
        }
        return impl;
    }

    public interface Impl {

        @Nullable
        SpawnGroupData finalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data);

        @Nullable
        Boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason, BaseSpawner spawner);

        boolean isSpawnCancelled(Mob mob);

        void setSpawnCancelled(Mob mob, boolean cancelled);
    }

}
