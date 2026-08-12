package dev.shadowsoffire.placebo.registry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
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

    private static class Loaded<K, V> implements DataMap<K, V> {

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
            Map<Identifier, V> merged = new ConcurrentHashMap<>();

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

            this.values = Map.copyOf(merged);
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

        void syncTo(ServerPlayer player) {
            this.spec.networkCodec().ifPresent(codec -> {
                try {
                    RegistryOps<JsonElement> ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    Map<Identifier, JsonElement> encoded = new java.util.HashMap<>();
                    this.values.forEach((id, value) -> encoded.put(id, codec.encodeStart(ops, value).getOrThrow()));
                    PayloadSender.toPlayer(player, new SyncPayload(this.id, this.spec.mandatorySync(), Map.copyOf(encoded)));
                }
                catch (Exception ex) {
                    String message = "Failed to encode synced data map " + this.id + ": " + ex.getMessage();
                    Placebo.LOGGER.error(message, ex);
                    if (this.spec.mandatorySync()) {
                        player.connection.disconnect(Component.literal(message));
                    }
                }
            });
        }

        void acceptSync(Map<Identifier, JsonElement> encoded, HolderLookup.Provider registries) {
            Codec<V> codec = this.spec.networkCodec().orElseThrow();
            RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            Map<Identifier, V> decoded = new java.util.HashMap<>();
            encoded.forEach((id, json) -> decoded.put(id, codec.parse(ops, json).getOrThrow()));
            this.values = Map.copyOf(decoded);
        }
    }

    public record SyncPayload(Identifier id, boolean mandatory, Map<Identifier, JsonElement> values) implements CustomPacketPayload {

        public static final Type<SyncPayload> TYPE = new Type<>(Placebo.loc("data_map_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(SyncPayload::write, SyncPayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buf, SyncPayload payload) {
            buf.writeIdentifier(payload.id);
            buf.writeBoolean(payload.mandatory);
            ByteBufCodecs.writeCount(buf, payload.values.size(), MAX_SYNCED_VALUES);
            payload.values.forEach((id, json) -> {
                buf.writeIdentifier(id);
                buf.writeUtf(json.toString(), MAX_SYNCED_VALUE_JSON);
            });
        }

        private static SyncPayload read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            boolean mandatory = buf.readBoolean();
            int size = ByteBufCodecs.readCount(buf, MAX_SYNCED_VALUES);
            Map<Identifier, JsonElement> values = new java.util.HashMap<>(size);
            for (int i = 0; i < size; i++) {
                values.put(buf.readIdentifier(), JsonParser.parseString(buf.readUtf(MAX_SYNCED_VALUE_JSON)));
            }
            return new SyncPayload(id, mandatory, Map.copyOf(values));
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
                    map.acceptSync(msg.values, ctx.player().registryAccess());
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
    }

    /** Only for tests and diagnostics: the maps declared so far. */
    public static List<Identifier> declared() {
        return DECLARED.stream().map(m -> m.id).toList();
    }


}
