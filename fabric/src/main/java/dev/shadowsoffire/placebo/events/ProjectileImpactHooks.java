package dev.shadowsoffire.placebo.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;

/** Shared Fabric implementation for the seven {@code ProjectileImpactEvent} patch shapes. */
public final class ProjectileImpactHooks {

    private ProjectileImpactHooks() {}

    public static ProjectileDeflection fire(Projectile projectile, HitResult hitResult, Operation<ProjectileDeflection> original) {
        if (hitResult.getType() == HitResult.Type.MISS || !PlaceboEvents.fireProjectileImpact(projectile, hitResult)) {
            return original.call(projectile, hitResult);
        }
        return ProjectileDeflection.NONE;
    }

    /**
     * Arrow's collection path uses the returned deflection to decide whether to inspect another target.
     * Returning a non-NONE sentinel skips the hit and stops that sweep, exactly like NeoForge's cancelled
     * event branch; no deflection is actually applied because the original call is never reached.
     */
    public static ProjectileDeflection fireArrow(Projectile projectile, HitResult hitResult, Operation<ProjectileDeflection> original) {
        if (hitResult.getType() == HitResult.Type.MISS || !PlaceboEvents.fireProjectileImpact(projectile, hitResult)) {
            return original.call(projectile, hitResult);
        }
        return ProjectileDeflection.REVERSE;
    }
}
