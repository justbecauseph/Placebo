package dev.shadowsoffire.placebo.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.MapCodec;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

final class LootModifierContractTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultModifiersRemainReplacementCapable() {
        TestModifier modifier = new TestModifier(false, loot -> ObjectArrayList.of());
        assertFalse(modifier.inPlaceOnly());

        ObjectArrayList<ItemStack> input = ObjectArrayList.of();
        ObjectArrayList<ItemStack> result = modifier.apply(input, null);
        assertEquals(0, result.size());
        assertTrue(result != input, "the default contract must not force identity preservation");
    }

    @Test
    void optedInModifiersPreserveTheSuppliedListIdentity() {
        TestModifier modifier = new TestModifier(true, loot -> {
            loot.add(ItemStack.EMPTY);
            return loot;
        });
        ObjectArrayList<ItemStack> input = ObjectArrayList.of();

        assertTrue(modifier.inPlaceOnly());
        assertSame(input, modifier.apply(input, null));
        assertEquals(List.of(ItemStack.EMPTY), input);
    }

    @Test
    void conditionsAreEvaluatedOncePerApply() {
        int[] evaluations = {0};
        LootItemCondition condition = new LootItemCondition() {
            @Override
            public boolean test(LootContext context) {
                evaluations[0]++;
                return true;
            }

            @Override
            public MapCodec<? extends LootItemCondition> codec() {
                return null;
            }
        };
        TestModifier modifier = new TestModifier(false, loot -> loot, condition);

        modifier.apply(ObjectArrayList.of(), null);
        assertEquals(1, evaluations[0]);
    }

    @FunctionalInterface
    private interface Application {
        ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot);
    }

    private static final class TestModifier extends LootModifier {

        private final boolean inPlace;
        private final Application application;

        private TestModifier(boolean inPlace, Application application) {
            this(inPlace, application, new LootItemCondition[0]);
        }

        private TestModifier(boolean inPlace, Application application, LootItemCondition... conditions) {
            super(conditions, DEFAULT_PRIORITY);
            this.inPlace = inPlace;
            this.application = application;
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
            return this.application.apply(generatedLoot);
        }
    }
}
