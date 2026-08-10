package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.util.DespawnHooks;
import net.minecraft.world.entity.Mob;

/**
 * Fires {@link PlaceboEvents#MOB_DESPAWN} on Fabric. NeoForge gets the same event from its own
 * {@code MobDespawnEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * At HEAD with an early return, which is exactly where and how NeoForge patches the method. {@code WitherBoss}
 * overrides {@code checkDespawn} without calling super and gets its own patch there, so it gets its own mixin
 * here too — see {@code WitherBossDespawnMixin}. Hooking only {@code Mob} would leave withers unprotected on
 * Fabric and protected on NeoForge, which is the kind of asymmetry that only shows up in a bug report.
 */
@Mixin(value = Mob.class, remap = false)
public abstract class MobDespawnMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true, remap = false)
    private void placebo$fireMobDespawn(CallbackInfo ci) {
        if (DespawnHooks.applyDespawnResult((Mob) (Object) this)) {
            ci.cancel();
        }
    }

}
