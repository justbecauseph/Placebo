package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Fires the entity tick bridge for entities ticked directly by a server level. */
@Mixin(value = ServerLevel.class, remap = false)
public class ServerLevelEntityTickMixin {

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER), remap = false)
    private void placebo$fireEntityTickPost(Entity entity, CallbackInfo ci) {
        PlaceboEvents.fireEntityTickPost(entity);
    }

}
