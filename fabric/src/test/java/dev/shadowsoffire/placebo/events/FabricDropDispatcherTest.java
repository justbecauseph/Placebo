package dev.shadowsoffire.placebo.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.architectury.event.EventPriority;
import dev.shadowsoffire.placebo.events.PlaceboEvents.BlockDrops;
import dev.shadowsoffire.placebo.events.PlaceboEvents.DespawnResult;
import dev.shadowsoffire.placebo.events.PlaceboEvents.LivingDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;

final class FabricDropDispatcherTest {

    @Test
    void livingGenerationsUsePrioritySequenceMasksAndFallbackMode() {
        AtomicBoolean relevant = new AtomicBoolean(true);
        List<String> order = new ArrayList<>();
        FabricDropDispatcher.LivingHandler lowHandler = (entity, source, drops) -> order.add("low");
        FabricDropDispatcher.registerLiving(EventPriority.LOW, (entity, source) -> relevant.get(), lowHandler);

        FabricDropDispatcher.LivingPlan first = FabricDropDispatcher.planLiving(null);
        int baselineSize = first.entries().size();
        FabricDropDispatcher.LivingHandler highHandler = (entity, source, drops) -> order.add("high");
        FabricDropDispatcher.registerLiving(EventPriority.HIGH, (entity, source) -> relevant.get(), highHandler);
        FabricDropDispatcher.LivingPlan second = FabricDropDispatcher.planLiving(null);

        assertEquals(FabricDropDispatcher.Mode.DIRECT, first.mode());
        assertEquals(FabricDropDispatcher.Mode.DIRECT, second.mode());
        assertEquals(baselineSize, first.entries().size(), "an old plan changed after a new generation was published");
        assertEquals(baselineSize + 1, second.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear(),
            "published generations must not be mutable through a plan");

        FabricDropDispatcher.dispatchLiving(first, null, null, List.of());
        assertEquals(List.of("low"), order);
        order.clear();
        FabricDropDispatcher.dispatchLiving(second, null, null, List.of());
        assertEquals(List.of("high", "low"), order,
            "priority ordering changed or the snapshot picked up a later registration");

        relevant.set(false);
        FabricDropDispatcher.LivingPlan inactive = FabricDropDispatcher.planLiving(null);
        assertEquals(FabricDropDispatcher.Mode.NONE, inactive.mode());
        assertSame(inactive, FabricDropDispatcher.planLiving(null),
            "irrelevant calls should reuse the inactive plan rather than allocate per death");

        LivingDrops external = context -> {};
        PlaceboEvents.LIVING_DROPS.register(external);
        try {
            FabricDropDispatcher.LivingPlan fallback = FabricDropDispatcher.planLiving(null);
            assertEquals(FabricDropDispatcher.Mode.FALLBACK, fallback.mode());
            assertEquals(0, fallback.relevantMask());
        }
        finally {
            PlaceboEvents.LIVING_DROPS.unregister(external);
        }
    }

    @Test
    void blockGenerationsUnionCaptureKindsAndFallbackAlwaysCapturesBoth() {
        AtomicBoolean relevant = new AtomicBoolean(true);
        List<String> order = new ArrayList<>();
        FabricDropDispatcher.registerBlock(EventPriority.LOW, (level, pos, state, breaker, tool) -> relevant.get(),
            FabricDropDispatcher.CaptureKind.XP_ONLY,
            (level, pos, state, breaker, tool, drops, experience) -> {
                order.add("low");
                return experience + 1;
            });
        FabricDropDispatcher.BlockPlan first = FabricDropDispatcher.planBlock(null, null, null, null, ItemStack.EMPTY);
        int baselineSize = first.entries().size();

        FabricDropDispatcher.registerBlock(EventPriority.HIGHEST,
            (level, pos, state, breaker, tool) -> relevant.get(),
            FabricDropDispatcher.CaptureKind.ITEMS_ONLY,
            (level, pos, state, breaker, tool, drops, experience) -> {
                order.add("high");
                return experience + 2;
            });
        FabricDropDispatcher.BlockPlan second = FabricDropDispatcher.planBlock(null, null, null, null, ItemStack.EMPTY);

        assertEquals(baselineSize, first.entries().size(), "an old block plan changed after publication");
        assertEquals(baselineSize + 1, second.entries().size());
        assertEquals(2, Long.bitCount(second.relevantMask()));
        assertTrue(second.capturesItems());
        assertTrue(second.capturesExperience());
        assertEquals(2, first.captureKinds(), "XP_ONLY must capture experience without item entities");
        assertThrows(UnsupportedOperationException.class, () -> second.entries().clear());

        assertEquals(3, FabricDropDispatcher.dispatchBlock(second, null, null, null, null, ItemStack.EMPTY,
            new ArrayList<>(), 0));
        assertEquals(List.of("high", "low"), order);

        BlockDrops external = context -> {};
        PlaceboEvents.BLOCK_DROPS.register(external);
        try {
            FabricDropDispatcher.BlockPlan fallback = FabricDropDispatcher.planBlock(null, null, null, null,
                ItemStack.EMPTY);
            assertEquals(FabricDropDispatcher.Mode.FALLBACK, fallback.mode());
            assertTrue(fallback.capturesItems(), "fallback must retain item identities even for owned XP_ONLY");
            assertTrue(fallback.capturesExperience(), "fallback must retain experience even for owned ITEMS_ONLY");

            relevant.set(false);
            FabricDropDispatcher.BlockPlan fallbackWithoutOwned = FabricDropDispatcher.planBlock(null, null, null,
                null, ItemStack.EMPTY);
            assertEquals(FabricDropDispatcher.Mode.FALLBACK, fallbackWithoutOwned.mode());
            assertEquals(3, fallbackWithoutOwned.captureKinds());

            FabricDropDispatcher.BlockPlanTarget fallbackTarget = new FabricDropDispatcher.BlockPlanTarget();
            FabricDropDispatcher.planBlockInto(fallbackTarget, null, null, null, null, ItemStack.EMPTY);
            assertEquals(FabricDropDispatcher.Mode.FALLBACK, fallbackTarget.mode());
            assertEquals(3, fallbackTarget.captureKinds(), "reusable fallback targets must capture both fields");
        }
        finally {
            PlaceboEvents.BLOCK_DROPS.unregister(external);
        }

        assertEquals(FabricDropDispatcher.Mode.NONE,
            FabricDropDispatcher.planBlock(null, null, null, null, ItemStack.EMPTY).mode());
        assertSame(FabricDropDispatcher.planBlock(null, null, null, null, ItemStack.EMPTY),
            FabricDropDispatcher.planBlock(null, null, null, null, ItemStack.EMPTY));
    }

    @Test
    void typedRelevanceReceivesFullLivingAndBlockInputsAndTargetsAreReusable() {
        DamageSource sourceMarker = new DamageSource((Holder<DamageType>) null);
        AtomicReference<DamageSource> seenSource = new AtomicReference<>();
        FabricDropDispatcher.registerLiving(EventPriority.NORMAL, (entity, source) -> {
            seenSource.set(source);
            return source == sourceMarker;
        }, (entity, source, drops) -> {});

        FabricDropDispatcher.LivingPlanTarget livingTarget = new FabricDropDispatcher.LivingPlanTarget();
        FabricDropDispatcher.planLivingInto(livingTarget, null, sourceMarker);
        assertSame(sourceMarker, seenSource.get());
        assertEquals(FabricDropDispatcher.Mode.DIRECT, livingTarget.mode());
        assertTrue(livingTarget.relevantMask() != 0);
        FabricDropDispatcher.planLivingInto(livingTarget, null, null);
        assertEquals(FabricDropDispatcher.Mode.NONE, livingTarget.mode());

        BlockPos posMarker = new BlockPos(3, 4, 5);
        ItemStack toolMarker = ItemStack.EMPTY;
        AtomicReference<BlockPos> seenPos = new AtomicReference<>();
        AtomicReference<ItemStack> seenTool = new AtomicReference<>();
        FabricDropDispatcher.registerBlock(EventPriority.NORMAL,
            (level, pos, state, breaker, tool) -> {
                seenPos.set(pos);
                seenTool.set(tool);
                return level == null && pos == posMarker && state == null && breaker == null && tool == toolMarker;
            }, FabricDropDispatcher.CaptureKind.ITEMS_XP,
            (level, pos, state, breaker, tool, drops, experience) -> experience);

        FabricDropDispatcher.BlockPlanTarget blockTarget = new FabricDropDispatcher.BlockPlanTarget();
        FabricDropDispatcher.planBlockInto(blockTarget, null, posMarker, null, null, toolMarker);
        assertSame(posMarker, seenPos.get());
        assertSame(toolMarker, seenTool.get());
        assertEquals(FabricDropDispatcher.Mode.DIRECT, blockTarget.mode());
        assertTrue(blockTarget.relevantMask() != 0);
    }

    @Test
    void despawnChainUsesPriorityAndRegistrationSequenceWithLastWriterReset() {
        List<String> order = new ArrayList<>();
        FabricDropDispatcher.registerDespawn(EventPriority.LOWEST, (mob, level, current) -> {
            order.add("late:" + current);
            return current;
        });
        FabricDropDispatcher.registerDespawn(EventPriority.HIGHEST, (mob, level, current) -> {
            order.add("early:" + current);
            return DespawnResult.ALLOW;
        });
        FabricDropDispatcher.registerDespawn(EventPriority.NORMAL, (mob, level, current) -> {
            order.add("middle-first:" + current);
            return DespawnResult.DENY;
        });
        FabricDropDispatcher.registerDespawn(EventPriority.NORMAL, (mob, level, current) -> {
            order.add("middle-second:" + current);
            return DespawnResult.DEFAULT;
        });

        assertEquals(DespawnResult.DEFAULT, FabricDropDispatcher.dispatchDespawn(null, null, DespawnResult.DEFAULT));
        assertEquals(List.of("early:DEFAULT", "middle-first:ALLOW", "middle-second:DENY", "late:DEFAULT"), order);
    }
}
