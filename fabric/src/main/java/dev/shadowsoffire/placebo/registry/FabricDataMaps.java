package dev.shadowsoffire.placebo.registry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.datamap.DataMap;
import dev.shadowsoffire.placebo.datamap.DataMapSpec;
import dev.shadowsoffire.placebo.network.PayloadContext;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import dev.shadowsoffire.placebo.network.PayloadSender;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import dev.architectury.registry.ReloadListenerRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * {@link DeferredHelper.DataMapFactory} for Fabric, which has no data maps at all -- so unlike most of this
 * stack's platform halves, this one is an implementation rather than an adapter.
 * <p>
 * It reads NeoForge's file layout and format deliberately:
 * {@code data/<namespace>/data_maps/<registry>/<name>.json} holding {@code {"replace": ?, "values": {...},
 * "remove": [...]}}. The alternative is a second copy of the same three files in a Placebo-shaped format,
 * which is more work and one more thing to keep in step.
 * <p>
 * <b>What is implemented:</b> loading, replace, and the list form of remove. NeoForge's advanced data maps
 * -- the object form of {@code remove} that runs a custom remover, and per-entry merge behaviour -- are
 * not, because nothing in this stack declares one. They fail loudly rather than being ignored.
 */
public class FabricDataMaps implements DeferredHelper.DataMapFactory {

    private static final int MAX_SYNCED_VALUES = 1 << 20;
    private static final int MAX_SYNCED_VALUE_JSON = 1 << 20;

    /** Every map declared so far, so the reload listener can fill them. */
    private static final List<Loaded<?, ?>> DECLARED = new ArrayList<>();
    private static MinecraftServer server;

    @Override
    public <K, V> DataMap<K, V> create(DeferredHelper owner, Identifier id, DataMapSpec<K, V> spec) {
        Loaded<K, V> map = new Loaded<>(id, spec);
        DECLARED.add(map);
        return map;
    }

    /**
     * Hooks the loader into datapack reloads. Called once from the entrypoint.
     * <p>
     * The dynamic registries declare a dependency on this listener, so anything deserialized there sees
     * loaded values rather than an empty map -- see {@code FabricDynReg#registerReloadListener}.
     */
    public static void registerReloadListener() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            clearSyncCaches();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> syncTo(handler.player));

        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SimplePreparableReloadListener<Void>() {

            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                for (Loaded<?, ?> map : DECLARED) {
                    map.reload(manager);
                }
                if (server != null) {
                    PlayerLookup.all(server).forEach(FabricDataMaps::syncTo);
                }
            }
        }, ID);
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Placebo.MODID, "data_maps");

    private static void syncTo(ServerPlayer player) {
        for (Loaded<?, ?> map : DECLARED) {
            map.syncTo(player);
        }
    }

    private static Loaded<?, ?> byId(Identifier id) {
        return DECLARED.stream().filter(map -> map.id.equals(id)).findFirst().orElse(null);
    }

    /**
     * NeoForge's folder rule: the registry's path, prefixed by its namespace unless that is `minecraft`.
     */
    private static String folder(ResourceKey<? extends Registry<?>> registry) {
        Identifier id = registry.identifier();
        return (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? "" : id.getNamespace() + "/") + id.getPath();
    }

    static class Loaded<K, V> implements DataMap<K, V> {

        private final Identifier id;
        private final DataMapSpec<K, V> spec;
        private final Codec<Map<Identifier, V>> valuesCodec;
        private volatile Map<Identifier, V> values = Map.of();

        Loaded(Identifier id, DataMapSpec<K, V> spec) {
            this.id = id;
            this.spec = spec;
            this.valuesCodec = Codec.unboundedMap(Identifier.CODEC, spec.codec());
        }

        @Override
        @Nullable
        public V get(HolderLookup.RegistryLookup<K> lookup, ResourceKey<K> key) {
            // The lookup is what NeoForge needs to find its per-registry storage; here the values are
            // already ours, so the key alone answers it.
            return this.values.get(key.identifier());
        }

        @Override
        @Nullable
        public V get(Holder<K> holder) {
            return holder.unwrapKey().map(k -> this.values.get(k.identifier())).orElse(null);
        }

        void reload(ResourceManager manager) {
            var converter = FileToIdConverter.json("data_maps/" + folder(this.spec.registry()));
            Map<Identifier, V> merged = new LinkedHashMap<>();

            // Every pack that declares this map, lowest priority first, so later packs win.
            for (var entry : converter.listMatchingResourceStacks(manager).entrySet()) {
                if (!converter.fileToId(entry.getKey()).equals(this.id)) continue;
                for (Resource resource : entry.getValue()) {
                    try (Reader reader = resource.openAsReader()) {
                        this.readOne(JsonParser.parseReader(reader), merged);
                    }
                    catch (Exception ex) {
                        Placebo.LOGGER.error("Failed to read data map {} from {}: {}",
                            this.id, resource.sourcePackId(), ex.toString());
                    }
                }
            }

            synchronized (this.syncCacheLock) {
                this.values = immutableMap(merged);
                this.valuesGeneration++;
                this.syncCache.clear();
            }
            Placebo.LOGGER.debug("Loaded {} value(s) for data map {}.", this.values.size(), this.id);
        }

        private void readOne(JsonElement json, Map<Identifier, V> into) {
            var obj = json.getAsJsonObject();

            if (obj.has("replace") && obj.get("replace").getAsBoolean()) {
                into.clear();
            }

            if (obj.has("values")) {
                into.putAll(this.valuesCodec.parse(JsonOps.INSTANCE, obj.get("values")).getOrThrow());
            }

            if (obj.has("remove")) {
                var remove = obj.get("remove");
                if (!remove.isJsonArray()) {
                    // NeoForge's object form runs a custom remover. Nothing here declares one, and
                    // silently ignoring a removal would leave data in the map that the pack asked to
                    // take out -- worse than refusing to load it.
                    throw new UnsupportedOperationException(
                        "Data map " + this.id + " uses the object form of `remove`, which needs a custom "
                            + "remover; only the list form is implemented.");
                }
                for (JsonElement element : remove.getAsJsonArray()) {
                    into.remove(Identifier.parse(element.getAsString()));
                }
            }
        }

        private final Object syncCacheLock = new Object();
        private final IdentityHashMap<RegistryAccess, EncodedSync> syncCache = new IdentityHashMap<>();
        private volatile long valuesGeneration;

        void syncTo(ServerPlayer player) {
            if (this.spec.networkCodec().isEmpty()) return;
            try {
                RegistryAccess access = player.registryAccess();
                SyncPayload payload = this.encodedSync(access);
                PayloadSender.toPlayer(player, payload);
            }
            catch (Exception ex) {
                String message = "Failed to encode synced data map " + this.id + ": " + ex.getMessage();
                Placebo.LOGGER.error(message, ex);
                if (this.spec.mandatorySync()) {
                    player.connection.disconnect(Component.literal(message));
                }
            }
        }

        void acceptSync(Map<Identifier, JsonElement> encoded, HolderLookup.Provider registries) {
            Codec<V> codec = this.spec.networkCodec().orElseThrow();
            RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            Map<Identifier, V> decoded = new LinkedHashMap<>();
            encoded.forEach((id, json) -> decoded.put(id, codec.parse(ops, json).getOrThrow()));
            this.values = immutableMap(decoded);
        }

        private SyncPayload encodedSync(RegistryAccess access) {
            synchronized (this.syncCacheLock) {
                long generation = this.valuesGeneration;
                EncodedSync cached = this.syncCache.get(access);
                if (cached != null && cached.generation() == generation) {
                    return cached.payload();
                }

                Codec<V> codec = this.spec.networkCodec().orElseThrow();
                RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
                ByteBuf source = Unpooled.buffer();
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(source, access);
                try {
                    ByteBufCodecs.writeCount(buf, this.values.size(), MAX_SYNCED_VALUES);
                    for (Map.Entry<Identifier, V> entry : this.values.entrySet()) {
                        buf.writeIdentifier(entry.getKey());
                        JsonElement json = codec.encodeStart(ops, entry.getValue()).getOrThrow();
                        buf.writeUtf(json.toString(), MAX_SYNCED_VALUE_JSON);
                    }
                    byte[] body = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), body);
                    SyncPayload payload = SyncPayload.raw(this.id, this.spec.mandatorySync(), body);
                    this.syncCache.put(access, new EncodedSync(generation, payload));
                    return payload;
                }
                finally {
                    buf.release();
                }
            }
        }

        void clearSyncCache() {
            synchronized (this.syncCacheLock) {
                this.syncCache.clear();
            }
        }

        /** Test seam for publishing an immutable values generation without constructing reload resources. */
        void publishForTesting(Map<Identifier, V> values) {
            synchronized (this.syncCacheLock) {
                this.values = immutableMap(values);
                this.valuesGeneration++;
                this.syncCache.clear();
            }
        }

        /** Test seam for exercising the same lazy access-keyed cache used by player sync. */
        SyncPayload encodedSyncForTesting(RegistryAccess access) {
            return this.encodedSync(access);
        }

        long valuesGeneration() {
            return this.valuesGeneration;
        }

        private record EncodedSync(long generation, SyncPayload payload) {}
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static final class SyncPayload implements CustomPacketPayload {

        private final Identifier id;
        private final boolean mandatory;
        private final Map<Identifier, JsonElement> values;
        /** Immutable outbound body containing the count and value strings; null means legacy map form. */
        private final byte[] rawBody;

        public static final Type<SyncPayload> TYPE = new Type<>(Placebo.loc("data_map_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(SyncPayload::write, SyncPayload::read);

        public SyncPayload(Identifier id, boolean mandatory, Map<Identifier, JsonElement> values) {
            this(id, mandatory, immutableMap(values), null);
        }

        private SyncPayload(Identifier id, boolean mandatory, byte[] rawBody) {
            this(id, mandatory, Map.of(), rawBody);
        }

        private SyncPayload(Identifier id, boolean mandatory, Map<Identifier, JsonElement> values, byte[] rawBody) {
            this.id = Objects.requireNonNull(id, "id");
            this.mandatory = mandatory;
            this.values = values;
            this.rawBody = rawBody;
        }

        /** Creates an outbound payload backed by immutable raw body bytes. */
        public static SyncPayload raw(Identifier id, boolean mandatory, byte[] rawBody) {
            return new SyncPayload(id, mandatory, rawBody.clone());
        }

        public Identifier id() {
            return this.id;
        }

        public boolean mandatory() {
            return this.mandatory;
        }

        public Map<Identifier, JsonElement> values() {
            return this.values;
        }

        /** Defensive copy for diagnostics/tests; the writer uses the private immutable array directly. */
        public byte[] rawBody() {
            return this.rawBody == null ? null : this.rawBody.clone();
        }

        /** Encodes the exact legacy body (everything after id and mandatory) into immutable bytes. */
        static byte[] encodeBody(Map<Identifier, JsonElement> values) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                writeBody(buf, values);
                byte[] result = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), result);
                return result;
            }
            finally {
                buf.release();
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buf, SyncPayload payload) {
            buf.writeIdentifier(payload.id);
            buf.writeBoolean(payload.mandatory);
            if (payload.rawBody != null) {
                // writeBytes(byte[]) copies and does not consume a source reader index.
                buf.writeBytes(payload.rawBody);
            }
            else {
                writeBody(buf, payload.values);
            }
        }

        private static void writeBody(RegistryFriendlyByteBuf buf, Map<Identifier, JsonElement> values) {
            ByteBufCodecs.writeCount(buf, values.size(), MAX_SYNCED_VALUES);
            values.forEach((id, json) -> {
                buf.writeIdentifier(id);
                buf.writeUtf(json.toString(), MAX_SYNCED_VALUE_JSON);
            });
        }

        private static SyncPayload read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            boolean mandatory = buf.readBoolean();
            int size = ByteBufCodecs.readCount(buf, MAX_SYNCED_VALUES);
            Map<Identifier, JsonElement> values = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                values.put(buf.readIdentifier(), JsonParser.parseString(buf.readUtf(MAX_SYNCED_VALUE_JSON)));
            }
            return new SyncPayload(id, mandatory, immutableMap(values));
        }

        public static class Provider implements PayloadProvider<SyncPayload> {

            @Override
            public Type<SyncPayload> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, SyncPayload> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(SyncPayload msg, PayloadContext ctx) {
                Loaded<?, ?> map = byId(msg.id);
                if (map == null) {
                    String error = "Server sent unknown data map " + msg.id;
                    if (msg.mandatory) ctx.disconnect(Component.literal(error));
                    else Placebo.LOGGER.warn(error);
                    return;
                }
                try {
                    map.acceptSync(msg.values(), ctx.player().registryAccess());
                }
                catch (Exception ex) {
                    String error = "Failed to decode synced data map " + msg.id + ": " + ex.getMessage();
                    if (msg.mandatory) ctx.disconnect(Component.literal(error));
                    else Placebo.LOGGER.error(error, ex);
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
                return "1";
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SyncPayload other)) return false;
            return this.id.equals(other.id) && this.mandatory == other.mandatory
                && this.values.equals(other.values) && java.util.Arrays.equals(this.rawBody, other.rawBody);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(this.id, this.mandatory, this.values) + java.util.Arrays.hashCode(this.rawBody);
        }

        @Override
        public String toString() {
            return this.rawBody == null
                ? "SyncPayload[id=" + this.id + ", mandatory=" + this.mandatory + ", values=" + this.values + "]"
                : "SyncPayload[id=" + this.id + ", mandatory=" + this.mandatory + ", rawBodyLength=" + this.rawBody.length + "]";
        }
    }

    /** Only for tests and diagnostics: the maps declared so far. */
    public static List<Identifier> declared() {
        return DECLARED.stream().map(m -> m.id).toList();
    }

    /** Drops registry-access-specific encoded bodies when the server goes away. */
    public static void clearSyncCaches() {
        DECLARED.forEach(Loaded::clearSyncCache);
    }


}
