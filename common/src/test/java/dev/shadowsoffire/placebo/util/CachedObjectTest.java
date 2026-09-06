package dev.shadowsoffire.placebo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

final class CachedObjectTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void componentHasherMatchesTheHistoricalFilteredArrayHashInOrder() {
        ItemStack stack = testStack();
        var ordered = new DataComponentType<?>[] {
            DataComponents.CUSTOM_NAME,
            DataComponents.DAMAGE,
            DataComponents.GLIDER
        };
        ToIntFunction<ItemStack> actual = CachedObject.hashComponents(ordered);

        assertEquals(historicalHash(stack, ordered), actual.applyAsInt(stack), "null component values changed the hash");

        stack.set(DataComponents.CUSTOM_NAME, Component.literal("phase4a"));
        stack.set(DataComponents.DAMAGE, 7);
        assertEquals(historicalHash(stack, ordered), actual.applyAsInt(stack), "populated component values changed the hash");

        var reversed = new DataComponentType<?>[] {
            DataComponents.GLIDER,
            DataComponents.DAMAGE,
            DataComponents.CUSTOM_NAME
        };
        assertEquals(historicalHash(stack, reversed), CachedObject.hashComponents(reversed).applyAsInt(stack),
            "component order changed the hash contract");
        assertNotEquals(actual.applyAsInt(stack), CachedObject.hashComponents(reversed).applyAsInt(stack),
            "the order fixture did not exercise a distinct hash");
    }

    @Test
    void cachedObjectRecomputesAfterAHashedComponentMutates() {
        ItemStack stack = testStack();
        ToIntFunction<ItemStack> hasher = CachedObject.hashComponents(DataComponents.CUSTOM_NAME, DataComponents.DAMAGE);
        AtomicInteger computations = new AtomicInteger();
        CachedObject<Integer> cached = new CachedObject<>(Identifier.fromNamespaceAndPath("placebo", "phase4a"),
            ignored -> computations.incrementAndGet(), hasher);

        assertEquals(1, cached.get(stack));
        assertEquals(1, computations.get());
        assertEquals(1, cached.get(stack));
        assertEquals(1, computations.get(), "an unchanged stack missed the cached value");

        stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertEquals(2, cached.get(stack));
        assertEquals(2, computations.get(), "a component mutation did not invalidate the cache");

        stack.remove(DataComponents.CUSTOM_NAME);
        assertEquals(3, cached.get(stack));
        assertEquals(3, computations.get(), "removing a component did not invalidate the cache");
    }

    private static int historicalHash(ItemStack stack, DataComponentType<?>[] types) {
        Object[] values = Arrays.stream(types).map(stack::get).filter(Objects::nonNull).toArray();
        return Arrays.hashCode(values);
    }

    private static ItemStack testStack() {
        Holder.Reference<net.minecraft.world.item.Item> stone = BuiltInRegistries.ITEM
            .get(Identifier.fromNamespaceAndPath("minecraft", "stone")).orElseThrow();
        if (!stone.areComponentsBound()) {
            stone.bindComponents(DataComponentMap.EMPTY);
        }
        return new ItemStack(stone);
    }
}
