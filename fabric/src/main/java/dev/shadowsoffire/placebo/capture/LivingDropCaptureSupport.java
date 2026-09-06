package dev.shadowsoffire.placebo.capture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.events.FabricDropDispatcher;
import dev.shadowsoffire.placebo.util.DropCapturer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import dev.shadowsoffire.placebo.mixin.LivingEntityDropsMixin;

/** Non-mixin storage and frame primitives used by {@link LivingEntityDropsMixin}. */
@ApiStatus.Internal
public final class LivingDropCaptureSupport {
    private LivingDropCaptureSupport() {}

    @ApiStatus.Internal
    public static final class State {
        public final List<Frame> active = new ArrayList<>();
        public final List<Frame> pool = new ArrayList<>();
        public final List<Mask> masks = new ArrayList<>();
        public final List<Mask> maskPool = new ArrayList<>();
        public final SinkImpl drainSink = new SinkImpl();

        public Frame acquire() { int last = pool.size() - 1; return last < 0 ? new Frame() : pool.remove(last); }
        public Mask acquireMask() { int last = maskPool.size() - 1; return last < 0 ? new Mask() : maskPool.remove(last); }
        public void releaseMask(Mask mask) { mask.capturer = null; mask.previous = null; maskPool.add(mask); }
        public void release(Frame frame) {
            if (frame.drops != null) {
                frame.drops.clear();
                frame.reusableDrops = frame.drops;
                frame.drops = null;
            }
            frame.entity = null;
            frame.capturer = null;
            frame.previous = null;
            frame.plan.reset();
            pool.add(frame);
        }
    }

    @ApiStatus.Internal
    public static final class Frame {
        public LivingEntity entity;
        public DropCapturer capturer;
        public Collection<ItemEntity> previous;
        public final FabricDropDispatcher.LivingPlanTarget plan = new FabricDropDispatcher.LivingPlanTarget();
        public ArrayList<ItemEntity> drops;
        public ArrayList<ItemEntity> reusableDrops;
        public ArrayList<ItemEntity> takeReusableDrops() {
            ArrayList<ItemEntity> result = reusableDrops;
            reusableDrops = null;
            return result == null ? new ArrayList<>() : result;
        }
    }

    @ApiStatus.Internal
    public static final class Mask {
        public DropCapturer capturer;
        public Collection<ItemEntity> previous;
    }

    public static void pop(State state, Frame frame) {
        int index = state.active.size() - 1;
        if (index >= 0 && state.active.get(index) == frame) state.active.remove(index);
        else state.active.remove(frame);
    }

    public static int maskActive(State state) {
        int mark = state.masks.size();
        for (Frame active : state.active) {
            DropCapturer capturer = active.capturer;
            boolean alreadyMasked = false;
            for (int i = mark; i < state.masks.size(); i++) {
                if (state.masks.get(i).capturer == capturer) { alreadyMasked = true; break; }
            }
            if (alreadyMasked) continue;
            Mask mask = state.acquireMask();
            mask.capturer = capturer;
            mask.previous = capturer.placebo$getCapturedDrops();
            capturer.placebo$setCapturedDrops(null);
            state.masks.add(mask);
        }
        return mark;
    }

    public static void restoreMasked(State state, int mark) {
        for (int i = state.masks.size() - 1; i >= mark; i--) {
            Mask mask = state.masks.remove(i);
            mask.capturer.placebo$setCapturedDrops(mask.previous);
            state.releaseMask(mask);
        }
    }

    public static void drainOnce(@Nullable List<ItemEntity> drops, Sink sink) throws Throwable {
        if (drops == null) return;
        Throwable failure = null;
        try {
            for (ItemEntity drop : drops) {
                try { sink.accept(drop); }
                catch (Throwable dropFailure) {
                    if (failure == null) failure = dropFailure;
                    else failure.addSuppressed(dropFailure);
                }
            }
        }
        finally { drops.clear(); }
        if (failure != null) throw failure;
    }

    public static void cleanupOriginalFailure(State state, Frame frame, DropCapturer self, Throwable originalFailure,
        Sink sink) {
        pop(state, frame);
        self.placebo$setCapturedDrops(null);
        Throwable cleanupFailure = null;
        int mark = state.masks.size();
        try {
            mark = maskActive(state);
            drainOnce(frame.drops, sink);
        }
        catch (Throwable failure) { cleanupFailure = failure; }
        finally {
            try { restoreMasked(state, mark); }
            catch (Throwable failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            self.placebo$setCapturedDrops(frame.previous);
            state.release(frame);
        }
        if (cleanupFailure != null) originalFailure.addSuppressed(cleanupFailure);
    }

    @FunctionalInterface
    @ApiStatus.Internal
    public interface Sink { void accept(ItemEntity drop) throws Throwable; }

    @ApiStatus.Internal
    public static final class SinkImpl implements Sink {
        public ServerLevel level;
        @Override public void accept(ItemEntity drop) { level.addFreshEntity(drop); }
    }
}
