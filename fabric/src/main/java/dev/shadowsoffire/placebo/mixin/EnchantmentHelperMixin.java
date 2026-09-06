package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.shadowsoffire.placebo.events.EnchantmentLevelHooks;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Routes Fabric's four gameplay-enchantment reads through Placebo's fixed typed
 * dispatcher. Direct component reads used by crafting and tooltips remain untouched.
 */
@Mixin(value = EnchantmentHelper.class, remap = false)
public abstract class EnchantmentHelperMixin {

    /** Single-enchantment lookup; the dispatcher keeps this path primitive. */
    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void placebo$modifySingle(Holder<Enchantment> enchantment, ItemInstance piece,
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EnchantmentLevelHooks.modifySingle(piece, enchantment, cir.getReturnValueI()));
    }

    /** Whole-stack iteration uses getOrDefault in vanilla. */
    @ModifyExpressionValue(
        method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$modifyIteration(Object enchantments, @Local(argsOnly = true) ItemStack piece) {
        return EnchantmentLevelHooks.modifyAll(piece, (ItemEnchantments) enchantments);
    }

    /**
     * The equipment-slot overload uses nullable get rather than getOrDefault. Passing
     * null through when no handler can contribute preserves vanilla's absent-component
     * short circuit; a contributing handler receives EMPTY and may create the map.
     */
    @ModifyExpressionValue(
        method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$modifySlotIteration(Object enchantments, @Local(argsOnly = true) ItemStack piece) {
        return EnchantmentLevelHooks.modifyAll(piece, (ItemEnchantments) enchantments);
    }

    /** Tag presence checks use getOrDefault in vanilla. */
    @ModifyExpressionValue(
        method = "hasTag",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$modifyTagCheck(Object enchantments, @Local(argsOnly = true) ItemStack item) {
        return EnchantmentLevelHooks.modifyAll(item, (ItemEnchantments) enchantments);
    }
}
