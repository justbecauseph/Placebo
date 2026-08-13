package dev.shadowsoffire.placebo.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * Fires {@link PlaceboEvents#ENCHANTMENT_LEVEL_SET} on Fabric. NeoForge gets the same event from its patched
 * {@code EnchantmentMenu} through {@code EnchantmentLevelSetEvent} and {@code NeoForgeEventBridge}.
 * <p>
 * The 26.2 jar has no local variable table, so this hooks the first cost read in the second loop rather than a
 * local assignment. That is immediately after all three vanilla costs have been clamped and immediately before
 * they are used to produce the clues, which is the same boundary NeoForge patches.
 */
@Mixin(value = EnchantmentMenu.class, remap = false)
public abstract class EnchantmentMenuMixin {

    @Shadow public int[] costs;

    /**
     * The third {@code costs} field read is the {@code costs[l] > 0} test in the method's second loop. The first
     * two are its assignments in the calculation loop. The lambda owns the three otherwise-unavailable context
     * arguments, so targeting this instruction avoids fragile locals without recomputing an access lookup.
     */
    @Inject(
        method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I", opcode = Opcodes.GETFIELD, ordinal = 2, shift = At.Shift.BEFORE),
        remap = false)
    private void placebo$fireEnchantmentLevelSet(ItemStack item, Level level, BlockPos pos, CallbackInfo ci) {
        int power = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) {
                power++;
            }
        }

        for (int row = 0; row < this.costs.length; row++) {
            this.costs[row] = PlaceboEvents.fireEnchantmentLevelSet(level, pos, row, power, item, this.costs[row]);
        }
    }

}
