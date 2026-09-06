package dev.shadowsoffire.placebo.dynreg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

final class SyncPayloadEncodingTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void contentRawBodyMatchesLegacyEncodingAndDecodesTheSame() {
        TestRegistry registry = new TestRegistry();
        SyncManagement.registerForSync(registry);
        Identifier entry = Identifier.fromNamespaceAndPath("placebo_test", "entry");
        DynRegPayloads.Content<Integer> legacy = new DynRegPayloads.Content<>(registry.getId(), entry, 37);

        byte[] legacyBytes = encode(DynRegPayloads.Content.CODEC, legacy);
        byte[] itemBytes = encodeItem(registry, 37);
        byte[] rawBytes = encode(DynRegPayloads.Content.CODEC,
            DynRegPayloads.Content.raw(registry.getId(), entry, itemBytes));

        assertArrayEquals(legacyBytes, rawBytes);
        DynRegPayloads.Content<?> decoded = decode(DynRegPayloads.Content.CODEC, rawBytes);
        assertArrayEquals(itemBytes, decoded.rawBody());
        assertEquals(37, decodeItem(registry, decoded.rawBody()));
    }

    @Test
    void contentByteBufConstructorDoesNotConsumeSourceReaderIndex() {
        TestRegistry registry = new TestRegistry();
        SyncManagement.registerForSync(registry);
        Identifier entry = Identifier.fromNamespaceAndPath("placebo_test", "entry");
        ByteBuf source = Unpooled.buffer();
        source.writeByte(11);
        source.writeByte(22);
        source.readerIndex(1);
        int readerIndex = source.readerIndex();
        try {
            DynRegPayloads.Content<Integer> content = new DynRegPayloads.Content<>(registry.getId(), entry, source);
            assertEquals(readerIndex, source.readerIndex());
            assertArrayEquals(encode(DynRegPayloads.Content.CODEC, content), encode(DynRegPayloads.Content.CODEC,
                DynRegPayloads.Content.raw(registry.getId(), entry, new byte[] {22})));
        }
        finally {
            source.release();
        }
    }

    @Test
    void tagRawBodyMatchesLegacyEncodingAndPreservesOrderOnDecode() {
        Identifier registryId = Identifier.fromNamespaceAndPath("placebo_test", "tags_" + IDS.incrementAndGet());
        Identifier firstTag = Identifier.fromNamespaceAndPath("placebo_test", "first");
        Identifier secondTag = Identifier.fromNamespaceAndPath("placebo_test", "second");
        Identifier firstEntry = Identifier.fromNamespaceAndPath("placebo_test", "one");
        Identifier secondEntry = Identifier.fromNamespaceAndPath("placebo_test", "two");
        Map<Identifier, List<Identifier>> tags = new LinkedHashMap<>();
        tags.put(firstTag, List.of(firstEntry, secondEntry));
        tags.put(secondTag, List.of(secondEntry));

        byte[] legacyBytes = encode(TagSyncPayload.CODEC, new TagSyncPayload(registryId, tags));
        byte[] rawBytes = encode(TagSyncPayload.CODEC,
            TagSyncPayload.raw(registryId, TagSyncPayload.encodeBody(tags, RegistryAccess.EMPTY)));

        assertArrayEquals(legacyBytes, rawBytes);
        TagSyncPayload decoded = decode(TagSyncPayload.CODEC, rawBytes);
        assertEquals(List.copyOf(tags.keySet()), List.copyOf(decoded.tags().keySet()));
        assertEquals(tags, decoded.tags());
    }

    private static <T> byte[] encode(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buf, value);
            byte[] result = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), result);
            return result;
        }
        finally {
            buf.release();
        }
    }

    private static <T> T decode(StreamCodec<RegistryFriendlyByteBuf, T> codec, byte[] bytes) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
        try {
            return codec.decode(buf);
        }
        finally {
            buf.release();
        }
    }

    private static byte[] encodeItem(TestRegistry registry, int value) {
        return encode(registry.serializer.streamCodec(), value);
    }

    private static int decodeItem(TestRegistry registry, byte[] bytes) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
        try {
            return registry.serializer.streamCodec().decode(buf);
        }
        finally {
            buf.release();
        }
    }

    private static final class TestRegistry extends DynamicRegistry<Integer> {

        private TestRegistry() {
            super(LoggerFactory.getLogger("SyncPayloadEncodingTest"),
                Identifier.fromNamespaceAndPath("placebo_test", "sync_" + IDS.incrementAndGet()),
                RegistrySerializer.synced(Codec.INT));
        }
    }
}
