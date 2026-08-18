package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Shulker;

@Mixin(value = Shulker.class, remap = false)
public class ShulkerTeleportMixin {

    @Inject(method = "teleportSomewhere", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Shulker;unRide()V"), cancellable = true)
    private void placebo$fireShulkerTeleport(CallbackInfoReturnable<Boolean> cir, @Local(index = 3) LocalRef<BlockPos> targetRef) {
        Shulker self = (Shulker) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            BlockPos target = targetRef.get();
            PlaceboEvents.EntityTeleportContext ctx = new PlaceboEvents.EntityTeleportContext(self, level,
                target.getX(), target.getY(), target.getZ());
            if (!PlaceboEvents.fireEntityTeleport(ctx)) {
                cir.setReturnValue(false);
            }
            else {
                targetRef.set(BlockPos.containing(ctx.getTargetX(), ctx.getTargetY(), ctx.getTargetZ()));
            }
        }
    }
}
