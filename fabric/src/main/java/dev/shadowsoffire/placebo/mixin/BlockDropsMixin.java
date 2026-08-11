package dev.shadowsoffire.placebo.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fires {@link PlaceboEvents#BLOCK_DROPS} on Fabric. NeoForge gets the same event from its own
 * {@code BlockDropsEvent} via {@code NeoForgeEventBridge}.
 * <p>
 * This is {@link LivingEntityDropsMixin}'s problem again with a second half. Vanilla adds each dropped
 * {@link ItemEntity} to the world inside {@code popResource} as it is produced, so the drops have to be
 * captured to exist as a list at all — NeoForge patches a static capture onto {@code Block} and this does the
 * same.
 * <p>
 * <b>The experience needs capturing too, and for a different reason.</b> NeoForge asks
 * {@code BlockState#getExpDrop} for the amount up front, but that method is one of NeoForge's own additions to
 * vanilla. On Fabric there is nothing to ask: each block decides its own amount inside {@code spawnAfterBreak}
 * and hands it to {@code popExperience}. Intercepting the award is the only way to see the number — and it is
 * arguably the better one, since it is whatever the block actually meant to drop rather than a re-derivation.
 * <p>
 * Intercepting at {@code ExperienceOrb.award} rather than at the head of {@code popExperience} is deliberate:
 * the {@code BLOCK_DROPS} game rule is checked in between, so hooking earlier would capture experience that
 * vanilla had already decided not to award.
 * <p>
 * The capture is a stack rather than a single slot, so a block that breaks another block from inside its own
 * {@code spawnAfterBreak} does not swallow the outer one's drops. NeoForge's assigns unconditionally; this
 * behaves identically in the ordinary case and correctly in the nested one.
 * <p>
 * <b>One ordering difference from NeoForge, recorded rather than hidden:</b> NeoForge fires the event, spawns
 * the drops, and only then calls {@code spawnAfterBreak}, having suppressed vanilla's experience drop. Here
 * vanilla's {@code spawnAfterBreak} has already run by the time the event fires, so any of its other side
 * effects — particles, sculk vibrations — happen before the drops reach the world instead of after. Nothing in
 * this stack observes that order.
 */
@Mixin(value = Block.class, remap = false)
public abstract class BlockDropsMixin {

    /**
     * One frame per {@code dropResources} call in progress. A stack rather than a single slot because a block
     * can break another block from inside its own {@code spawnAfterBreak}; NeoForge's capture assigns
     * unconditionally and would let the inner call swallow the outer one's drops.
     */
    @Unique
    private static final Deque<PlaceboBlockDropCapture> placebo$captures = new ArrayDeque<>();

    @Unique
    private static @Nullable PlaceboBlockDropCapture placebo$capture() {
        return placebo$captures.peek();
    }

    @WrapOperation(
        method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
        remap = false)
    private static boolean placebo$captureBlockDrop(Level level, Entity dropped, Operation<Boolean> original) {
        PlaceboBlockDropCapture capture = placebo$capture();
        if (capture != null) {
            capture.drops.add((ItemEntity) dropped);
            return true;
        }
        return original.call(level, dropped);
    }

    @WrapOperation(
        method = "popExperience",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
        remap = false)
    private void placebo$captureBlockExperience(ServerLevel level, Vec3 pos, int amount, Operation<Void> original) {
        PlaceboBlockDropCapture capture = placebo$capture();
        if (capture != null) {
            capture.experience += amount;
            return;
        }
        original.call(level, pos, amount);
    }

    // --- dropResources(BlockState, Level, BlockPos) ---

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), remap = false)
    private static void placebo$beginSimpleCapture(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        placebo$begin(level);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"), remap = false)
    private static void placebo$fireSimpleDrops(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        placebo$fire(level, pos, state, null, ItemStack.EMPTY);
    }

    // --- dropResources(BlockState, LevelAccessor, BlockPos, BlockEntity) ---

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"), remap = false)
    private static void placebo$beginBlockEntityCapture(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
        placebo$begin(level);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("RETURN"), remap = false)
    private static void placebo$fireBlockEntityDrops(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
        placebo$fire(level, pos, state, null, ItemStack.EMPTY);
    }

    // --- dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack) ---

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), remap = false)
    private static void placebo$beginToolCapture(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool, CallbackInfo ci) {
        placebo$begin(level);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"), remap = false)
    private static void placebo$fireToolDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool, CallbackInfo ci) {
        placebo$fire(level, pos, state, breaker, tool);
    }

    @Unique
    private static void placebo$begin(LevelAccessor level) {
        if (level instanceof ServerLevel) {
            placebo$captures.push(new PlaceboBlockDropCapture());
        }
    }

    /**
     * Fires the event with whatever was captured, then puts it into the world. The capture is cleared first so
     * that a listener spawning an entity of its own is not swallowed by the drain in progress.
     */
    @Unique
    private static void placebo$fire(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Entity breaker, ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel) || placebo$captures.isEmpty()) {
            return;
        }
        PlaceboBlockDropCapture capture = placebo$captures.pop();
        List<ItemEntity> drops = capture.drops;

        int experience = PlaceboEvents.fireBlockDrops(serverLevel, pos, state, breaker, tool, drops, capture.experience);

        for (ItemEntity drop : drops) {
            serverLevel.addFreshEntity(drop);
        }
        if (experience > 0) {
            // The game rule was already checked where the amount was captured; re-checking would double-gate it.
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), experience);
        }
    }

    /**
     * A single in-progress capture. Declared here rather than as a separate file because it is an
     * implementation detail of this mixin, and mixin inner classes are shadowed into the target alongside it.
     */
    @Unique
    private static final class PlaceboBlockDropCapture {

        private final List<ItemEntity> drops = new ArrayList<>();
        private int experience;
    }

}
