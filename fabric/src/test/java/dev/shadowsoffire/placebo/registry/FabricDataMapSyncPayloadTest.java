package dev.shadowsoffire.placebo.registry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;

import dev.shadowsoffire.placebo.datamap.DataMapSpec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import dev.shadowsoffire.placebo.registry.FabricDataMaps.SyncPayload;

final class FabricDataMapSyncPayloadTest {

    @Test
    void rawBodyMatchesLegacyEncodingAndDecodesTheSame() {
        Identifier mapId = Identifier.fromNamespaceAndPath("placebo_test", "map");
        Map<Identifier, JsonElement> values = new LinkedHashMap<>();
        values.put(Identifier.fromNamespaceAndPath("placebo_test", "first"), JsonParser.parseString("{\"weight\":3}"));
        values.put(Identifier.fromNamespaceAndPath("placebo_test", "second"), JsonParser.parseString("[1,2,3]"));

        byte[] legacyBytes = encode(SyncPayload.CODEC, new SyncPayload(mapId, true, values));
        byte[] rawBytes = encode(SyncPayload.CODEC, SyncPayload.raw(mapId, true, SyncPayload.encodeBody(values)));

        assertArrayEquals(legacyBytes, rawBytes);
        SyncPayload decoded = decode(SyncPayload.CODEC, rawBytes);
        assertEquals(true, decoded.mandatory());
        assertEquals(values, decoded.values());
        assertEquals(List.copyOf(values.keySet()), List.copyOf(decoded.values().keySet()));
    }

    @Test
    void dataMapCacheEncodesOncePerAccessAndAgainAfterPublication() {
        AtomicInteger encodeCount = new AtomicInteger();
        Codec<Integer> networkCodec = Codec.INT.xmap(
            value -> value,
            value -> {
                encodeCount.incrementAndGet();
                return value;
            });
        ResourceKey<Registry<String>> registryKey = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath("placebo_test", "data_map_registry"));
        DataMapSpec<String, Integer> spec = new DataMapSpec<>(registryKey, Codec.INT).synced(networkCodec, true);
        FabricDataMaps.Loaded<String, Integer> map = new FabricDataMaps.Loaded<>(
            Identifier.fromNamespaceAndPath("placebo_test", "cached_map"), spec);
        Map<Identifier, Integer> values = new LinkedHashMap<>();
        values.put(Identifier.fromNamespaceAndPath("placebo_test", "one"), 1);
        values.put(Identifier.fromNamespaceAndPath("placebo_test", "two"), 2);
        map.publishForTesting(values);

        RegistryAccess firstAccess = RegistryAccess.EMPTY;
        SyncPayload first = map.encodedSyncForTesting(firstAccess);
        SyncPayload second = map.encodedSyncForTesting(firstAccess);
        assertSame(first, second);
        assertEquals(2, encodeCount.get(), "two sends using one access and generation encode each value once");

        RegistryAccess secondAccess = distinctAccess();
        SyncPayload otherAccess = map.encodedSyncForTesting(secondAccess);
        assertNotSame(first, otherAccess);
        assertEquals(4, encodeCount.get(), "a distinct registry access gets its own encoded body");

        map.publishForTesting(Map.of(Identifier.fromNamespaceAndPath("placebo_test", "replacement"), 3));
        SyncPayload replacement = map.encodedSyncForTesting(firstAccess);
        assertNotSame(first, replacement);
        assertEquals(5, encodeCount.get(), "publishing a new values generation invalidates the body cache");
    }

    private static RegistryAccess distinctAccess() {
        return new RegistryAccess() {

            @Override
            public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
                return RegistryAccess.EMPTY.lookup(key);
            }

            @Override
            public Stream<RegistryAccess.RegistryEntry<?>> registries() {
                return RegistryAccess.EMPTY.registries();
            }
        };
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
}
