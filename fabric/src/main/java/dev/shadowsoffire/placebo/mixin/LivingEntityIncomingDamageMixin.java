package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.IncomingDamageContext;
import dev.shadowsoffire.placebo.events.FabricDamageDispatcher;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Recreates NeoForge's mutable incoming-damage event after vanilla's immunity checks but before item
 * blocking, armor/magic reductions, absorption and health mutation.
 */
@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityIncomingDamageMixin {

    @Inject(
        method = "hurtServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"),
        cancellable = true,
        remap = false)
    private void placebo$fireIncomingDamage(ServerLevel level, DamageSource source, float damage,
        CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) LocalFloatRef damageRef) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (FabricDamageDispatcher.useDirectPath()) {
            long result = FabricDamageDispatcher.dispatch(entity, source, damageRef.get());
            if (FabricDamageDispatcher.isCancelled(result)) {
                cir.setReturnValue(false);
            }
            else {
                damageRef.set(FabricDamageDispatcher.unpackAmount(result));
            }
            return;
        }

        IncomingDamageContext ctx = new IncomingDamageContext((LivingEntity) (Object) this, source, damageRef.get());
        if (PlaceboEvents.fireIncomingDamage(ctx)) {
            cir.setReturnValue(false);
            return;
        }
        damageRef.set(ctx.getDamage());
    }

}
