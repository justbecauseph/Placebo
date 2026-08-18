package dev.shadowsoffire.placebo.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

@Mixin(value = SpreadPlayersCommand.class, remap = false)
public class SpreadPlayersCommandMixin {

    @WrapOperation(method = "setPlayerPositions", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean placebo$fireSpreadPlayersTeleport(Entity entity, ServerLevel level, double x, double y, double z,
        Set<Relative> relatives, float yRot, float xRot, boolean resetCamera, Operation<Boolean> original) {
        PlaceboEvents.EntityTeleportContext ctx = new PlaceboEvents.EntityTeleportContext(entity, level, x, y, z);
        return PlaceboEvents.fireEntityTeleport(ctx)
            && original.call(entity, level, ctx.getTargetX(), ctx.getTargetY(), ctx.getTargetZ(), relatives, yRot, xRot, resetCamera);
    }
}
