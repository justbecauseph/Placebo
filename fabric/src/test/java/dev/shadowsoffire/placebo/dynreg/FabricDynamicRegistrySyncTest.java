package dev.shadowsoffire.placebo.dynreg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class FabricDynamicRegistrySyncTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void generationPreservesSequenceAndCachesCodecBytesByRevision() {
        CountingRegistry registry = new CountingRegistry();
        registry.reload(3, 7);
        registry.bindTags(Map.of(
            Identifier.fromNamespaceAndPath("placebo_test", "all"),
            List.of(Identifier.fromNamespaceAndPath("placebo_test", "entry_0"),
                Identifier.fromNamespaceAndPath("placebo_test", "entry_1"))));
        FabricDynReg.clearSyncCaches();

        FabricDynReg.SyncGeneration first = FabricDynReg.generationForTesting(registry, RegistryAccess.EMPTY);
        assertEquals(2, registry.codec().encodes());
        assertEquals(List.of("entry_0", "entry_1"), first.content().stream().map(p -> p.key().getPath()).toList());
        assertSame(DynRegPayloads.Start.TYPE, first.start().type());
        assertSame(DynRegPayloads.Content.TYPE, first.content().get(0).type());
        assertSame(TagSyncPayload.TYPE, first.tags().type());
        assertSame(DynRegPayloads.End.TYPE, first.end().type());

        FabricDynReg.SyncGeneration second = FabricDynReg.generationForTesting(registry, RegistryAccess.EMPTY);
        assertSame(first, second);
        assertEquals(2, registry.codec().encodes(), "two sends in one generation must encode each item once");

        AtomicReference<FabricDynReg.SyncGeneration> callbackGeneration = new AtomicReference<>();
        registry.addCallback(RegistryCallback.reloadOnly(ignored -> callbackGeneration.set(
            FabricDynReg.generationForTesting(registry, RegistryAccess.EMPTY))));

        registry.reload(11, 13);
        FabricDynReg.SyncGeneration fromCallback = callbackGeneration.get();
        assertNotSame(first, fromCallback);
        assertEquals(List.of("entry_0", "entry_1"),
            fromCallback.content().stream().map(p -> p.key().getPath()).toList());
        assertArrayEquals(new byte[] {11}, fromCallback.content().get(0).rawBody(),
            "reload callback must observe the newly encoded first entry data");
        assertArrayEquals(new byte[] {13}, fromCallback.content().get(1).rawBody(),
            "reload callback must observe the newly encoded second entry data");
        assertEquals(first.revision() + 1, fromCallback.revision());

        FabricDynReg.SyncGeneration afterReload = FabricDynReg.generationForTesting(registry, RegistryAccess.EMPTY);
        assertSame(fromCallback, afterReload);
        assertEquals(4, registry.codec().encodes(), "callback and post-reload sends must share one encoded generation");

        registry.bindTags(Map.of(
            Identifier.fromNamespaceAndPath("placebo_test", "all"),
            List.of(Identifier.fromNamespaceAndPath("placebo_test", "entry_1"))));
        FabricDynReg.SyncGeneration tagged = FabricDynReg.generationForTesting(registry, RegistryAccess.EMPTY);
        assertNotSame(afterReload, tagged);
        assertEquals(6, registry.codec().encodes(), "a new tag-bound generation must re-encode content");
        assertEquals(afterReload.revision() + 1, tagged.revision());
        FabricDynReg.clearSyncCaches();
    }

    private static final class CountingRegistry extends DynamicRegistry<Integer> {

        private final CountingCodec codec;

        private CountingRegistry() {
            this(new CountingCodec());
        }

        private CountingRegistry(CountingCodec codec) {
            super(LoggerFactory.getLogger("FabricDynamicRegistrySyncTest"),
                Identifier.fromNamespaceAndPath("placebo_test", "dynamic_sync_" + IDS.incrementAndGet()),
                RegistrySerializer.synced(Codec.INT, codec));
            this.codec = codec;
        }

        private void reload(int... values) {
            this.beginReload(ReloadType.SERVER);
            for (int i = 0; i < values.length; i++) {
                this.register(Identifier.fromNamespaceAndPath("placebo_test", "entry_" + i), values[i]);
            }
            this.onReload(ReloadType.SERVER);
        }

        private CountingCodec codec() {
            return this.codec;
        }
    }

    private static final class CountingCodec implements StreamCodec<RegistryFriendlyByteBuf, Integer> {

        private final AtomicInteger encodes = new AtomicInteger();

        @Override
        public Integer decode(RegistryFriendlyByteBuf buf) {
            return buf.readVarInt();
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Integer value) {
            this.encodes.incrementAndGet();
            buf.writeVarInt(value);
        }

        private int encodes() {
            return this.encodes.get();
        }
    }

    private static final AtomicInteger IDS = new AtomicInteger();
}
