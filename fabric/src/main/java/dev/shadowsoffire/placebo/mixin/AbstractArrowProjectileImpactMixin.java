package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.ProjectileImpactHooks;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.HitResult;

/** Fires the impact event for both 26.2 arrow collision paths. */
@Mixin(value = AbstractArrow.class, remap = false)
public class AbstractArrowProjectileImpactMixin {

    @WrapOperation(
        method = { "tick", "hitTargetsOrDeflectSelf" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"),
        remap = false)
    private ProjectileDeflection placebo$fireProjectileImpact(AbstractArrow arrow, HitResult hitResult, Operation<ProjectileDeflection> original) {
        return ProjectileImpactHooks.fireArrow(arrow, hitResult, original);
    }
}
