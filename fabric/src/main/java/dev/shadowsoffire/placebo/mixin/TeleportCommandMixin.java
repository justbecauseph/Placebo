package dev.shadowsoffire.placebo.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

@Mixin(value = TeleportCommand.class, remap = false)
public class TeleportCommandMixin {

    @WrapMethod(method = "performTeleport")
    private static void placebo$fireTeleportCommand(CommandSourceStack source, Entity entity, ServerLevel level,
        double x, double y, double z, Set<Relative> relatives, float yRot, float xRot, LookAt lookAt,
        Operation<Void> original) {
        PlaceboEvents.EntityTeleportContext ctx = new PlaceboEvents.EntityTeleportContext(entity, level, x, y, z);
        if (PlaceboEvents.fireEntityTeleport(ctx)) {
            original.call(source, entity, level, ctx.getTargetX(), ctx.getTargetY(), ctx.getTargetZ(), relatives, yRot, xRot, lookAt);
        }
    }
}
