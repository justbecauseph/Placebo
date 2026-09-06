package dev.shadowsoffire.placebo.dynreg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import dev.shadowsoffire.placebo.network.PayloadContext;

/**
 * Sync payload for the resolved tag content of a single {@link DynamicRegistry}.
 * <p>
 * Sent server → client during the {@link net.neoforged.neoforge.event.OnDatapackSyncEvent} flow, after the registry's
 * {@code Content} packets but before the {@code End} packet. The client stages the resolved tag map; the {@code End}
 * payload's existing handler binds the staged tags into the registry alongside the staged content.
 */
public final class TagSyncPayload implements CustomPacketPayload {

    private final Identifier id;
    private final Map<Identifier, List<Identifier>> tags;
    /** Immutable outbound body; null means this is a legacy map payload. */
    private final byte[] rawBody;

    public static final Type<TagSyncPayload> TYPE = new Type<>(Placebo.loc("reload_sync_tags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TagSyncPayload> CODEC = StreamCodec.of(TagSyncPayload::write, TagSyncPayload::read);

    public TagSyncPayload(Identifier id, Map<Identifier, List<Identifier>> tags) {
        this(id, immutableTags(tags), null);
    }

    private TagSyncPayload(Identifier id, byte[] rawBody) {
        this(id, Map.of(), rawBody);
    }

    private TagSyncPayload(Identifier id, Map<Identifier, List<Identifier>> tags, byte[] rawBody) {
        this.id = Objects.requireNonNull(id, "id");
        this.tags = tags;
        this.rawBody = rawBody;
    }

    /** Creates an outbound payload backed by immutable raw tag-body bytes. */
    public static TagSyncPayload raw(Identifier id, byte[] rawBody) {
        return new TagSyncPayload(id, rawBody.clone());
    }

    public Identifier id() {
        return this.id;
    }

    public Map<Identifier, List<Identifier>> tags() {
        return this.tags;
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

    private static void write(RegistryFriendlyByteBuf buf, TagSyncPayload payload) {
        buf.writeIdentifier(payload.id);
        if (payload.rawBody != null) {
            // writeBytes(byte[]) copies and does not consume a source reader index.
            buf.writeBytes(payload.rawBody);
        }
        else {
            writeBody(buf, payload.tags);
        }
    }

    private static TagSyncPayload read(RegistryFriendlyByteBuf buf) {
        Identifier id = buf.readIdentifier();
        int tagCount = ByteBufCodecs.readCount(buf, Integer.MAX_VALUE);
        Map<Identifier, List<Identifier>> tags = new LinkedHashMap<>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            Identifier tagId = buf.readIdentifier();
            int entryCount = ByteBufCodecs.readCount(buf, Integer.MAX_VALUE);
            List<Identifier> entries = new ArrayList<>(entryCount);
            for (int j = 0; j < entryCount; j++) {
                entries.add(buf.readIdentifier());
            }
            tags.put(tagId, entries);
        }
        return new TagSyncPayload(id, tags);
    }

    /** Encodes the exact legacy tag body (everything after the registry id) into immutable bytes. */
    static byte[] encodeBody(Map<Identifier, List<Identifier>> tags, RegistryAccess access) {
        ByteBuf source = Unpooled.buffer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(source, access);
        try {
            writeBody(buf, tags);
            byte[] result = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), result);
            return result;
        }
        finally {
            buf.release();
        }
    }

    private static void writeBody(RegistryFriendlyByteBuf buf, Map<Identifier, List<Identifier>> tags) {
        ByteBufCodecs.writeCount(buf, tags.size(), Integer.MAX_VALUE);
        for (Map.Entry<Identifier, List<Identifier>> entry : tags.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            ByteBufCodecs.writeCount(buf, entry.getValue().size(), Integer.MAX_VALUE);
            for (Identifier id : entry.getValue()) {
                buf.writeIdentifier(id);
            }
        }
    }

    private static Map<Identifier, List<Identifier>> immutableTags(Map<Identifier, List<Identifier>> tags) {
        Objects.requireNonNull(tags, "tags");
        Map<Identifier, List<Identifier>> copy = new LinkedHashMap<>(tags.size());
        tags.forEach((id, entries) -> copy.put(Objects.requireNonNull(id, "tag id"), List.copyOf(entries)));
        return Collections.unmodifiableMap(copy);
    }

    @ApiStatus.Internal
    public static class Provider implements PayloadProvider<TagSyncPayload> {

        @Override
        public Type<TagSyncPayload> getType() {
            return TYPE;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TagSyncPayload> getCodec() {
            return CODEC;
        }

        @Override
        public void handleClient(TagSyncPayload msg, PayloadContext ctx) {
            SyncManagement.acceptTags(msg.id, msg.tags);
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TagSyncPayload other)) return false;
        return this.id.equals(other.id) && this.tags.equals(other.tags)
            && java.util.Arrays.equals(this.rawBody, other.rawBody);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(this.id, this.tags) + java.util.Arrays.hashCode(this.rawBody);
    }

    @Override
    public String toString() {
        return this.rawBody == null
            ? "TagSyncPayload[id=" + this.id + ", tags=" + this.tags + "]"
            : "TagSyncPayload[id=" + this.id + ", rawBodyLength=" + this.rawBody.length + "]";
    }
}
