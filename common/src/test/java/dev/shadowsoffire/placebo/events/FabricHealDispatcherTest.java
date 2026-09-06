package dev.shadowsoffire.placebo.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class FabricHealDispatcherTest {

    @Test
    void packedResultsPreserveRawFloatAndCancellation() {
        int[] rawValues = {0x80000000, 0x7f800000, 0xff800000, 0x7fc01234, 0x00000000};
        for (int raw : rawValues) {
            float value = Float.intBitsToFloat(raw);
            long pass = FabricHealDispatcher.result(value, false);
            long cancelled = FabricHealDispatcher.result(value, true);
            assertEquals(raw, Float.floatToRawIntBits(FabricDamageDispatcher.unpackAmount(pass)));
            assertEquals(raw, Float.floatToRawIntBits(FabricDamageDispatcher.unpackAmount(cancelled)));
            assertFalse(FabricDamageDispatcher.isCancelled(pass));
            assertTrue(FabricDamageDispatcher.isCancelled(cancelled));
        }
    }

    @Test
    void publicHealPreservesPriorityAndBothInterruptResults() {
        List<String> order = new ArrayList<>();
        PlaceboEvents.Heal high = ctx -> {
            order.add("high");
            ctx.setAmount(9F);
            return dev.architectury.event.EventResult.interruptTrue();
        };
        PlaceboEvents.Heal low = ctx -> {
            order.add("low");
            ctx.setAmount(2F);
            return dev.architectury.event.EventResult.pass();
        };
        PlaceboEvents.LIVING_HEAL.register(dev.architectury.event.EventPriority.HIGH, high);
        PlaceboEvents.LIVING_HEAL.register(dev.architectury.event.EventPriority.LOW, low);
        try {
            assertEquals(9F, PlaceboEvents.fireLivingHeal(null, 1F));
            assertEquals(List.of("high"), order, "interruptTrue must stop lower-priority listeners");
        }
        finally {
            PlaceboEvents.LIVING_HEAL.unregister(high);
            PlaceboEvents.LIVING_HEAL.unregister(low);
        }

        order.clear();
        PlaceboEvents.Heal pass = ctx -> {
            order.add("pass");
            return dev.architectury.event.EventResult.pass();
        };
        PlaceboEvents.Heal cancel = ctx -> {
            order.add("cancel");
            return dev.architectury.event.EventResult.interruptFalse();
        };
        PlaceboEvents.LIVING_HEAL.register(dev.architectury.event.EventPriority.HIGH, pass);
        PlaceboEvents.LIVING_HEAL.register(dev.architectury.event.EventPriority.LOW, cancel);
        try {
            assertEquals(0F, PlaceboEvents.fireLivingHeal(null, 1F));
            assertEquals(List.of("pass", "cancel"), order);
        }
        finally {
            PlaceboEvents.LIVING_HEAL.unregister(pass);
            PlaceboEvents.LIVING_HEAL.unregister(cancel);
        }
    }

    @Test
    void publicHealSupportsNestedDispatchWithoutSharedMutableContext() {
        AtomicInteger calls = new AtomicInteger();
        PlaceboEvents.Heal nested = ctx -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(4F, PlaceboEvents.fireLivingHeal(null, 3F));
            }
            ctx.setAmount(ctx.getAmount() + 1F);
            return dev.architectury.event.EventResult.pass();
        };
        PlaceboEvents.LIVING_HEAL.register(nested);
        try {
            assertEquals(2F, PlaceboEvents.fireLivingHeal(null, 1F));
            assertEquals(2, calls.get());
        }
        finally {
            PlaceboEvents.LIVING_HEAL.unregister(nested);
        }
    }

    @Test
    void directDispatchSupportsNestedCallsAndStopsOnCancellation() {
        AtomicInteger attributeCalls = new AtomicInteger();
        AtomicInteger nestedCalls = new AtomicInteger();
        List<Float> enchantingAmounts = new ArrayList<>();

        FabricHealDispatcher.registerAttributes((entity, amount) -> {
            attributeCalls.incrementAndGet();
            if (amount == 1F && nestedCalls.compareAndSet(0, 1)) {
                long nested = FabricHealDispatcher.dispatch(null, 3F);
                assertFalse(FabricDamageDispatcher.isCancelled(nested));
                assertEquals(8F, FabricDamageDispatcher.unpackAmount(nested));
            }
            if (amount < 0F) return FabricHealDispatcher.result(amount, true);
            return FabricHealDispatcher.result(amount + 1F, false);
        });
        FabricHealDispatcher.registerEnchanting((entity, amount) -> {
            enchantingAmounts.add(amount);
            return FabricHealDispatcher.result(amount * 2F, false);
        });

        long outer = FabricHealDispatcher.dispatch(null, 1F);
        assertEquals(4F, FabricDamageDispatcher.unpackAmount(outer));
        assertEquals(List.of(4F, 2F), enchantingAmounts,
            "nested direct dispatch must not reuse the outer amount");
        int enchantingCount = enchantingAmounts.size();

        long cancelled = FabricHealDispatcher.dispatch(null, -2F);
        assertTrue(FabricDamageDispatcher.isCancelled(cancelled));
        assertEquals(-2F, FabricDamageDispatcher.unpackAmount(cancelled));
        assertEquals(enchantingCount, enchantingAmounts.size(),
            "Attributes cancellation must stop the lower direct slot");
        assertEquals(3, attributeCalls.get());
    }
}
