package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fires {@link PlaceboEvents#LIVING_EXPERIENCE_DROP} on Fabric. NeoForge gets the same event from its own
 * {@code LivingExperienceDropEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * This wraps the {@code getExperienceReward} call <i>inside</i> {@code dropExperience} rather than
 * {@code getExperienceReward} itself, and the distinction matters: the sculk catalyst also calls that method,
 * to size its bloom. NeoForge patches only the death-drop call site, so hooking the method itself would give
 * Fabric a behaviour NeoForge does not have.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityExperienceMixin {

    @ModifyExpressionValue(
        method = "dropExperience",
        at = @At(value = "INVOKE", target = "getExperienceReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)I"),
        remap = false)
    private int placebo$fireExperienceDropEvent(int reward) {
        LivingEntity self = (LivingEntity) (Object) this;
        return PlaceboEvents.fireLivingExperienceDrop(self, self.getLastHurtByPlayer(), reward);
    }

}
