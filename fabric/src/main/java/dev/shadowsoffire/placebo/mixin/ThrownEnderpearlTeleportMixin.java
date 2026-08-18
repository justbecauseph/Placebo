package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.HitResult;

@Mixin(value = ThrownEnderpearl.class, remap = false)
public class ThrownEnderpearlTeleportMixin {

    @Unique
    private PlaceboEvents.EntityTeleportContext placebo$teleportContext;

    @Inject(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F", ordinal = 0), cancellable = true)
    private void placebo$fireEnderPearlTeleport(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        if (pearl.getOwner() instanceof ServerPlayer player && pearl.level() instanceof ServerLevel level) {
            this.placebo$teleportContext = new PlaceboEvents.EntityTeleportContext(player, level, pearl.getX(), pearl.getY(), pearl.getZ());
            if (!PlaceboEvents.fireEntityTeleport(this.placebo$teleportContext)) {
                pearl.discard();
                ci.cancel();
            }
        }
    }

    @ModifyArg(method = "onHit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"))
    private TeleportTransition placebo$applyEnderPearlTarget(TeleportTransition transition) {
        PlaceboEvents.EntityTeleportContext ctx = this.placebo$teleportContext;
        this.placebo$teleportContext = null;
        return ctx == null ? transition : transition.withPosition(ctx.getTarget());
    }
}
