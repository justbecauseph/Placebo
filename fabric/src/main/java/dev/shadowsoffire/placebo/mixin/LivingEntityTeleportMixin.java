package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityTeleportMixin {

    @WrapMethod(method = "randomTeleport")
    private boolean placebo$fireRandomTeleport(double x, double y, double z, boolean showParticles, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return original.call(x, y, z, showParticles);
        }
        PlaceboEvents.EntityTeleportContext ctx = new PlaceboEvents.EntityTeleportContext(self, level, x, y, z);
        return PlaceboEvents.fireEntityTeleport(ctx)
            && original.call(ctx.getTargetX(), ctx.getTargetY(), ctx.getTargetZ(), showParticles);
    }
}
