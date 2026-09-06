package dev.shadowsoffire.placebo.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.shadowsoffire.placebo.util.DropCapturer;
import dev.shadowsoffire.placebo.capture.BlockDropCaptureSupport;
import dev.shadowsoffire.placebo.capture.LivingDropCaptureSupport;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

final class DropCaptureFrameTest {

    @Test
    void blockDrainAttemptsItemsAndExperienceOnceAndClearsAfterFailure() throws Throwable {
        BlockDropCaptureSupport.Capture capture = new BlockDropCaptureSupport.Capture();
        capture.drops = new ArrayList<>();
        capture.drops.add(null);
        capture.drops.add(null);
        capture.experience = 7;
        AtomicInteger items = new AtomicInteger();
        AtomicInteger experience = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> BlockDropCaptureSupport.drainOnce(capture,
            drop -> { items.incrementAndGet(); throw new IllegalArgumentException("item"); },
            amount -> { experience.addAndGet(amount); throw new IllegalArgumentException("xp"); }));
        assertEquals(2, items.get());
        assertEquals(7, experience.get());
        assertEquals(0, capture.drops.size());
        assertEquals(0, capture.experience);
        BlockDropCaptureSupport.drainOnce(capture, drop -> items.incrementAndGet(), amount -> experience.addAndGet(amount));
        assertEquals(2, items.get());
        assertEquals(7, experience.get());
    }

    @Test
    void blockFramesKeepActiveOuterCaptureBelowInactiveAndActiveInnerFrames() {
        ThreadLocal<BlockDropCaptureSupport.State> captures = new ThreadLocal<>();
        assertNull(BlockDropCaptureSupport.begin(captures, null, null, null, null, ItemStack.EMPTY),
            "an unobserved top-level block drop must not create a capture frame");
        assertNull(captures.get(), "NONE bypass must leave the capture state unallocated");

        BlockDropCaptureSupport.State state = new BlockDropCaptureSupport.State();
        BlockDropCaptureSupport.Capture outer = state.acquire();
        state.active.add(outer);

        BlockDropCaptureSupport.pushInactive(state);
        assertEquals(2, state.active.size());
        assertSame(outer, state.active.get(0));
        assertEquals(0, state.active.get(1).plan.relevantMask());
        BlockDropCaptureSupport.popInactive(state);
        assertEquals(List.of(outer), state.active);

        BlockDropCaptureSupport.Capture inner = state.acquire();
        state.active.add(inner);
        BlockDropCaptureSupport.pop(state, inner);
        assertEquals(List.of(outer), state.active,
            "an active nested block frame must pop without removing its outer frame");
        BlockDropCaptureSupport.pop(state, outer);
        state.release(outer);
        state.release(inner);
    }

    @Test
    void livingFramesMaskSameAndDifferentEntityCapturesAndRestoreThemByMark() {
        DropCaptureFrameTest.FakeCapturer sameEntity = new FakeCapturer();
        ArrayList<ItemEntity> sameDrops = new ArrayList<>();
        sameEntity.setCapturedDrops(sameDrops);

        DropCaptureFrameTest.FakeCapturer otherEntity = new FakeCapturer();
        ArrayList<ItemEntity> otherDrops = new ArrayList<>();
        otherEntity.setCapturedDrops(otherDrops);

        LivingDropCaptureSupport.State state = new LivingDropCaptureSupport.State();
        LivingDropCaptureSupport.Frame sameOuter = state.acquire();
        sameOuter.capturer = sameEntity;
        state.active.add(sameOuter);
        LivingDropCaptureSupport.Frame sameInner = state.acquire();
        sameInner.capturer = sameEntity;
        state.active.add(sameInner);
        LivingDropCaptureSupport.Frame other = state.acquire();
        other.capturer = otherEntity;
        state.active.add(other);

        int mark = LivingDropCaptureSupport.maskActive(state);
        assertEquals(0, mark);
        assertNull(sameEntity.getCapturedDrops());
        assertNull(otherEntity.getCapturedDrops());
        assertEquals(2, state.masks.size(), "same entity should have one mask and the other entity one mask");

        LivingDropCaptureSupport.restoreMasked(state, mark);
        assertSame(sameDrops, sameEntity.getCapturedDrops());
        assertSame(otherDrops, otherEntity.getCapturedDrops());
        assertEquals(0, state.masks.size());
        assertEquals(2, state.maskPool.size(), "mask frames must be reusable after restoration");

        state.active.clear();
        state.release(sameOuter);
        state.release(sameInner);
        state.release(other);
    }

    @Test
    void originalFailureCleanupPopsRestoresAndNeverDispatchesAnEvent() {
        FakeCapturer capturer = new FakeCapturer();
        ArrayList<ItemEntity> previous = new ArrayList<>();
        capturer.setCapturedDrops(previous);

        LivingDropCaptureSupport.State state = new LivingDropCaptureSupport.State();
        LivingDropCaptureSupport.Frame frame = state.acquire();
        frame.capturer = capturer;
        frame.previous = previous;
        frame.drops = new ArrayList<>();
        frame.drops.add(null);
        state.active.add(frame);

        AtomicInteger drained = new AtomicInteger();
        IllegalStateException originalFailure = new IllegalStateException("original");
        LivingDropCaptureSupport.cleanupOriginalFailure(state, frame, capturer, originalFailure,
            drop -> drained.incrementAndGet());

        assertEquals(1, drained.get());
        assertSame(previous, capturer.getCapturedDrops());
        assertTrueStateClean(state);
        assertEquals(0, originalFailure.getSuppressed().length, "cleanup should not add a failure");
        assertSame(frame, state.acquire(), "the failed frame must return to the reusable pool");
    }

    @Test
    void listenerFailureDrainClearsListSoASecondCleanupCannotDuplicateDrops() throws Throwable {
        ArrayList<ItemEntity> drops = new ArrayList<>();
        drops.add(null);
        drops.add(null);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
            () -> LivingDropCaptureSupport.drainOnce(drops, drop -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("listener");
            }));
        assertEquals(2, calls.get());
        assertEquals(0, drops.size());

        assertEquals(2, calls.get(), "a second drain after listener cleanup must not invoke the listener again");
        LivingDropCaptureSupport.drainOnce(drops, drop -> calls.incrementAndGet());
        assertEquals(2, calls.get());
    }

    @Test
    void livingFrameAndListAreReusedAfterExceptionCleanup() {
        LivingDropCaptureSupport.State state = new LivingDropCaptureSupport.State();
        LivingDropCaptureSupport.Frame frame = state.acquire();
        ArrayList<ItemEntity> drops = new ArrayList<>();
        frame.drops = drops;
        state.active.add(frame);
        LivingDropCaptureSupport.pop(state, frame);
        state.release(frame);

        LivingDropCaptureSupport.Frame reused = state.acquire();
        assertSame(frame, reused);
        assertSame(drops, reused.takeReusableDrops());
        state.release(reused);
    }

    private static void assertTrueStateClean(LivingDropCaptureSupport.State state) {
        assertEquals(0, state.active.size());
        assertEquals(0, state.masks.size());
        assertEquals(1, state.pool.size());
    }

    private static final class FakeCapturer implements DropCapturer {
        private Collection<ItemEntity> drops;

        @Override
        public Collection<ItemEntity> placebo$getCapturedDrops() {
            return this.drops;
        }

        @Override
        public void placebo$setCapturedDrops(Collection<ItemEntity> drops) {
            this.drops = drops;
        }

        Collection<ItemEntity> getCapturedDrops() {
            return this.drops;
        }

        void setCapturedDrops(Collection<ItemEntity> drops) {
            this.drops = drops;
        }
    }
}
