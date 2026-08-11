package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fires {@link PlaceboEvents#LIVING_SHIELD_BLOCK} on Fabric. NeoForge gets the same event from its own
 * {@code LivingShieldBlockEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * NeoForge fires immediately after {@code BlocksAttacks#resolveBlockedDamage} and then uses the event's figure
 * for the rest of the method, so modifying that call's value is the same thing. There is exactly one such call
 * in {@code applyItemBlocking}, checked against the 26.2 bytecode.
 * <p>
 * NeoForge's patch does two further things this does not: it can suppress the block entirely, and it passes a
 * listener-chosen durability cost to {@code hurtBlockingItem}. Both reach into NeoForge's own damage pipeline
 * and neither is used by anything in this stack, so the common event is sized to the value both loaders can
 * agree on.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityShieldBlockMixin {

    @ModifyExpressionValue(
        method = "applyItemBlocking",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"),
        remap = false)
    private float placebo$fireShieldBlockEvent(float blockedDamage, @Local(argsOnly = true) DamageSource source) {
        return PlaceboEvents.fireShieldBlock((LivingEntity) (Object) this, source, blockedDamage);
    }

}
