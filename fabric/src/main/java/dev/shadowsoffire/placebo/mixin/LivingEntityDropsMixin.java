package dev.shadowsoffire.placebo.mixin;

import java.util.ArrayList;
import java.util.Collection;
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
import dev.shadowsoffire.placebo.capture.LivingDropCaptureSupport;
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
 * Vanilla has no drop collection: loot-table drops pass through {@code Entity#spawnAtLocation}, while
 * equipment drops pass through {@code LivingEntity#drop}. Both call sites use the capture collection supplied
 * by {@code EntityDropCaptureMixin}. A depth stack is needed for nested deaths, and a separate frame pool keeps
 * the normal owned path from allocating a frame or list on every death.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityDropsMixin {

    /** Nullable so ordinary deaths with no relevant listener do not create a ThreadLocal state. */
    @Unique
    private static final ThreadLocal<LivingDropCaptureSupport.State> placebo$frames = new ThreadLocal<>();

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

    @WrapMethod(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V")
    private void placebo$wrapDropAllDeathLoot(ServerLevel level, DamageSource source, Operation<Void> original)
        throws Throwable {
        LivingEntity entity = (LivingEntity) (Object) this;
        DropCapturer self = (DropCapturer) this;
        Collection<ItemEntity> previous = self.placebo$getCapturedDrops();

        // With no relevant owned handler or fallback listener, preserve the vanilla call completely. If an
        // outer same-entity frame is active, however, a null sentinel is still required to mask that outer
        // collection while this inner call performs its vanilla work.
        if (previous == null && FabricDropDispatcher.livingPlanIsNone(entity, source)) {
            original.call(level, source);
            return;
        }

        LivingDropCaptureSupport.State frameState = placebo$state();
        LivingDropCaptureSupport.Frame frame = frameState.acquire();
        frame.entity = entity;
        frame.capturer = self;
        frame.previous = previous;
        FabricDropDispatcher.planLivingInto(frame.plan, entity, source);
        frame.drops = frame.plan.mode() == FabricDropDispatcher.Mode.NONE ? null : frame.takeReusableDrops();
        frameState.active.add(frame);
        // A NONE frame is an intentionally inactive sentinel. It masks a same-entity outer capture without
        // allocating a drop list or allowing the inner vanilla entities to leak into the outer event.
        self.placebo$setCapturedDrops(frame.drops);

        try {
            original.call(level, source);
        }
        catch (Throwable originalFailure) {
            frameState.drainSink.level = level;
            try {
                placebo$cleanupOriginalFailure(frameState, frame, self, originalFailure, frameState.drainSink);
            }
            finally {
                frameState.drainSink.level = null;
            }
            throw originalFailure;
        }

        placebo$pop(frameState, frame);
        if (frame.drops == null) {
            self.placebo$setCapturedDrops(frame.previous);
            frameState.release(frame);
            return;
        }

        // The frame is no longer active before the event. Clear the entity field while listeners run and while
        // their surviving drops drain; listener-spawned items must not be added to this event or an outer frame.
        self.placebo$setCapturedDrops(null);
        Throwable dispatchFailure = null;
        int maskMark = placebo$maskActiveCaptures(frameState);
        try {
            if (frame.plan.isFallback()) {
                PlaceboEvents.fireLivingDrops(entity, source, frame.drops, true);
            }
            else {
                FabricDropDispatcher.dispatchLiving(frame.plan, entity, source, frame.drops);
            }
            placebo$drain(level, frame.drops, frameState);
        }
        catch (Throwable failure) {
            dispatchFailure = failure;
            try {
                placebo$drain(level, frame.drops, frameState);
            }
            catch (Throwable drainFailure) {
                dispatchFailure.addSuppressed(drainFailure);
            }
        }
        finally {
            // Keep the previous outer capture unavailable until event dispatch and draining have both ended.
            placebo$restoreMaskedCaptures(frameState, maskMark);
            self.placebo$setCapturedDrops(frame.previous);
            frameState.release(frame);
        }
        if (dispatchFailure != null) throw dispatchFailure;
    }

    @Unique
    private static LivingDropCaptureSupport.State placebo$state() {
        LivingDropCaptureSupport.State state = placebo$frames.get();
        if (state == null) {
            state = new LivingDropCaptureSupport.State();
            placebo$frames.set(state);
        }
        return state;
    }

    @Unique
    private static void placebo$pop(LivingDropCaptureSupport.State state, LivingDropCaptureSupport.Frame frame) {
        LivingDropCaptureSupport.pop(state, frame);
    }

    /**
     * Masks every active outer entity while an event is dispatched. Same-entity masking is covered by the
     * wrapper's direct null assignment; the loop also prevents a listener for one entity from adding a drop to
     * a different entity whose death is still collecting on this thread.
     */
    @Unique
    private static int placebo$maskActiveCaptures(LivingDropCaptureSupport.State state) {
        return LivingDropCaptureSupport.maskActive(state);
    }

    @Unique
    private static void placebo$restoreMaskedCaptures(LivingDropCaptureSupport.State state, int mark) {
        LivingDropCaptureSupport.restoreMasked(state, mark);
    }

    /** Drains captured entity identities exactly once; the list is cleared even if a world insertion fails. */
    @Unique
    private static void placebo$drain(ServerLevel level, @Nullable List<ItemEntity> drops,
        LivingDropCaptureSupport.State state) throws Throwable {
        if (drops == null) return;
        state.drainSink.level = level;
        try {
            LivingDropCaptureSupport.drainOnce(drops, state.drainSink);
        }
        finally {
            state.drainSink.level = null;
        }
    }

    /** Production drain primitive and package-private test seam for exactly-once listener cleanup. */
    private static void placebo$drainOnce(@Nullable List<ItemEntity> drops, LivingDropCaptureSupport.Sink sink) throws Throwable {
        LivingDropCaptureSupport.drainOnce(drops, sink);
    }

    /**
     * Production original-failure cleanup and package-private test seam. The event is deliberately absent from
     * this path: intercepted partial output is drained, then the original failure is allowed to propagate.
     */
    private static void placebo$cleanupOriginalFailure(LivingDropCaptureSupport.State state,
        LivingDropCaptureSupport.Frame frame, DropCapturer self, Throwable originalFailure,
        LivingDropCaptureSupport.Sink sink) {
        LivingDropCaptureSupport.cleanupOriginalFailure(state, frame, self, originalFailure, sink);
    }

}
