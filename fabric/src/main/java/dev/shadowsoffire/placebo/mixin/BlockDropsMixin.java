package dev.shadowsoffire.placebo.mixin;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.shadowsoffire.placebo.events.FabricDropDispatcher;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.capture.BlockDropCaptureSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * Vanilla adds item entities directly from {@code popResource} and awards experience from
 * {@code popExperience}, so both calls are diverted while one of the three exact {@code dropResources}
 * overloads is running. Every invocation pushes a frame, including an inactive sentinel. That sentinel is
 * important when an active block drop calls an irrelevant nested drop: the nested vanilla work must not be
 * swallowed by the outer capture.
 */
@Mixin(value = Block.class, remap = false)
public abstract class BlockDropsMixin {

    /**
     * A state is created only when a dropResources wrapper is entered. The capture hooks themselves use a
     * nullable ThreadLocal and therefore do not allocate a state on ordinary block-item spawning.
     */
    @Unique
    private static final ThreadLocal<BlockDropCaptureSupport.State> placebo$captures = new ThreadLocal<>();

    @Unique
    private static @Nullable BlockDropCaptureSupport.Capture placebo$capture() {
        BlockDropCaptureSupport.State state = placebo$captures.get();
        if (state == null || state.active.isEmpty()) return null;
        return state.active.get(state.active.size() - 1);
    }

    @WrapOperation(
        method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
        remap = false)
    private static boolean placebo$captureBlockDrop(Level level, Entity dropped, Operation<Boolean> original) {
        BlockDropCaptureSupport.Capture capture = placebo$capture();
        if (capture != null && capture.plan.capturesItems()) {
            if (capture.drops == null) capture.drops = capture.takeReusableDrops();
            capture.drops.add((ItemEntity) dropped);
            return true;
        }
        // In XP_ONLY mode this is deliberately the original call: item identity and vanilla spawn timing are
        // unchanged, while only the experience award is diverted.
        return original.call(level, dropped);
    }

    @WrapOperation(
        method = "popExperience",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
        remap = false)
    private void placebo$captureBlockExperience(ServerLevel level, Vec3 pos, int amount, Operation<Void> original) {
        BlockDropCaptureSupport.Capture capture = placebo$capture();
        if (capture != null && capture.plan.capturesExperience()) {
            capture.experience += amount;
            return;
        }
        original.call(level, pos, amount);
    }

    // --- dropResources(BlockState, Level, BlockPos) ---

    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
    private static void placebo$wrapSimple(BlockState state, Level level, BlockPos pos, Operation<Void> original)
        throws Throwable {
        BlockDropCaptureSupport.Capture capture = placebo$begin(state, level, pos, null, ItemStack.EMPTY);
        if (capture == null) {
            original.call(state, level, pos);
            return;
        }
        try {
            original.call(state, level, pos);
        }
        catch (Throwable failure) {
            placebo$failure(level, pos, capture, failure);
            throw failure;
        }
        placebo$success(level, pos, state, null, ItemStack.EMPTY, capture);
    }

    // --- dropResources(BlockState, LevelAccessor, BlockPos, BlockEntity) ---

    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V")
    private static void placebo$wrapBlockEntity(BlockState state, LevelAccessor level, BlockPos pos,
        BlockEntity blockEntity, Operation<Void> original) throws Throwable {
        BlockDropCaptureSupport.Capture capture = placebo$begin(state, level, pos, null, ItemStack.EMPTY);
        if (capture == null) {
            original.call(state, level, pos, blockEntity);
            return;
        }
        try {
            original.call(state, level, pos, blockEntity);
        }
        catch (Throwable failure) {
            placebo$failure(level, pos, capture, failure);
            throw failure;
        }
        placebo$success(level, pos, state, null, ItemStack.EMPTY, capture);
    }

    // --- dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack) ---

    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V")
    private static void placebo$wrapTool(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity,
        Entity breaker, ItemStack tool, Operation<Void> original) throws Throwable {
        BlockDropCaptureSupport.Capture capture = placebo$begin(state, level, pos, breaker, tool);
        if (capture == null) {
            original.call(state, level, pos, blockEntity, breaker, tool);
            return;
        }
        try {
            original.call(state, level, pos, blockEntity, breaker, tool);
        }
        catch (Throwable failure) {
            placebo$failure(level, pos, capture, failure);
            throw failure;
        }
        placebo$success(level, pos, state, breaker, tool, capture);
    }

    @Unique
    private static @Nullable BlockDropCaptureSupport.Capture placebo$begin(BlockState state, LevelAccessor level, BlockPos pos,
        @Nullable Entity breaker, ItemStack tool) {
        return BlockDropCaptureSupport.begin(placebo$captures, state, level, pos, breaker, tool);
    }

    /** Original method failed: remove this frame, drain partial intercepted output once, then let the failure win. */
    @Unique
    private static void placebo$failure(LevelAccessor level, BlockPos pos, BlockDropCaptureSupport.Capture capture,
        Throwable originalFailure) throws Throwable {
        BlockDropCaptureSupport.State captureState = placebo$captures.get();
        BlockDropCaptureSupport.pop(captureState, capture);
        Throwable drainFailure = null;
        try {
            placebo$withInactiveDrain(captureState, level, pos, capture);
        }
        catch (Throwable failure) {
            drainFailure = failure;
        }
        finally {
            captureState.release(capture);
        }
        if (drainFailure != null) originalFailure.addSuppressed(drainFailure);
    }

    /** Original method completed: pop before dispatch, mask any outer capture while dispatching and draining. */
    @Unique
    private static void placebo$success(LevelAccessor level, BlockPos pos, BlockState state,
        @Nullable Entity breaker, ItemStack tool, BlockDropCaptureSupport.Capture capture) throws Throwable {
        BlockDropCaptureSupport.State captureState = placebo$captures.get();
        BlockDropCaptureSupport.pop(captureState, capture);
        if (capture.plan.mode() == FabricDropDispatcher.Mode.NONE) {
            captureState.release(capture);
            return;
        }

        Throwable dispatchFailure = null;
        try {
            BlockDropCaptureSupport.pushInactive(captureState);
            if (!(level instanceof ServerLevel server)) return;

            // The list is still lazy: an XP_ONLY handler gets an immutable empty view, while an item-aware
            // event receives the same mutable list that capture hooks use (allocated only at this boundary if
            // vanilla produced no item entities).
            List<ItemEntity> drops;
            if (capture.plan.capturesItems()) {
                if (capture.drops == null) capture.drops = capture.takeReusableDrops();
                drops = capture.drops;
            }
            else {
                drops = Collections.emptyList();
            }

            int experience = capture.experience;
            if (capture.plan.isFallback()) {
                experience = PlaceboEvents.fireBlockDrops(server, pos, state, breaker, tool, drops, experience);
            }
            else {
                experience = FabricDropDispatcher.dispatchBlock(capture.plan, server, pos, state, breaker, tool,
                    drops, experience);
            }
            capture.experience = experience;
            BlockDropCaptureSupport.drain(captureState, server, pos, capture);
        }
        catch (Throwable failure) {
            dispatchFailure = failure;
            try {
                BlockDropCaptureSupport.drain(captureState, level, pos, capture);
            }
            catch (Throwable drainFailure) {
                dispatchFailure.addSuppressed(drainFailure);
            }
        }
        finally {
            BlockDropCaptureSupport.popInactive(captureState);
            captureState.release(capture);
        }
        if (dispatchFailure != null) throw dispatchFailure;
    }

    @Unique
    private static void placebo$withInactiveDrain(BlockDropCaptureSupport.State captureState, LevelAccessor level,
        BlockPos pos, BlockDropCaptureSupport.Capture capture) throws Throwable {
        BlockDropCaptureSupport.pushInactive(captureState);
        try {
            BlockDropCaptureSupport.drain(captureState, level, pos, capture);
        }
        finally {
            BlockDropCaptureSupport.popInactive(captureState);
        }
    }
}
