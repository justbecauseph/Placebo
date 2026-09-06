package dev.shadowsoffire.placebo.dynreg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

final class WeightedDynamicRegistrySelectionTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void reloadCacheResolvesEachWeightOnceAndSelectionDrawsAtMostOnce() {
        TestItem item = new TestItem(3, 2);
        TestRegistry registry = new TestRegistry();
        registry.reload(item);
        item.resetCounts();

        CountingRandom random = new CountingRandom(0);
        assertEquals(item, registry.getRandomItem(random));
        assertEquals(0, item.weightCalls, "zero-luck selection must use the reload cache");
        assertEquals(1, random.nextIntCalls);
        assertEquals(3, random.lastBound);

        item.resetCounts();
        random.reset();
        assertEquals(item, registry.getRandomItem(random, 1));
        assertEquals(1, item.weightCalls, "luck selection must resolve weight once");
        assertEquals(1, item.qualityCalls, "luck selection must resolve quality once");
        assertEquals(1, random.nextIntCalls);
        assertEquals(5, random.lastBound);
    }

    @Test
    void nullVarargsRetainsLegacyNullPointerException() {
        TestRegistry registry = new TestRegistry();
        @SuppressWarnings("unchecked")
        Predicate<TestItem>[] filters = (Predicate<TestItem>[]) null;
        assertThrows(NullPointerException.class, () -> registry.getRandomItem(RandomSource.create(1), 1, filters));
    }

    @Test
    void zeroWeightDoesNotDrawAndReloadPublishesReplacement() {
        TestItem oldItem = new TestItem(0, 0);
        TestItem newItem = new TestItem(4, 0);
        TestRegistry registry = new TestRegistry();
        registry.reload(oldItem);

        CountingRandom random = new CountingRandom(0);
        assertNull(registry.getRandomItem(random));
        assertEquals(0, random.nextIntCalls);

        registry.reload(newItem);
        random.reset();
        assertEquals(newItem, registry.getRandomItem(random));
        assertEquals(1, random.nextIntCalls);
        assertEquals(4, random.lastBound);
    }

    @Test
    void boundaryTotalsUseTheSamePositiveIntervalAndOverflowMessage() {
        for (int total : new int[] {1, 63, 64, Integer.MAX_VALUE}) {
            TestRegistry registry = new TestRegistry();
            TestItem item = new TestItem(total, 0);
            registry.reload(item);
            CountingRandom random = new CountingRandom(0);
            assertEquals(item, registry.getRandomItem(random));
            assertEquals(1, random.nextIntCalls);
            assertEquals(total, random.lastBound);
        }

        TestRegistry overflow = new TestRegistry();
        assertThrowsWithMessage(IllegalArgumentException.class, "Sum of weights must be <= 2147483647",
            () -> overflow.reload(new TestItem(Integer.MAX_VALUE, 0), new TestItem(1, 0)));
    }

    @Test
    void luckArithmeticRetainsLegacyFloatToIntEdges() {
        TestRegistry registry = new TestRegistry();
        TestItem item = new TestItem(1, 2);
        registry.reload(item);

        CountingRandom random = new CountingRandom(0);
        assertEquals(item, registry.getRandomItem(random, Float.NaN));
        assertEquals(1, random.lastBound);

        random.reset();
        assertNull(registry.getRandomItem(random, Float.POSITIVE_INFINITY));
        assertEquals(0, random.nextIntCalls);

        random.reset();
        assertNull(registry.getRandomItem(random, Float.NEGATIVE_INFINITY));
        assertEquals(0, random.nextIntCalls);
    }

    private static <T extends Throwable> void assertThrowsWithMessage(Class<T> type, String message, Runnable action) {
        T error = assertThrows(type, action::run);
        assertEquals(message, error.getMessage());
    }

    @Test
    void nestedSelectionKeepsOuterScratchIntact() {
        TestRegistry registry = new TestRegistry();
        TestItem item = new TestItem(7, 0);
        item.owner = registry;
        registry.reload(item);

        item.resetCounts();
        CountingRandom random = new CountingRandom(0);
        registry.reentryRandom = random;
        item.reenter = true;
        assertNotNull(registry.getRandomItem(random, 1));
        assertEquals(2, random.nextIntCalls, "outer and nested selections each draw once");
        assertEquals(2, item.weightCalls, "each nested selection must resolve its accepted item once");
    }

    @Test
    void exceptionReleasesScratchForTheNextSelection() {
        TestRegistry registry = new TestRegistry();
        TestItem item = new TestItem(2, 0);
        registry.reload(item);
        item.throwOnWeight = true;

        assertThrows(IllegalStateException.class, () -> registry.getRandomItem(RandomSource.create(3), 1));
        item.throwOnWeight = false;
        assertEquals(item, registry.getRandomItem(RandomSource.create(3), 1));
    }

    @Test
    void throwingReloadGetterLeavesThePublishedZeroGenerationEmpty() {
        TestRegistry registry = new TestRegistry();
        TestItem item = new TestItem(3, 0);
        registry.reloadWithCacheFailure(item);

        CountingRandom random = new CountingRandom(0);
        assertNull(registry.getRandomItem(random));
        assertEquals(0, random.nextIntCalls);
    }

    @Test
    void reloadCallbackSeesTheStagedEmptyGenerationAndPostReloadSeesReplacement() {
        TestRegistry registry = new TestRegistry();
        TestItem item = new TestItem(3, 0);
        TestItem[] callbackResult = new TestItem[1];
        registry.addCallback(RegistryCallback.reloadOnly(manager -> callbackResult[0] = registry.getRandomItem(RandomSource.create(1))));

        registry.reload(item);
        assertNull(callbackResult[0], "reload callbacks must not select from a stale or partially-mutated map");
        assertEquals(item, registry.getRandomItem(RandomSource.create(1)));
    }

    private static final class TestRegistry extends WeightedDynamicRegistry<TestItem> {

        private RandomSource reentryRandom;

        private TestRegistry() {
            super(LoggerFactory.getLogger("WeightedDynamicRegistrySelectionTest"),
                Identifier.fromNamespaceAndPath("placebo_test", "weighted_" + IDS.incrementAndGet()),
                RegistrySerializer.simple(com.mojang.serialization.Codec.INT.xmap(i -> new TestItem(i, 0), item -> item.weight)));
        }

        private void reload(TestItem... items) {
            this.beginReload(ReloadType.SERVER);
            for (int i = 0; i < items.length; i++) {
                this.register(Identifier.fromNamespaceAndPath("placebo_test", "item_" + i), items[i]);
            }
            this.onReload(ReloadType.SERVER);
        }

        private void reloadWithCacheFailure(TestItem item) {
            this.beginReload(ReloadType.SERVER);
            this.register(Identifier.fromNamespaceAndPath("placebo_test", "item_failure"), item);
            item.throwOnWeight = true;
            try {
                assertThrows(IllegalStateException.class, () -> this.onReload(ReloadType.SERVER));
            }
            finally {
                item.throwOnWeight = false;
            }
        }
    }

    private static final class TestItem implements WeightedDynamicRegistry.ILuckyWeighted {

        private final int weight;
        private final float quality;
        private int weightCalls;
        private int qualityCalls;
        private TestRegistry owner;
        private boolean reenter;
        private boolean entered;
        private boolean throwOnWeight;

        private TestItem(int weight, float quality) {
            this.weight = weight;
            this.quality = quality;
        }

        @Override
        public float getQuality() {
            this.qualityCalls++;
            return this.quality;
        }

        @Override
        public int getWeight() {
            this.weightCalls++;
            if (this.throwOnWeight) {
                throw new IllegalStateException("test weight failure");
            }
            if (this.reenter && !this.entered) {
                this.entered = true;
                try {
                    this.owner.getRandomItem(this.owner.reentryRandom, 1);
                }
                finally {
                    this.entered = false;
                }
            }
            return this.weight;
        }

        private void resetCounts() {
            this.weightCalls = 0;
            this.qualityCalls = 0;
        }
    }

    private static final class CountingRandom implements RandomSource {

        private final RandomSource delegate;
        private int nextIntCalls;
        private int lastBound;

        private CountingRandom(long seed) {
            this.delegate = RandomSource.create(seed);
        }

        private void reset() {
            this.nextIntCalls = 0;
            this.lastBound = 0;
        }

        @Override
        public int nextInt(int bound) {
            this.nextIntCalls++;
            this.lastBound = bound;
            return this.delegate.nextInt(bound);
        }

        @Override
        public RandomSource fork() {
            return this.delegate.fork();
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return this.delegate.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            this.delegate.setSeed(seed);
        }

        @Override
        public int nextInt() {
            return this.delegate.nextInt();
        }

        @Override
        public long nextLong() {
            return this.delegate.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return this.delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return this.delegate.nextFloat();
        }

        @Override
        public double nextDouble() {
            return this.delegate.nextDouble();
        }

        @Override
        public double nextGaussian() {
            return this.delegate.nextGaussian();
        }
    }
}
