package dev.shadowsoffire.placebo.dynreg;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.common.base.Preconditions;

import dev.shadowsoffire.placebo.dynreg.WeightedDynamicRegistry.ILuckyWeighted;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicHolderSet;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Level;

/**
 * An extension of {@link DynamicRegistry} with support for weighted entries, including various utilities for accessing items randomly.
 *
 * @param <V>
 */
public abstract class WeightedDynamicRegistry<V extends ILuckyWeighted> extends DynamicRegistry<V> {

    private static final Object[] EMPTY_SELECTION_VALUES = new Object[0];
    private static final int[] EMPTY_SELECTION_WEIGHTS = new int[0];
    private volatile SelectionGeneration<V> selectionGeneration = SelectionGeneration.empty(this.registry);
    /** Compatibility field for subclasses; selection reads the atomically-published generation below. */
    protected volatile WeightedList<V> zeroLuckList = WeightedList.of();

    /**
     * Reusable weighted-selection storage. Frames are depth-aware because filters are extension points and may
     * re-enter a registry while an outer selection is in progress. Arrays grow only on first use or capacity growth.
     */
    private final ThreadLocal<SelectionScratch<V>> selectionScratch = ThreadLocal.withInitial(SelectionScratch::new);

    public WeightedDynamicRegistry(Logger logger, Identifier id, RegistrySerializer<V> serializer) {
        super(logger, id, serializer);
    }

    @Override
    protected void beginReload(ReloadType type) {
        super.beginReload(type);
        this.publishGeneration(SelectionGeneration.empty(this.registry));
    }

    @Override
    protected void validateItem(Identifier key, V item) {
        super.validateItem(key, item);
        Preconditions.checkArgument(item.getQuality() >= 0, "Item may not have negative quality!");
        Preconditions.checkArgument(item.getWeight() >= 0, "Item may not have negative weight!");
    }

    @Override
    protected void onReload(ReloadType type) {
        super.onReload(type);
        // The base hook has replaced the mutable map and run callbacks. Keep selection empty while building the
        // complete replacement so a throwing dynamic getter cannot expose a partially-built cache.
        this.publishGeneration(SelectionGeneration.empty(this.registry));
        Object[] values = this.registry.values().toArray();
        WeightedList.Builder<V> builder = WeightedList.builder();
        Object[] zeroValues = new Object[values.length];
        int[] zeroWeights = new int[values.length];
        int zeroSize = 0;
        long zeroTotal = 0;
        for (Object rawItem : values) {
            @SuppressWarnings("unchecked")
            V item = (V) rawItem;
            int weight = item.getWeight();
            if (weight > 0) {
                builder.add(item, weight);
                zeroValues[zeroSize] = item;
                zeroWeights[zeroSize++] = weight;
                zeroTotal += weight;
            }
        }
        if (zeroTotal > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        }
        WeightedList<V> zeroLuck = builder.build();
        SelectionGeneration<V> generation = new SelectionGeneration<>(this.registry, values,
            Arrays.copyOf(zeroValues, zeroSize), Arrays.copyOf(zeroWeights, zeroSize), (int) zeroTotal, zeroLuck);
        this.publishGeneration(generation);
    }

    /**
     * Gets a random item from this manager, ignoring luck.
     */
    @Nullable
    public V getRandomItem(RandomSource rand) {
        return this.getRandomItem(rand, 0);
    }

    /**
     * Gets a random item from this manager, re-calculating the weights based on luck.
     */
    @Nullable
    public V getRandomItem(RandomSource rand, float luck) {
        SelectionGeneration<V> generation = this.selectionGeneration;
        if (luck == 0) {
            if (generation.zeroTotal == 0) {
                return null;
            }
            int roll = rand.nextInt(generation.zeroTotal);
            for (int i = 0; i < generation.zeroWeights.length; i++) {
                roll -= generation.zeroWeights[i];
                if (roll < 0) {
                    @SuppressWarnings("unchecked")
                    V value = (V) generation.zeroValues[i];
                    return value;
                }
            }
            return null;
        }
        return this.select(rand, luck, null, null);
    }

    /**
     * Gets a random item from this manager, re-calculating the weights based on luck and omitting items based on a filter.
     */
    @Nullable
    @SafeVarargs
    public final V getRandomItem(RandomSource rand, float luck, Predicate<V>... filters) {
        if (filters == null) {
            throw new NullPointerException();
        }
        return this.select(rand, luck, null, filters);
    }

    /**
     * Gets a random item from the given tag, re-calculating the weights based on luck.
     *
     * @return A random item from the tag, or null if the tag is unbound or empty.
     */
    @Nullable
    public V getRandomFromTag(DynamicTagKey<V> tag, RandomSource rand, float luck) {
        DynamicHolderSet.Named<V> set = this.getBoundTag(tag);
        return set == null ? null : this.getRandomFromSet(set, rand, luck);
    }

    /**
     * Gets a random item from the given holder set, re-calculating the weights based on luck. Unbound or empty
     * holders are skipped.
     *
     * @return A random item from the set, or null if the set has no bound entries with positive weight.
     */
    @Nullable
    public V getRandomFromSet(DynamicHolderSet<V> set, RandomSource rand, float luck) {
        return this.selectFromSet(rand, luck, set);
    }

    @Nullable
    private V select(RandomSource rand, float luck, @Nullable Predicate<V> singleFilter, @Nullable Predicate<V>[] filters) {
        return this.selectInternal(rand, luck, singleFilter, null, filters);
    }

    @Nullable
    private V selectFromSet(RandomSource rand, float luck, DynamicHolderSet<V> allowedSet) {
        return this.selectInternal(rand, luck, null, allowedSet, null);
    }

    @Nullable
    private V selectInternal(RandomSource rand, float luck, @Nullable Predicate<V> singleFilter,
        @Nullable DynamicHolderSet<V> allowedSet, @Nullable Predicate<V>[] filters) {
        // One volatile generation owns the iteration array and is intentionally used without consulting the mutable
        // base registry. Reload publishes an empty generation before constructing the replacement.
        SelectionGeneration<V> generation = this.selectionGeneration;
        Object[] values = generation.values;
        SelectionScratch<V> scratch = this.selectionScratch.get();
        SelectionScratch.Frame<V> frame = scratch.acquire(values.length);
        try {
            long totalWeight = 0;
            for (Object rawItem : values) {
                @SuppressWarnings("unchecked")
                V item = (V) rawItem;
                if (allowedSet != null && !allowedSet.contains(item)) {
                    continue;
                }
                if (singleFilter != null && !singleFilter.test(item)) {
                    continue;
                }
                if (filters != null) {
                    boolean accepted = true;
                    for (Predicate<V> filter : filters) {
                        if (!filter.test(item)) {
                            accepted = false;
                            break;
                        }
                    }
                    if (!accepted) {
                        continue;
                    }
                }

                // Preserve the legacy operand evaluation order: getWeight(), then getQuality(), then the int
                // arithmetic and clamp. Each dynamic value is resolved exactly once for this candidate.
                int weight = Math.max(0, item.getWeight() + (int) (luck * item.getQuality()));
                if (weight > 0) {
                    frame.values[frame.size] = item;
                    frame.weights[frame.size++] = weight;
                    totalWeight += weight;
                }
            }

            if (totalWeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
            }
            if (totalWeight == 0) {
                return null;
            }

            int roll = rand.nextInt((int) totalWeight);
            for (int i = 0; i < frame.size; i++) {
                int weight = frame.weights[i];
                roll -= weight;
                if (roll < 0) {
                    return frame.valueAt(i);
                }
            }
            return null;
        }
        finally {
            frame.clear();
            scratch.release();
        }
    }

    private void publishGeneration(SelectionGeneration<V> generation) {
        this.zeroLuckList = generation.zeroLuckList;
        this.selectionGeneration = generation;
    }

    private static final class SelectionGeneration<T> {

        private final Object registry;
        private final Object[] values;
        private final Object[] zeroValues;
        private final int[] zeroWeights;
        private final int zeroTotal;
        private final WeightedList<T> zeroLuckList;

        private SelectionGeneration(Object registry, Object[] values, Object[] zeroValues, int[] zeroWeights, int zeroTotal,
            WeightedList<T> zeroLuckList) {
            this.registry = registry;
            this.values = values;
            this.zeroValues = zeroValues;
            this.zeroWeights = zeroWeights;
            this.zeroTotal = zeroTotal;
            this.zeroLuckList = zeroLuckList;
        }

        private static <T> SelectionGeneration<T> empty(Object registry) {
            return new SelectionGeneration<>(registry, EMPTY_SELECTION_VALUES, EMPTY_SELECTION_VALUES, EMPTY_SELECTION_WEIGHTS, 0,
                WeightedList.of());
        }
    }

    private static final class SelectionScratch<T> {

        @SuppressWarnings("unchecked")
        private Frame<T>[] frames = (Frame<T>[]) new Frame<?>[2];
        private int depth;

        private Frame<T> acquire(int capacity) {
            if (this.depth == this.frames.length) {
                this.frames = Arrays.copyOf(this.frames, this.frames.length * 2);
            }
            Frame<T> frame = this.frames[this.depth];
            if (frame == null) {
                frame = this.frames[this.depth] = new Frame<>();
            }
            this.depth++;
            frame.ensureCapacity(capacity);
            return frame;
        }

        private void release() {
            this.depth--;
        }

        private static final class Frame<T> {

            private Object[] values = new Object[0];
            private int[] weights = new int[0];
            private int size;

            private void ensureCapacity(int capacity) {
                if (this.values.length < capacity) {
                    this.values = new Object[capacity];
                    this.weights = new int[capacity];
                }
                this.size = 0;
            }

            @SuppressWarnings("unchecked")
            private T valueAt(int index) {
                return (T) this.values[index];
            }

            private void clear() {
                Arrays.fill(this.values, 0, this.size, null);
                this.size = 0;
            }
        }
    }

    /**
     * An item that will hold both a quality and a weight, for use with luck-based loot systems.
     * Luck increases the weight of an item by <quality> for each point of luck.
     */
    public static interface ILuckyWeighted {

        /**
         * @return The quality of this item. May not be negative.
         */
        public float getQuality();

        /**
         * @return The weight of this item. May not be negative.
         */
        public int getWeight();

        /**
         * Helper to wrap this object as a {@link Weighted} entry, with its weight adjusted by the given luck value.
         */
        @SuppressWarnings("unchecked")
        default <T extends ILuckyWeighted> Weighted<T> wrap(float luck) {
            return wrap((T) this, luck);
        }

        /**
         * Static (and more generic-safe) variant of {@link ILuckyWeighted#wrap(float)}
         */
        static <T extends ILuckyWeighted> Weighted<T> wrap(T item, float luck) {
            return new Weighted<>(item, Math.max(0, item.getWeight() + (int) (luck * item.getQuality())));
        }
    }

    /**
     * An item that is limited on a per-dimension basis.
     */
    public static interface IDimensional {

        /**
         * Null or empty means "all dimensions". To make an item invalid, return 0 weight.
         *
         * @return A set of the names of all dimensions this item is available in.
         */
        @Nullable
        Set<Identifier> getDimensions();

        /**
         * Creates a new predicate matching objects limited to the passed dimension.
         */
        public static <T extends IDimensional> Predicate<T> createPredicate(Identifier dimId) {
            return obj -> {
                Set<Identifier> dims = obj.getDimensions();
                return dims == null || dims.isEmpty() || dims.contains(dimId);
            };
        }

        public static <T extends IDimensional> Predicate<T> matches(Level level) {
            return createPredicate(level.dimension().identifier());
        }
    }

}
