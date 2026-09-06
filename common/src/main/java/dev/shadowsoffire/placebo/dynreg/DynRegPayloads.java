package dev.shadowsoffire.placebo.dynreg;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.datafixers.util.Either;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.shadowsoffire.placebo.network.PayloadContext;

@ApiStatus.Internal
public class DynRegPayloads {

    public static record Start(Identifier id) implements CustomPacketPayload {

        public static final Type<Start> TYPE = new Type<>(Placebo.loc("reload_sync_start"));

        public static final StreamCodec<FriendlyByteBuf, Start> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, Start::id,
            Start::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static class Provider implements PayloadProvider<Start> {

            @Override
            public Type<Start> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, Start> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(Start msg, PayloadContext ctx) {
                SyncManagement.initSync(msg.id);
            }

            @Override
            public List<ConnectionProtocol> getSupportedProtocols() {
                return List.of(ConnectionProtocol.PLAY);
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }

            @Override
            public String getVersion() {
                return "2";
            }
        }
    }

    public static final class Content<V> implements CustomPacketPayload {

        private final Identifier id;
        private final Identifier key;
        /** Retained for source compatibility with the old record constructor/accessor. */
        private final Either<V, ByteBuf> item;
        /** Immutable outbound body. Null means this is a legacy object payload. */
        private final byte[] rawBody;

        public static final Type<Content<?>> TYPE = new Type<>(Placebo.loc("reload_sync_content"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Content<?>> CODEC = StreamCodec.of(Content::write, Content::read);

        public Content(Identifier id, Identifier key, V item) {
            this(id, key, Either.left(Objects.requireNonNull(item, "item")), null);
        }

        public Content(Identifier id, Identifier key, ByteBuf buf) {
            this(id, key, null, copyReadable(buf));
        }

        /**
         * Preserves the canonical record constructor used by callers that already hold an Either.
         * ByteBuf values are copied without advancing the source reader index.
         */
        public Content(Identifier id, Identifier key, Either<V, ByteBuf> item) {
            this(id, key, item != null && item.right().isPresent() ? null : Objects.requireNonNull(item, "item"),
                item != null && item.right().isPresent() ? copyReadable(item.right().orElseThrow()) : null);
        }

        private Content(Identifier id, Identifier key, Either<V, ByteBuf> item, byte[] rawBody) {
            this.id = Objects.requireNonNull(id, "id");
            this.key = Objects.requireNonNull(key, "key");
            this.item = item;
            this.rawBody = rawBody;
        }

        /** Creates an outbound payload backed by immutable raw codec bytes. */
        public static <V> Content<V> raw(Identifier id, Identifier key, byte[] rawBody) {
            return new Content<>(id, key, null, rawBody.clone());
        }

        /** Returns the registry id. */
        public Identifier id() {
            return this.id;
        }

        /** Returns the entry id. */
        public Identifier key() {
            return this.key;
        }

        /**
         * Returns the legacy object-or-buffer view. Raw payloads expose a defensive buffer wrapper so callers cannot
         * mutate the cached byte array.
         */
        public Either<V, ByteBuf> item() {
            return this.rawBody == null ? this.item : Either.right(Unpooled.wrappedBuffer(this.rawBody.clone()));
        }

        /** Defensive copy for diagnostics/tests; the network writer uses the private immutable array directly. */
        public byte[] rawBody() {
            return this.rawBody == null ? null : this.rawBody.clone();
        }

        boolean hasRawBody() {
            return this.rawBody != null;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static <V> void write(RegistryFriendlyByteBuf buf, Content<V> payload) {
            buf.writeIdentifier(payload.id);
            buf.writeIdentifier(payload.key);
            if (payload.rawBody != null) {
                // writeBytes(byte[]) copies and does not consume a source reader index.
                buf.writeBytes(payload.rawBody);
            }
            else {
                SyncManagement.writeItem(payload.id, payload.item.left().orElseThrow(), buf);
            }
        }

        /**
         * Reads a content payload. We defer deserialization of the underlying object, since it may depend on the state of
         * other registries that are being setup on the main thread.
         */
        public static <V> Content<V> read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier key = buf.readIdentifier();

            byte[] item = new byte[buf.readableBytes()];
            buf.readBytes(item);
            return new Content<>(id, key, null, item);
        }

        public static class Provider<V> implements PayloadProvider<Content<?>> {

            @Override
            public Type<Content<?>> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, Content<?>> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(Content<?> msg, PayloadContext ctx) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                    Unpooled.wrappedBuffer(msg.rawBody), ctx.player().registryAccess());

                try {
                    V value = SyncManagement.readItem(msg.id, buf);
                    SyncManagement.acceptItem(msg.id, msg.key, value);
                }
                catch (Exception ex) {
                    Placebo.LOGGER.error("Failure when deserializing a dynamic registry object via network: Registry: {}, Object ID: {}", msg.id, msg.key);
                    throw ex;
                }
                finally {
                    buf.release();
                }
            }

            @Override
            public List<ConnectionProtocol> getSupportedProtocols() {
                return List.of(ConnectionProtocol.PLAY);
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }

            @Override
            public String getVersion() {
                return "2";
            }
        }

        private static byte[] copyReadable(ByteBuf buf) {
            Objects.requireNonNull(buf, "buf");
            byte[] copy = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), copy);
            return copy;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Content<?> other)) return false;
            return this.id.equals(other.id) && this.key.equals(other.key)
                && Objects.equals(this.item, other.item) && java.util.Arrays.equals(this.rawBody, other.rawBody);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(this.id, this.key, this.item);
            return 31 * result + java.util.Arrays.hashCode(this.rawBody);
        }

        @Override
        public String toString() {
            return this.rawBody == null
                ? "Content[id=" + this.id + ", key=" + this.key + ", item=" + this.item + "]"
                : "Content[id=" + this.id + ", key=" + this.key + ", rawBodyLength=" + this.rawBody.length + "]";
        }
    }

    public static record End(Identifier id) implements CustomPacketPayload {

        public static final Type<End> TYPE = new Type<>(Placebo.loc("reload_sync_end"));

        public static final StreamCodec<FriendlyByteBuf, End> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, End::id,
            End::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static class Provider implements PayloadProvider<End> {

            @Override
            public Type<End> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, End> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(End msg, PayloadContext ctx) {
                SyncManagement.endSync(msg.id);
            }

            @Override
            public List<ConnectionProtocol> getSupportedProtocols() {
                return List.of(ConnectionProtocol.PLAY);
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }

            @Override
            public String getVersion() {
                return "2";
            }
        }
    }
}
