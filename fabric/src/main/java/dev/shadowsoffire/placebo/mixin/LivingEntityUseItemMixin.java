package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fires {@link PlaceboEvents#ITEM_USE_TICK} on Fabric. NeoForge gets the same event from its own
 * {@code LivingEntityUseItemEvent.Tick} via {@code NeoForgeEventBridge}.
 * <p>
 * Placed at HEAD, writing the result straight back to {@code useItemRemaining}, which is exactly where and how
 * NeoForge patches the method -- verified against the patched 26.2 bytecode, not assumed.
 * <p>
 * The empty-stack guard is also NeoForge's: it skips the hook entirely when the stack is empty.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityUseItemMixin {

    @Shadow
    protected int useItemRemaining;

    @Inject(method = "updateUsingItem", at = @At("HEAD"), remap = false)
    private void placebo$fireItemUseTickEvent(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        this.useItemRemaining = PlaceboEvents.fireItemUseTick((LivingEntity) (Object) this, stack, this.useItemRemaining);
    }

}
