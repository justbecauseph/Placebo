package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.Entity;

/** Fires the entity tick bridge for passengers, which vanilla ticks through {@link Entity#rideTick()}. */
@Mixin(value = Entity.class, remap = false)
public class EntityRideTickMixin {

    @Inject(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER), remap = false)
    private void placebo$fireEntityTickPost(CallbackInfo ci) {
        PlaceboEvents.fireEntityTickPost((Entity) (Object) this);
    }

}
