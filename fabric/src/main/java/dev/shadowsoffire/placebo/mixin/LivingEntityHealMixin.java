package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.shadowsoffire.placebo.events.FabricHealDispatcher;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fires {@link PlaceboEvents#LIVING_HEAL} on Fabric. NeoForge gets the same event from its own
 * {@code LivingHealEvent} via {@code NeoForgeEventBridge}, so no mixin is needed there.
 * <p>
 * {@code remap = false} is required, not optional: 26.2 ships deobfuscated, so there is no mapping for the
 * annotation processor to resolve and it fails the build without it. This is the first direct confirmation
 * that the stack's existing {@code remap = false} annotations stay correct on Fabric -- see
 * {@code porting/reference/toolchain-26.2.md}.
 * <p>
 * A cancelled heal is represented as an amount of {@code 0} rather than by cancelling the method. That is
 * faithful: vanilla {@code heal} has no amount guard, so it would run {@code setHealth(getHealth() + 0)},
 * which sets the health it already had. Doing it this way keeps the whole thing to one injection with no
 * cross-method state. The dispatcher selects the direct owned chain or one complete public fallback invocation
 * before returning this value.
 */
@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityHealMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float placebo$fireHealEvent(float amount) {
        return FabricHealDispatcher.apply((LivingEntity) (Object) this, amount);
    }

}
