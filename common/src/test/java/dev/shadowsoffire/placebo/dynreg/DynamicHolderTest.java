package dev.shadowsoffire.placebo.dynreg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;

final class DynamicHolderTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void unboundHolderReturnsEmptyAndReportsTheTargetId() {
        TestRegistry registry = new TestRegistry();
        Identifier id = id("missing");
        DynamicHolder<Integer> holder = registry.holder(id);

        assertFalse(holder.isBound());
        assertNull(holder.getOrNull());
        assertEquals(Optional.empty(), holder.getOptional());
        NullPointerException error = assertThrows(NullPointerException.class, holder::get);
        assertEquals("Trying to access unbound value: " + id, error.getMessage());
    }

    @Test
    void holderBindsAfterReloadAndRebindsToTheReplacementValue() {
        TestRegistry registry = new TestRegistry();
        Identifier id = id("value");
        DynamicHolder<Integer> holder = registry.holder(id);

        registry.reload(id, 11);
        assertTrue(holder.isBound());
        assertEquals(11, holder.get());
        assertEquals(11, holder.getOrNull());
        assertEquals(Optional.of(11), holder.getOptional());

        registry.reload(id, 13);
        assertSame(holder, registry.holder(id), "registry holders remain interned across reloads");
        assertTrue(holder.isBound());
        assertEquals(13, holder.get());
        assertEquals(13, holder.getOrNull());

        registry.reloadEmpty();
        assertFalse(holder.isBound(), "a reload that removes an entry must invalidate its existing holder");
        assertNull(holder.getOrNull());
        NullPointerException error = assertThrows(NullPointerException.class, holder::get);
        assertEquals("Trying to access unbound value: " + id, error.getMessage());
    }

    @Test
    void equalHoldersShareIdentityContractAndHashCode() {
        TestRegistry registry = new TestRegistry();
        Identifier id = id("equal");
        DynamicHolder<Integer> interned = registry.holder(id);
        DynamicHolder<Integer> equivalent = new DynamicHolder<>(registry, id);

        assertEquals(interned, equivalent);
        assertEquals(interned.hashCode(), equivalent.hashCode());
        assertEquals(Objects.hash(id, registry), interned.hashCode());

        TestRegistry otherRegistry = new TestRegistry();
        assertNotEquals(interned, new DynamicHolder<>(otherRegistry, id));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("placebo_test", path + "_" + IDS.incrementAndGet());
    }

    private static final class TestRegistry extends DynamicRegistry<Integer> {

        private TestRegistry() {
            super(LoggerFactory.getLogger("DynamicHolderTest"),
                Identifier.fromNamespaceAndPath("placebo_test", "holder_" + IDS.incrementAndGet()),
                RegistrySerializer.simple(Codec.INT));
        }

        private void reload(Identifier id, int value) {
            this.beginReload(ReloadType.SERVER);
            this.register(id, value);
            this.onReload(ReloadType.SERVER);
        }

        private void reloadEmpty() {
            this.beginReload(ReloadType.SERVER);
            this.onReload(ReloadType.SERVER);
        }
    }
}
