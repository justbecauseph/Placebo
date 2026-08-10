package dev.shadowsoffire.placebo.mixin;

import java.util.ArrayList;
import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.util.DropCapturer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

/**
 * Fires {@link PlaceboEvents#LIVING_DROPS} on Fabric. NeoForge gets the same event from its own
 * {@code LivingDropsEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * The shape is NeoForge's, read out of its {@code LivingEntity} patch: open a capture list at the head of
 * {@code dropAllDeathLoot}, let vanilla fill it, fire the event, then spawn what survived. Vanilla builds no
 * such list — it adds each {@link ItemEntity} to the world as it is produced — so the capture is what makes
 * an event over "the drops" possible at all.
 * <p>
 * {@code drop} covers the equipment half; the loot-table half goes through {@code Entity#spawnAtLocation} and
 * is caught by {@code EntityDropCaptureMixin}. The capture flag lives on {@code Entity} for exactly that
 * reason, and because that is where NeoForge puts it.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityDropsMixin {

    @WrapOperation(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
        remap = false)
    private boolean placebo$captureEquipmentDrop(Level level, Entity dropped, Operation<Boolean> original) {
        Collection<ItemEntity> captured = ((DropCapturer) this).placebo$getCapturedDrops();
        if (captured != null) {
            captured.add((ItemEntity) dropped);
            return true;
        }
        return original.call(level, dropped);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), remap = false)
    private void placebo$beginDropCapture(ServerLevel level, DamageSource source, CallbackInfo ci) {
        ((DropCapturer) this).placebo$setCapturedDrops(new ArrayList<>());
    }

    /**
     * Clearing the capture flag before firing matters: a listener that spawns an entity of its own during the
     * event must not have it swallowed by the capture that is currently being drained.
     */
    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"), remap = false)
    private void placebo$fireLivingDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        DropCapturer self = (DropCapturer) this;
        Collection<ItemEntity> drops = self.placebo$getCapturedDrops();
        self.placebo$setCapturedDrops(null);
        if (drops == null) {
            return;
        }
        PlaceboEvents.fireLivingDrops((LivingEntity) (Object) this, source, drops, true);
        drops.forEach(level::addFreshEntity);
    }

}
