package dev.shadowsoffire.placebo.mixin;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.util.DropCapturer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Supplies {@link DropCapturer} and diverts the loot-table half of an entity's drops into it.
 * <p>
 * This mirrors NeoForge's {@code captureDrops} field on {@code Entity} and its patch to
 * {@code spawnAtLocation(ServerLevel, ItemStack, Vec3)} — the three-argument overload every other one funnels
 * into, and the one that actually constructs the {@link ItemEntity}. The equipment half goes through
 * {@code LivingEntity#drop} instead and is handled by {@code LivingEntityDropsMixin}; NeoForge patches both,
 * and hooking only one would silently drop half the loot out of the event.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class EntityDropCaptureMixin implements DropCapturer {

    @Unique
    private @Nullable Collection<ItemEntity> placebo$capturedDrops;

    @Override
    public @Nullable Collection<ItemEntity> placebo$getCapturedDrops() {
        return this.placebo$capturedDrops;
    }

    @Override
    public void placebo$setCapturedDrops(@Nullable Collection<ItemEntity> drops) {
        this.placebo$capturedDrops = drops;
    }

    @WrapOperation(
        method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
        remap = false)
    private boolean placebo$captureLootDrop(ServerLevel level, Entity dropped, Operation<Boolean> original) {
        if (this.placebo$capturedDrops != null) {
            this.placebo$capturedDrops.add((ItemEntity) dropped);
            // NeoForge's patch skips the spawn entirely here; the caller only uses the returned entity, so the
            // value reported for the suppressed spawn is never read.
            return true;
        }
        return original.call(level, dropped);
    }

}
