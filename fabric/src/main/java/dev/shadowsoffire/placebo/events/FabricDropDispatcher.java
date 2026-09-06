package dev.shadowsoffire.placebo.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.architectury.event.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ServerLevelAccessor;

import dev.shadowsoffire.placebo.events.PlaceboEvents.DespawnResult;

/**
 * Fabric's fixed dispatch registry for the two drop events.
 * <p>
 * Registration is a startup operation. Each registration publishes a new immutable generation, and a plan
 * keeps the generation it observed so that registration during an invocation cannot change that invocation's
 * ordering or relevance mask. Public event registrations stay on the tracked-event fallback until the
 * downstream owned handlers are migrated in the later optimization phase.
 */
public final class FabricDropDispatcher {

    /** Describes which part of a block drop an owned handler needs to observe. */
    public enum CaptureKind {
        ITEMS_XP,
        ITEMS_ONLY,
        XP_ONLY
    }

    @FunctionalInterface
    public interface LivingHandler {
        void handle(LivingEntity entity, DamageSource source, java.util.Collection<ItemEntity> drops);
    }

    @FunctionalInterface
    public interface BlockHandler {
        int handle(ServerLevel level, BlockPos pos, BlockState state, Entity breaker, ItemStack tool,
            List<ItemEntity> drops, int experience);
    }

    @FunctionalInterface
    public interface LivingRelevance {
        boolean test(LivingEntity entity, DamageSource source);
    }

    @FunctionalInterface
    public interface BlockRelevance {
        boolean test(ServerLevel level, BlockPos pos, BlockState state, Entity breaker, ItemStack tool);
    }

    /** A loader-owned despawn handler; the current result is carried through for last-writer-wins semantics. */
    @FunctionalInterface
    public interface DespawnHandler {
        DespawnResult handle(Mob mob, ServerLevelAccessor level, DespawnResult current);
    }

    /** An owned handler and its immutable registration metadata. */
    public record LivingEntry(LivingRelevance relevant, LivingHandler handler, EventPriority priority,
        long sequence) {}

    /** An owned handler and its immutable registration metadata. */
    public record BlockEntry(BlockRelevance relevant, BlockHandler handler, CaptureKind kind,
        EventPriority priority, long sequence) {}

    /** An owned despawn handler and its immutable registration metadata. */
    public record DespawnEntry(DespawnHandler handler, EventPriority priority, long sequence) {}

    public enum Mode {
        NONE,
        DIRECT,
        FALLBACK
    }

    /** A per-invocation living-drop generation and cheap relevance mask. */
    public record LivingPlan(Mode mode, List<LivingEntry> entries, long relevantMask) {
        public boolean isDirect() {
            return mode == Mode.DIRECT;
        }

        public boolean isFallback() {
            return mode == Mode.FALLBACK;
        }
    }

    /** A per-invocation block-drop generation, relevance mask, and union of required capture kinds. */
    public record BlockPlan(Mode mode, List<BlockEntry> entries, long relevantMask, int captureKinds) {
        public boolean isDirect() {
            return mode == Mode.DIRECT;
        }

        public boolean isFallback() {
            return mode == Mode.FALLBACK;
        }

        public boolean capturesItems() {
            return (captureKinds & 1) != 0;
        }

        public boolean capturesExperience() {
            return (captureKinds & 2) != 0;
        }
    }

    /**
     * Mutable per-invocation target used by the Fabric mixins. The target is owned by a depth frame and is
     * populated in place, so a relevant direct invocation does not allocate a plan record. The handler list
     * stored here is still an immutable published generation; only the mode and mask are mutable.
     */
    public static final class LivingPlanTarget {
        private Mode mode = Mode.NONE;
        private List<LivingEntry> entries = NO_LIVING;
        private long relevantMask;

        public Mode mode() {
            return this.mode;
        }

        public List<LivingEntry> entries() {
            return this.entries;
        }

        public long relevantMask() {
            return this.relevantMask;
        }

        public boolean isDirect() {
            return this.mode == Mode.DIRECT;
        }

        public boolean isFallback() {
            return this.mode == Mode.FALLBACK;
        }

        public void reset() {
            this.set(Mode.NONE, NO_LIVING, 0);
        }

        private void set(Mode mode, List<LivingEntry> entries, long relevantMask) {
            this.mode = mode;
            this.entries = entries;
            this.relevantMask = relevantMask;
        }
    }

    /** Mutable per-invocation target counterpart for block-drop frames. */
    public static final class BlockPlanTarget {
        private Mode mode = Mode.NONE;
        private List<BlockEntry> entries = NO_BLOCK;
        private long relevantMask;
        private int captureKinds;

        public Mode mode() {
            return this.mode;
        }

        public List<BlockEntry> entries() {
            return this.entries;
        }

        public long relevantMask() {
            return this.relevantMask;
        }

        public int captureKinds() {
            return this.captureKinds;
        }

        public boolean isDirect() {
            return this.mode == Mode.DIRECT;
        }

        public boolean isFallback() {
            return this.mode == Mode.FALLBACK;
        }

        public boolean capturesItems() {
            return (this.captureKinds & 1) != 0;
        }

        public boolean capturesExperience() {
            return (this.captureKinds & 2) != 0;
        }

        public void reset() {
            this.set(Mode.NONE, NO_BLOCK, 0, 0);
        }

        private void set(Mode mode, List<BlockEntry> entries, long relevantMask, int captureKinds) {
            this.mode = mode;
            this.entries = entries;
            this.relevantMask = relevantMask;
            this.captureKinds = captureKinds;
        }
    }

    private static final int MAX_ENTRIES = Long.SIZE;
    private static final List<LivingEntry> NO_LIVING = List.of();
    private static final List<BlockEntry> NO_BLOCK = List.of();
    private static final List<DespawnEntry> NO_DESPAWN = List.of();
    private static final LivingPlan NONE_LIVING = new LivingPlan(Mode.NONE, NO_LIVING, 0);
    private static final LivingPlan FALLBACK_LIVING = new LivingPlan(Mode.FALLBACK, NO_LIVING, 0);
    private static final BlockPlan NONE_BLOCK = new BlockPlan(Mode.NONE, NO_BLOCK, 0, 0);
    private static final BlockPlan FALLBACK_BLOCK = new BlockPlan(Mode.FALLBACK, NO_BLOCK, 0, 3);

    private static volatile List<LivingEntry> living = NO_LIVING;
    private static volatile List<BlockEntry> block = NO_BLOCK;
    private static volatile List<DespawnEntry> despawn = NO_DESPAWN;
    private static long livingSequence;
    private static long blockSequence;
    private static long despawnSequence;

    private FabricDropDispatcher() {}

    /** Registers one loader-owned living-drop handler in Architectury priority order. */
    public static synchronized void registerLiving(EventPriority priority, LivingRelevance relevant,
        LivingHandler handler) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(relevant, "relevant");
        Objects.requireNonNull(handler, "handler");
        if (living.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Too many Fabric living-drop handlers");
        }

        ArrayList<LivingEntry> next = new ArrayList<>(living);
        next.add(new LivingEntry(relevant, handler, priority, livingSequence++));
        next.sort(Comparator.comparingInt((LivingEntry entry) -> priorityRank(entry.priority()))
            .thenComparingLong(LivingEntry::sequence));
        living = List.copyOf(next);
    }

    /** Registers one loader-owned block-drop handler in Architectury priority order. */
    public static synchronized void registerBlock(EventPriority priority, BlockRelevance relevant,
        CaptureKind kind, BlockHandler handler) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(relevant, "relevant");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(handler, "handler");
        if (block.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Too many Fabric block-drop handlers");
        }

        ArrayList<BlockEntry> next = new ArrayList<>(block);
        next.add(new BlockEntry(relevant, handler, kind, priority, blockSequence++));
        next.sort(Comparator.comparingInt((BlockEntry entry) -> priorityRank(entry.priority()))
            .thenComparingLong(BlockEntry::sequence));
        block = List.copyOf(next);
    }

    /** Registers one loader-owned despawn handler in Architectury priority order. */
    public static synchronized void registerDespawn(EventPriority priority, DespawnHandler handler) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(handler, "handler");
        ArrayList<DespawnEntry> next = new ArrayList<>(despawn);
        next.add(new DespawnEntry(handler, priority, despawnSequence++));
        next.sort(Comparator.comparingInt((DespawnEntry entry) -> priorityRank(entry.priority()))
            .thenComparingLong(DespawnEntry::sequence));
        despawn = List.copyOf(next);
    }

    /** Runs the immutable owned generation without allocating a context or snapshot. */
    public static DespawnResult dispatchDespawn(Mob mob, ServerLevelAccessor level, DespawnResult current) {
        DespawnResult result = current;
        List<DespawnEntry> entries = despawn;
        for (int i = 0; i < entries.size(); i++) {
            DespawnResult updated = entries.get(i).handler().handle(mob, level, result);
            if (updated != null) result = updated;
        }
        return result;
    }

    /**
     * Plans a living-drop invocation. The public event mode and the owned relevance mask are read once before
     * dispatch. An irrelevant call with no public fallback listener returns the singleton inactive plan.
     */
    public static LivingPlan planLiving(LivingEntity entity, DamageSource source) {
        List<LivingEntry> generation = living;
        boolean directAllowed = PlaceboEvents.livingDropsDirectAllowed();
        boolean hasFallback = PlaceboEvents.livingDropsHasFallbackListeners();
        long mask = 0;
        for (int i = 0; i < generation.size(); i++) {
            if (generation.get(i).relevant().test(entity, source)) {
                mask |= 1L << i;
            }
        }

        if (mask == 0) {
            if (!hasFallback) return NONE_LIVING;
            return FALLBACK_LIVING;
        }
        return new LivingPlan(directAllowed ? Mode.DIRECT : Mode.FALLBACK, generation, mask);
    }

    /** Compatibility overload for tests and older internal callers that have no damage source. */
    public static LivingPlan planLiving(LivingEntity entity) {
        return planLiving(entity, null);
    }

    /** Populates a reusable living target without allocating a plan record. */
    public static void planLivingInto(LivingPlanTarget target, LivingEntity entity, DamageSource source) {
        Objects.requireNonNull(target, "target");
        List<LivingEntry> generation = living;
        boolean directAllowed = PlaceboEvents.livingDropsDirectAllowed();
        boolean hasFallback = PlaceboEvents.livingDropsHasFallbackListeners();
        long mask = 0;
        for (int i = 0; i < generation.size(); i++) {
            if (generation.get(i).relevant().test(entity, source)) {
                mask |= 1L << i;
            }
        }

        if (mask == 0) {
            target.set(hasFallback ? Mode.FALLBACK : Mode.NONE, NO_LIVING, 0);
        }
        else {
            target.set(directAllowed ? Mode.DIRECT : Mode.FALLBACK, generation, mask);
        }
    }

    /** Returns true without creating a state/frame when a living call has no owned or fallback observer. */
    public static boolean livingPlanIsNone(LivingEntity entity, DamageSource source) {
        if (PlaceboEvents.livingDropsHasFallbackListeners()) return false;
        List<LivingEntry> generation = living;
        for (int i = 0; i < generation.size(); i++) {
            if (generation.get(i).relevant().test(entity, source)) return false;
        }
        return true;
    }

    /**
     * Plans a block-drop invocation. The capture union is computed only from relevant owned handlers. Any
     * public fallback listener requires both item and experience capture, regardless of owned handler kinds.
     */
    public static BlockPlan planBlock(ServerLevel level, BlockPos pos, BlockState state, Entity breaker,
        ItemStack tool) {
        List<BlockEntry> generation = block;
        boolean directAllowed = PlaceboEvents.blockDropsDirectAllowed();
        boolean hasFallback = PlaceboEvents.blockDropsHasFallbackListeners();
        long mask = 0;
        int kinds = 0;
        for (int i = 0; i < generation.size(); i++) {
            BlockEntry entry = generation.get(i);
            if (!entry.relevant().test(level, pos, state, breaker, tool)) continue;
            mask |= 1L << i;
            kinds |= captureBits(entry.kind());
        }

        if (mask == 0) {
            if (!hasFallback) return NONE_BLOCK;
            return FALLBACK_BLOCK;
        }
        // The tracked public event can inspect and mutate both fields. Never let a relevant owned XP-only or
        // item-only handler narrow the capture when the invocation is already in compatibility mode.
        if (!directAllowed) kinds = 3;
        return new BlockPlan(directAllowed ? Mode.DIRECT : Mode.FALLBACK, generation, mask, kinds);
    }

    /** Populates a reusable block target without allocating a plan record. */
    public static void planBlockInto(BlockPlanTarget target, ServerLevel level, BlockPos pos, BlockState state,
        Entity breaker, ItemStack tool) {
        Objects.requireNonNull(target, "target");
        List<BlockEntry> generation = block;
        boolean directAllowed = PlaceboEvents.blockDropsDirectAllowed();
        boolean hasFallback = PlaceboEvents.blockDropsHasFallbackListeners();
        long mask = 0;
        int kinds = 0;
        for (int i = 0; i < generation.size(); i++) {
            BlockEntry entry = generation.get(i);
            if (!entry.relevant().test(level, pos, state, breaker, tool)) continue;
            mask |= 1L << i;
            kinds |= captureBits(entry.kind());
        }

        if (mask == 0) {
            target.set(hasFallback ? Mode.FALLBACK : Mode.NONE, NO_BLOCK, 0, hasFallback ? 3 : 0);
        }
        else {
            target.set(directAllowed ? Mode.DIRECT : Mode.FALLBACK, generation, mask, directAllowed ? kinds : 3);
        }
    }

    /** Returns true without creating a state/frame when a block call has no owned or fallback observer. */
    public static boolean blockPlanIsNone(net.minecraft.world.level.LevelAccessor level, BlockPos pos,
        BlockState state, Entity breaker, ItemStack tool) {
        if (!(level instanceof ServerLevel server)) return true;
        if (PlaceboEvents.blockDropsHasFallbackListeners()) return false;
        List<BlockEntry> generation = block;
        for (int i = 0; i < generation.size(); i++) {
            if (generation.get(i).relevant().test(server, pos, state, breaker, tool)) return false;
        }
        return true;
    }

    /** Returns the singleton inactive plan for non-server wrappers. */
    public static BlockPlan noneBlockPlan() {
        return NONE_BLOCK;
    }

    /** Dispatches exactly the owned living handlers selected by {@code plan}. */
    public static void dispatchLiving(LivingPlan plan, LivingEntity entity, DamageSource source,
        java.util.Collection<ItemEntity> drops) {
        List<LivingEntry> entries = plan.entries();
        long mask = plan.relevantMask();
        for (int i = 0; i < entries.size(); i++) {
            if ((mask & (1L << i)) != 0) {
                entries.get(i).handler().handle(entity, source, drops);
            }
        }
    }

    /** Dispatches a reusable target; no context or snapshot is allocated. */
    public static void dispatchLiving(LivingPlanTarget plan, LivingEntity entity, DamageSource source,
        java.util.Collection<ItemEntity> drops) {
        List<LivingEntry> entries = plan.entries();
        long mask = plan.relevantMask();
        for (int i = 0; i < entries.size(); i++) {
            if ((mask & (1L << i)) != 0) {
                entries.get(i).handler().handle(entity, source, drops);
            }
        }
    }

    /** Dispatches exactly the owned block handlers selected by {@code plan}. */
    public static int dispatchBlock(BlockPlan plan, ServerLevel level, BlockPos pos, BlockState state,
        Entity breaker, ItemStack tool, List<ItemEntity> drops, int experience) {
        List<BlockEntry> entries = plan.entries();
        long mask = plan.relevantMask();
        for (int i = 0; i < entries.size(); i++) {
            if ((mask & (1L << i)) != 0) {
                experience = entries.get(i).handler().handle(level, pos, state, breaker, tool, drops, experience);
            }
        }
        return experience;
    }

    /** Dispatches a reusable target; no context or snapshot is allocated. */
    public static int dispatchBlock(BlockPlanTarget plan, ServerLevel level, BlockPos pos, BlockState state,
        Entity breaker, ItemStack tool, List<ItemEntity> drops, int experience) {
        List<BlockEntry> entries = plan.entries();
        long mask = plan.relevantMask();
        for (int i = 0; i < entries.size(); i++) {
            if ((mask & (1L << i)) != 0) {
                experience = entries.get(i).handler().handle(level, pos, state, breaker, tool, drops, experience);
            }
        }
        return experience;
    }

    /**
     * Architectury's current EventFactory uses HIGHEST through LOWEST in this exact order. Keep the mapping
     * explicit here so dispatcher ordering does not depend on enum declaration ordinals if Architectury changes
     * its representation later.
     */
    private static int priorityRank(EventPriority priority) {
        return switch (priority) {
            case HIGHEST -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
            case LOWEST -> 4;
        };
    }

    private static int captureBits(CaptureKind kind) {
        return switch (kind) {
            case ITEMS_XP -> 3;
            case ITEMS_ONLY -> 1;
            case XP_ONLY -> 2;
        };
    }
}
