package dev.shadowsoffire.placebo.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fires {@link PlaceboEvents#LIVING_EQUIPMENT_CHANGE} on Fabric. NeoForge gets the same event from its own
 * {@code LivingEquipmentChangeEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * NeoForge posts from inside the slot loop, which would mean reading the loop's {@code EquipmentSlot} local —
 * and the 26.2 jar carries no local variable table. The method already returns exactly what the event needs, a
 * map of changed slot to new stack, so hooking the return is both equivalent and free of locals.
 * <p>
 * The only observable difference is that all slots are collected before any listener runs, rather than a
 * listener seeing the entity halfway through its update. Nothing in this stack looks.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityEquipmentMixin {

    @Inject(method = "collectEquipmentChanges", at = @At("RETURN"), remap = false)
    private void placebo$fireEquipmentChange(Map<EquipmentSlot, ItemStack> lastEquipment,
        CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        Map<EquipmentSlot, ItemStack> changed = cir.getReturnValue();
        if (changed == null) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : changed.entrySet()) {
            PlaceboEvents.fireEquipmentChange(self, entry.getKey(), entry.getValue());
        }
    }

}
