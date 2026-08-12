package dev.shadowsoffire.placebo.dynreg;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.architectury.registry.ReloadListenerRegistry;
import dev.shadowsoffire.placebo.registry.FabricDataMaps;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;

/**
 * Fabric implementations of the {@link DynRegPlatform} hooks. Installed from {@code PlaceboFabric}.
 * <p>
 * Counterpart to {@code NeoForgeDynReg}. The mapping is close to one-for-one:
 * <ul>
 * <li>{@code OnDatapackSyncEvent} → {@link ServerLifecycleEvents#SYNC_DATA_PACK_CONTENTS}, which Fabric
 * documents for exactly this case ("can be used to sync data loaded with custom resource reloaders")
 * <li>{@code AddServerReloadListenersEvent} → Architectury's {@link ReloadListenerRegistry}, which takes the
 * dependency list directly, so no buffering is needed the way NeoForge's event required
 * <li>{@code PacketDistributor} → {@link ServerPlayNetworking#send}
 * </ul>
 */
public final class FabricDynReg {

    private static MinecraftServer server;
    private static final RegistryAccess.Frozen BUILTIN_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static volatile HolderLookup.Provider reloadLookup = BUILTIN_LOOKUP;

    private FabricDynReg() {}

    public static void install() {
        bindRuntimeHooks();

        // The tag manager is itself a reload listener, and must be registered like any other.
        ReloadListenerRegistry.register(PackType.SERVER_DATA, DynamicTagManager.INSTANCE, DynamicTagManager.ID);
    }

    /**
     * Rebinds the per-class-state hooks used by an Architectury downstream development transform.
     * <p>
     * Loom may load a dependent common development jar against a fresh copy of Placebo's common static
     * state after Placebo's own entrypoint has run. Registering the singleton tag listener again is illegal,
     * but the copied {@link DynRegPlatform} and {@link SyncManagement} still need their Fabric delegates.
     * Production jars normally share one state; this method is intentionally safe there as well because
     * Fabric lifecycle events allow multiple listeners.
     */
    public static void rebindRuntimeHooks() {
        bindRuntimeHooks();
    }

    private static void bindRuntimeHooks() {
        DynRegPlatform.install(FabricDynReg::contextFor, FabricDynReg::registerReloadListener, new Sender());

        // The registry lookup has to come from the running server: unlike NeoForge, Fabric injects nothing
        // into the listener, so there is no context to read off it.
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> SyncManagement.syncAll(player));
    }

    private static ReloadContext contextFor(@Nullable DynamicRegistry<?> registry) {
        // Fabric performs a server-data validation reload while opening the world-selection/create-world
        // screens, before an integrated server exists. Built-in registries are sufficient for that pass;
        // the authoritative server reload runs again with the full registry access after SERVER_STARTING.
        return new FabricReloadContext(server == null ? reloadLookup : server.registryAccess());
    }

    /** Called by the reload-resources mixin immediately before custom listeners run. */
    public static void setReloadLookup(HolderLookup.Provider lookup) {
        reloadLookup = lookup;
    }

    /**
     * Registers the registry as a reload listener, ordered before the tag manager so tag loading runs after
     * registry content has been deserialized -- the same ordering NeoForge gets from {@code addDependency}.
     * <p>
     * Also declared to run after the data map listener, so a deserializer that reads a data map sees the
     * loaded values rather than an empty map on the first load and the right one only after {@code /reload}.
     * Nothing here reads one today; the edge is declared because the failure it prevents is silent and the
     * dependency costs nothing.
     */
    private static void registerReloadListener(Identifier id, DynamicRegistry<?> registry, List<Identifier> dependencies) {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, registry, id, dependencies);

        ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
        loader.addListenerOrdering(FabricDataMaps.ID, id);
        loader.addListenerOrdering(id, DynamicTagManager.ID);
    }

    private static class Sender implements DynRegPlatform.SyncHandler {

        private static Consumer<CustomPacketPayload> target(@Nullable ServerPlayer player) {
            if (player != null) {
                return payload -> ServerPlayNetworking.send(player, payload);
            }
            return payload -> {
                if (server != null) {
                    PlayerLookup.all(server).forEach(p -> ServerPlayNetworking.send(p, payload));
                }
            };
        }

        @Override
        public void start(@Nullable ServerPlayer player, Identifier registryId) {
            target(player).accept(new DynRegPayloads.Start(registryId));
        }

        @Override
        public <R> void content(@Nullable ServerPlayer player, Identifier registryId, Identifier key, R value) {
            target(player).accept(new DynRegPayloads.Content<>(registryId, key, value));
        }

        @Override
        public void tags(@Nullable ServerPlayer player, Identifier registryId, Map<Identifier, List<Identifier>> tags) {
            target(player).accept(new TagSyncPayload(registryId, tags));
        }

        @Override
        public void end(@Nullable ServerPlayer player, Identifier registryId) {
            target(player).accept(new DynRegPayloads.End(registryId));
        }

    }

}
