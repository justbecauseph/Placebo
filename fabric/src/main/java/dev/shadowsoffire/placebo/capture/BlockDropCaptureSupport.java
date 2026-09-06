package dev.shadowsoffire.placebo.capture;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.events.FabricDropDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.shadowsoffire.placebo.mixin.BlockDropsMixin;

/** Non-mixin storage and frame primitives used by {@link BlockDropsMixin}. */
@ApiStatus.Internal
public final class BlockDropCaptureSupport {
    private BlockDropCaptureSupport() {}

    @ApiStatus.Internal
    public static final class State {
        public final List<Capture> active = new ArrayList<>();
        public final List<Capture> pool = new ArrayList<>();
        public final DrainSink drainSink = new DrainSink();

        public Capture acquire() {
            int last = pool.size() - 1;
            return last < 0 ? new Capture() : pool.remove(last);
        }

        public void release(Capture capture) {
            capture.plan.reset();
            capture.experience = 0;
            if (capture.drops != null) {
                capture.drops.clear();
                capture.reusableDrops = capture.drops;
                capture.drops = null;
            }
            pool.add(capture);
        }
    }

    @ApiStatus.Internal
    public static final class Capture {
        public final FabricDropDispatcher.BlockPlanTarget plan = new FabricDropDispatcher.BlockPlanTarget();
        public ArrayList<ItemEntity> drops;
        public ArrayList<ItemEntity> reusableDrops;
        public int experience;

        public ArrayList<ItemEntity> takeReusableDrops() {
            ArrayList<ItemEntity> result = reusableDrops;
            reusableDrops = null;
            return result == null ? new ArrayList<>() : result;
        }
    }

    public static Capture begin(ThreadLocal<State> captures, BlockState state, LevelAccessor level, BlockPos pos,
        Entity breaker, ItemStack tool) {
        State captureState = captures.get();
        boolean nested = captureState != null && !captureState.active.isEmpty();
        if (!nested && FabricDropDispatcher.blockPlanIsNone(level, pos, state, breaker, tool)) return null;
        if (captureState == null) {
            captureState = new State();
            captures.set(captureState);
        }
        Capture capture = captureState.acquire();
        if (level instanceof ServerLevel server) FabricDropDispatcher.planBlockInto(capture.plan, server, pos, state,
            breaker, tool);
        else capture.plan.reset();
        capture.experience = 0;
        capture.drops = null;
        captureState.active.add(capture);
        return capture;
    }

    public static void pushInactive(State state) {
        Capture inactive = state.acquire();
        inactive.plan.reset();
        inactive.drops = null;
        inactive.experience = 0;
        state.active.add(inactive);
    }

    public static void popInactive(State state) {
        int index = state.active.size() - 1;
        if (index < 0) return;
        state.release(state.active.remove(index));
    }

    public static void pop(State state, Capture capture) {
        int index = state.active.size() - 1;
        if (index >= 0 && state.active.get(index) == capture) state.active.remove(index);
        else state.active.remove(capture);
    }

    public static void drain(State state, LevelAccessor level, BlockPos pos, Capture capture) throws Throwable {
        if (!(level instanceof ServerLevel server)) return;
        state.drainSink.level = server;
        state.drainSink.pos = pos;
        try { drainOnce(capture, state.drainSink, state.drainSink); }
        finally {
            state.drainSink.level = null;
            state.drainSink.pos = null;
        }
    }

    @FunctionalInterface
    public interface ItemSink { void accept(ItemEntity drop) throws Throwable; }

    @FunctionalInterface
    public interface ExperienceSink { void accept(int amount) throws Throwable; }

    public static void drainOnce(Capture capture, ItemSink items, ExperienceSink experienceSink) throws Throwable {
        Throwable failure = null;
        if (capture.drops != null) {
            try {
                for (ItemEntity drop : capture.drops) {
                    try { items.accept(drop); }
                    catch (Throwable dropFailure) {
                        if (failure == null) failure = dropFailure;
                        else failure.addSuppressed(dropFailure);
                    }
                }
            }
            finally { capture.drops.clear(); }
        }
        int experience = capture.experience;
        capture.experience = 0;
        if (experience > 0) {
            try { experienceSink.accept(experience); }
            catch (Throwable experienceFailure) {
                if (failure == null) failure = experienceFailure;
                else failure.addSuppressed(experienceFailure);
            }
        }
        if (failure != null) throw failure;
    }

    @ApiStatus.Internal
    public static final class DrainSink implements ItemSink, ExperienceSink {
        public ServerLevel level;
        public BlockPos pos;
        @Override public void accept(ItemEntity drop) { level.addFreshEntity(drop); }
        @Override public void accept(int amount) { ExperienceOrb.award(level, Vec3.atCenterOf(pos), amount); }
    }
}
