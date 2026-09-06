package dev.shadowsoffire.placebo.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.MapCodec;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

final class FabricLootModifiersTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void inactiveChainLeavesOriginalListAndIdentityUntouched() {
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of(stone());
        List<ItemStack> before = List.copyOf(drops);

        FabricLootModifiers.applyForTesting(List.of(), false, null, drops);

        assertSame(drops, drops);
        assertEquals(before, drops);
    }

    @Test
    void allInPlaceChainReceivesExactFabricListAndEvaluatesConditionsOnce() {
        int[] evaluations = {0, 0};
        List<ObjectArrayList<ItemStack>> seen = new ArrayList<>();
        RecordingModifier first = recording(true, evaluations, 0, seen, loot -> {
            loot.add(dirt());
            return loot;
        });
        RecordingModifier second = recording(true, evaluations, 1, seen, loot -> {
            loot.add(stone());
            return loot;
        });
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of();

        FabricLootModifiers.applyForTesting(List.of(first, second), true, null, drops);

        assertSame(drops, seen.get(0));
        assertSame(drops, seen.get(1));
        assertEquals(1, evaluations[0]);
        assertEquals(1, evaluations[1]);
        assertItemIds(List.of("minecraft:dirt", "minecraft:stone"), drops);
    }

    @Test
    void defaultModifierForcesCompatibilityCopyForTheWholeChain() {
        List<ObjectArrayList<ItemStack>> seen = new ArrayList<>();
        RecordingModifier first = recording(true, null, -1, seen, loot -> {
            loot.add(stone());
            return loot;
        });
        RecordingModifier second = recording(false, null, -1, seen, loot -> {
            loot.add(dirt());
            return loot;
        });
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of();

        FabricLootModifiers.applyForTesting(List.of(first, second), false, null, drops);

        assertEquals(2, seen.size());
        assertTrue(seen.get(0) != drops);
        assertSame(seen.get(0), seen.get(1));
        assertItemIds(List.of("minecraft:stone", "minecraft:dirt"), drops);
    }

    @Test
    void replacementModifierReturningNewListWinsExactFinalContents() {
        RecordingModifier replacement = new RecordingModifier(false, null, -1, loot -> ObjectArrayList.of(dirt(), stone()));
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of(stone());

        FabricLootModifiers.applyForTesting(List.of(replacement), false, null, drops);

        assertItemIds(List.of("minecraft:dirt", "minecraft:stone"), drops);
    }

    @Test
    void mixedPriorityOrderAndConditionsRemainExact() {
        int[] evaluations = {0, 0};
        List<String> order = new ArrayList<>();
        RecordingModifier low = recording(false, evaluations, 0, null, loot -> {
            order.add("low");
            loot.add(dirt());
            return loot;
        });
        RecordingModifier high = recording(false, evaluations, 1, null, loot -> {
            order.add("high");
            loot.add(stone());
            return loot;
        });
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of();

        // applyForTesting receives the already sorted active generation, just as the reload publication does.
        FabricLootModifiers.applyForTesting(List.of(low, high), false, null, drops);

        assertEquals(List.of("low", "high"), order);
        assertEquals(1, evaluations[0]);
        assertEquals(1, evaluations[1]);
        assertItemIds(List.of("minecraft:dirt", "minecraft:stone"), drops);
    }

    @Test
    void lyingOptInReplacementReconcilesIntoOriginalBeforeNextModifier() {
        List<ObjectArrayList<ItemStack>> seen = new ArrayList<>();
        RecordingModifier liar = new RecordingModifier(true, null, -1, loot -> ObjectArrayList.of(dirt()));
        RecordingModifier next = recording(true, null, -1, seen, loot -> {
            assertItemIds(List.of("minecraft:dirt"), loot);
            loot.add(stone());
            return loot;
        });
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of(stone());

        FabricLootModifiers.applyForTesting(List.of(liar, next), true, null, drops);

        assertSame(drops, seen.get(0));
        assertItemIds(List.of("minecraft:dirt", "minecraft:stone"), drops);
    }

    @Test
    void nonObjectArrayListInputUsesCompatibilityFallback() {
        List<ObjectArrayList<ItemStack>> seen = new ArrayList<>();
        RecordingModifier modifier = recording(true, null, -1, seen, loot -> {
            loot.add(dirt());
            return loot;
        });
        List<ItemStack> drops = new ArrayList<>(List.of(stone()));

        FabricLootModifiers.applyForTesting(List.of(modifier), true, null, drops);

        assertFalse(seen.get(0) == drops);
        assertItemIds(List.of("minecraft:stone", "minecraft:dirt"), drops);
    }

    @Test
    void nullReturnThrowsWithoutClaimingRollback() {
        ItemStack marker = stone();
        RecordingModifier nullResult = new RecordingModifier(true, null, -1, loot -> {
            loot.add(marker);
            return null;
        });
        ObjectArrayList<ItemStack> drops = ObjectArrayList.of();

        assertThrows(NullPointerException.class,
            () -> FabricLootModifiers.applyForTesting(List.of(nullResult), true, null, drops));
        assertEquals(1, drops.size(), "the bridge does not falsely claim rollback after a null return");
        assertSame(marker, drops.get(0), "the bridge does not falsely claim rollback after a null return");
    }

    private static RecordingModifier recording(boolean inPlace, int[] evaluations, int evaluationIndex,
        List<ObjectArrayList<ItemStack>> seen, Function<ObjectArrayList<ItemStack>, ObjectArrayList<ItemStack>> action) {
        LootItemCondition condition = evaluations == null ? null : new CountingCondition(evaluations, evaluationIndex);
        return new RecordingModifier(inPlace, condition, seen, action);
    }

    private static ItemStack stone() {
        return stack("stone");
    }

    private static ItemStack dirt() {
        return stack("dirt");
    }

    private static void assertItemIds(List<String> expected, List<ItemStack> actual) {
        assertEquals(expected, actual.stream()
            .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .toList());
    }

    private static ItemStack stack(String path) {
        Holder.Reference<Item> item = BuiltInRegistries.ITEM
            .get(Identifier.fromNamespaceAndPath("minecraft", path)).orElseThrow();
        if (!item.areComponentsBound()) {
            item.bindComponents(DataComponentMap.EMPTY);
        }
        return new ItemStack(item);
    }

    private static final class CountingCondition implements LootItemCondition {

        private final int[] evaluations;
        private final int index;

        private CountingCondition(int[] evaluations, int index) {
            this.evaluations = evaluations;
            this.index = index;
        }

        @Override
        public boolean test(LootContext context) {
            this.evaluations[this.index]++;
            return true;
        }

        @Override
        public MapCodec<? extends LootItemCondition> codec() {
            return null;
        }
    }

    private static final class RecordingModifier extends LootModifier {

        private final boolean inPlace;
        private final List<ObjectArrayList<ItemStack>> seen;
        private final Function<ObjectArrayList<ItemStack>, ObjectArrayList<ItemStack>> action;

        private RecordingModifier(boolean inPlace, LootItemCondition condition, List<ObjectArrayList<ItemStack>> seen,
            Function<ObjectArrayList<ItemStack>, ObjectArrayList<ItemStack>> action) {
            super(condition == null ? new LootItemCondition[0] : new LootItemCondition[] {condition}, DEFAULT_PRIORITY);
            this.inPlace = inPlace;
            this.seen = seen;
            this.action = action;
        }

        private RecordingModifier(boolean inPlace, int[] evaluations, int evaluationIndex,
            Function<ObjectArrayList<ItemStack>, ObjectArrayList<ItemStack>> action) {
            this(inPlace, evaluations == null ? null : new CountingCondition(evaluations, evaluationIndex), null, action);
        }

        @Override
        public boolean inPlaceOnly() {
            return this.inPlace;
        }

        @Override
        public MapCodec<? extends LootModifier> codec() {
            return null;
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            if (this.seen != null) this.seen.add(generatedLoot);
            return this.action == null ? generatedLoot : this.action.apply(generatedLoot);
        }
    }
}
