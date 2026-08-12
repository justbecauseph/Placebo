package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.ProjectileImpactHooks;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.HitResult;

@Mixin(value = ShulkerBullet.class, remap = false)
public class ShulkerBulletProjectileImpactMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ShulkerBullet;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"), remap = false)
    private ProjectileDeflection placebo$fireProjectileImpact(ShulkerBullet projectile, HitResult hitResult, Operation<ProjectileDeflection> original) {
        return ProjectileImpactHooks.fire(projectile, hitResult, original);
    }
}
