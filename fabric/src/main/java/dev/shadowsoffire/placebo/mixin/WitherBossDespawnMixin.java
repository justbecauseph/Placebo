package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.util.DespawnHooks;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * The second of {@link PlaceboEvents#MOB_DESPAWN}'s two injection points. {@code WitherBoss} overrides
 * {@code checkDespawn} without calling super, so {@code MobDespawnMixin} never runs for it — NeoForge patches
 * both methods for the same reason.
 * <p>
 * Found by grepping NeoForge's patch files for the hook rather than by reading the event class, which names
 * only one site.
 */
@Mixin(value = WitherBoss.class, remap = false)
public abstract class WitherBossDespawnMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true, remap = false)
    private void placebo$fireMobDespawn(CallbackInfo ci) {
        if (DespawnHooks.applyDespawnResult((Mob) (Object) this)) {
            ci.cancel();
        }
    }

}
