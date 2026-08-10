package dev.shadowsoffire.placebo.dynreg;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.architectury.registry.ReloadListenerRegistry;
import dev.shadowsoffire.placebo.registry.FabricDataMaps;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    private FabricDynReg() {}

    public static void install() {
        DynRegPlatform.install(FabricDynReg::contextFor, FabricDynReg::registerReloadListener, new Sender());

        // The registry lookup has to come from the running server: unlike NeoForge, Fabric injects nothing
        // into the listener, so there is no context to read off it.
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> SyncManagement.syncAll(player));

        // The tag manager is itself a reload listener, and must be registered like any other.
        ReloadListenerRegistry.register(PackType.SERVER_DATA, DynamicTagManager.INSTANCE, DynamicTagManager.ID);
    }

    private static ReloadContext contextFor(@Nullable DynamicRegistry<?> registry) {
        if (server == null) {
            throw new IllegalStateException("A dynamic registry reload was requested with no server running. "
                + "On Fabric the registry lookup comes from the server, so there is nothing to build a ReloadContext from.");
        }
        return new FabricReloadContext(server.registryAccess());
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
    private static void registerReloadListener(Identifier id, DynamicRegistry<?> registry) {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, registry, id,
            List.of(DynamicTagManager.ID, FabricDataMaps.ID));
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
