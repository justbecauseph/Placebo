package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;

/**
 * Fires {@link PlaceboEvents#MOB_SPLIT} on Fabric. NeoForge gets the same event from its own
 * {@code MobSplitEvent} via {@code NeoForgeEventBridge}, which fans its child list out one at a time.
 * <p>
 * Vanilla {@code remove} spawns each child inside {@code convertTo} and discards the return value, so there is
 * no list to intercept -- observing each conversion as it happens is the whole reason
 * {@link PlaceboEvents#MOB_SPLIT} is shaped per child. Nothing here modifies the value; the hook is used to
 * read it.
 * <p>
 * Note the class lives in {@code entity.monster.cubemob} as of 26.2, and {@code Slime} itself no longer carries
 * the split logic.
 */
@Mixin(value = AbstractCubeMob.class, remap = false)
public abstract class AbstractCubeMobSplitMixin {

    @ModifyExpressionValue(
        method = "remove",
        at = @At(value = "INVOKE", target = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"),
        remap = false)
    private Mob placebo$fireMobSplitEvent(Mob child) {
        if (child != null) {
            PlaceboEvents.fireMobSplit((AbstractCubeMob) (Object) this, child);
        }
        return child;
    }

}
