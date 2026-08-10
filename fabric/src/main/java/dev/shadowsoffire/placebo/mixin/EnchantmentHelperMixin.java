package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Fires {@link PlaceboEvents#ENCHANTMENT_LEVELS} on Fabric. NeoForge gets the same event from its own
 * {@code GetEnchantmentLevelEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * NeoForge patches this class in four places so that <i>gameplay</i> enchantment queries go through its event
 * while <i>NBT</i> reads do not, and all four are reproduced here — read out of
 * {@code EnchantmentHelper.java.patch} and checked against the 26.2 bytecode:
 * <ul>
 * <li>{@code getItemEnchantmentLevel} — the single-enchantment query.
 * <li>both {@code runIterationOnItem} overloads — what drives enchantment <i>effects</i> in combat. Missing
 * these would leave an affix-granted Sharpness visible to a level query and inert in a fight.
 * <li>{@code hasTag} — the tag check enchantment effects gate on.
 * </ul>
 * Everything else keeps reading the {@code ENCHANTMENTS} component directly on both loaders, which is why the
 * anvil and the item tooltip do not see granted enchantments.
 * <p>
 * <b>The 26.2 jar carries no local variable table</b>, so {@code @ModifyVariable} cannot identify the
 * {@code ItemEnchantments} local that NeoForge overwrites. These hooks therefore modify the
 * <i>component read</i> that feeds it, which is an instruction match and needs no locals at all;
 * {@code @Local(argsOnly = true)} then recovers the stack, by type, from the target's own parameters.
 * <p>
 * The reads return {@code Object} because {@code get}/{@code getOrDefault} are generic and erased — the
 * {@code checkcast} vanilla emits comes after this hook, so returning the modified map is still type-safe.
 */
@Mixin(value = EnchantmentHelper.class, remap = false)
public abstract class EnchantmentHelperMixin {

    /**
     * NeoForge rewrites the body of this method to call its own gameplay lookup instead of reading the
     * component; the observable difference is only in the returned level, so hooking the return is equivalent.
     */
    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void placebo$fireSingleEnchantmentLevel(Holder<Enchantment> enchantment, ItemInstance piece, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PlaceboEvents.fireSingleEnchantmentLevel(piece, enchantment, cir.getReturnValueI()));
    }

    @ModifyExpressionValue(
        method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$fireEnchantmentLevelsForIteration(Object enchantments, @Local(argsOnly = true) ItemStack piece) {
        return PlaceboEvents.fireAllEnchantmentLevels(piece, (ItemEnchantments) enchantments);
    }

    /**
     * This overload reads the component with {@code get} rather than {@code getOrDefault}, so the original value
     * is nullable. NeoForge's replacement is unconditional and never null, which is load-bearing: an item with no
     * enchantment component at all still runs the iteration if a listener grants one. Vanilla's following
     * {@code != null && !isEmpty()} check then behaves identically for the untouched case.
     */
    @ModifyExpressionValue(
        method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$fireEnchantmentLevelsForSlotIteration(Object enchantments, @Local(argsOnly = true) ItemStack piece) {
        ItemEnchantments present = enchantments == null ? ItemEnchantments.EMPTY : (ItemEnchantments) enchantments;
        return PlaceboEvents.fireAllEnchantmentLevels(piece, present);
    }

    @ModifyExpressionValue(
        method = "hasTag",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"),
        remap = false)
    private static Object placebo$fireEnchantmentLevelsForTagCheck(Object enchantments, @Local(argsOnly = true) ItemStack item) {
        return PlaceboEvents.fireAllEnchantmentLevels(item, (ItemEnchantments) enchantments);
    }

}
