package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fires {@link PlaceboEvents#ITEM_STACKED_ON_OTHER} on Fabric. NeoForge gets the same event from its own
 * {@code ItemStackedOnOtherEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * At HEAD of {@code tryItemClickBehaviourOverride}, returning true when a listener handled the click — exactly
 * where and how NeoForge patches, including the meaning of the return value: true stops the container
 * processing any further logic for this click.
 * <p>
 * <b>Mind the parameter order.</b> Vanilla's is {@code (player, clickAction, slot, clicked, carried)}, and the
 * event takes the carried stack first. NeoForge leaves a comment on its own patch saying the same thing, which
 * is a fair sign it is easy to get backwards.
 */
@Mixin(value = AbstractContainerMenu.class, remap = false)
public abstract class AbstractContainerMenuStackedOnMixin {

    @Shadow
    private SlotAccess createCarriedSlotAccess() {
        throw new AssertionError();
    }

    @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true, remap = false)
    private void placebo$fireItemStackedOnOther(Player player, ClickAction clickAction, Slot slot, ItemStack clicked, ItemStack carried,
        CallbackInfoReturnable<Boolean> cir) {
        if (PlaceboEvents.fireItemStackedOnOther(carried, clicked, slot, clickAction, player, this.createCarriedSlotAccess())) {
            cir.setReturnValue(true);
        }
    }

}
