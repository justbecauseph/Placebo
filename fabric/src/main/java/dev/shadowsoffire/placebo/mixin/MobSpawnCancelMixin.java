package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import dev.shadowsoffire.placebo.util.SpawnCancelable;
import net.minecraft.world.entity.Mob;

/**
 * Adds the spawn-cancelled flag NeoForge patches onto {@code Mob}, so that a listener on
 * {@code PlaceboEvents.FINALIZE_SPAWN} can stop a mob reaching the world on Fabric too.
 * <p>
 * A field is the whole implementation here; the check that acts on it is registered in {@code PlaceboFabric}
 * against Architectury's {@code EntityEvent.ADD}, which is the loader-neutral spelling of the
 * {@code EntityJoinLevelEvent} handler NeoForge uses for the same purpose.
 */
@Mixin(value = Mob.class, remap = false)
public abstract class MobSpawnCancelMixin implements SpawnCancelable {

    @Unique
    private boolean placebo$spawnCancelled;

    @Override
    public boolean placebo$isSpawnCancelled() {
        return this.placebo$spawnCancelled;
    }

    @Override
    public void placebo$setSpawnCancelled(boolean cancelled) {
        this.placebo$spawnCancelled = cancelled;
    }

}
