package dev.shadowsoffire.placebo.dynreg;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import dev.shadowsoffire.placebo.mixin.ContextAwareReloadListenerAccessor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge implementations of the {@link DynRegPlatform} hooks. Installed from {@code PlaceboNeoForge}.
 */
public final class NeoForgeDynReg {

    /**
     * Registries queued for reload registration. {@link AddServerReloadListenersEvent} is the only place the
     * listener can be added, so registrations made before it fires are buffered here.
     */
    private static final Map<Identifier, PendingRegistry> PENDING = new java.util.LinkedHashMap<>();

    private NeoForgeDynReg() {}

    public static void install() {
        DynRegPlatform.install(NeoForgeDynReg::contextFor, NeoForgeDynReg::queue, new Sender());
    }

    /**
     * Reads the registry lookup and condition context that NeoForge injected into the listener.
     */
    private static ReloadContext contextFor(@Nullable DynamicRegistry<?> registry) {
        ContextAwareReloadListenerAccessor accessor = (ContextAwareReloadListenerAccessor) (registry == null
            ? DynamicTagManager.INSTANCE
            : registry);
        return new NeoForgeReloadContext(accessor.placebo$getRegistryLookup(), accessor.placebo$getContext());
    }

    private static void queue(Identifier id, DynamicRegistry<?> registry, List<Identifier> dependencies) {
        PENDING.put(id, new PendingRegistry(registry, List.copyOf(dependencies)));
    }

    /**
     * Adds every queued registry to the reload listener set, each ordered before the tag manager so that tag
     * loading runs after registry content has been deserialized.
     */
    public static void addReloadListeners(AddServerReloadListenersEvent e) {
        PENDING.forEach((id, pending) -> {
            e.addListener(id, pending.registry());
            pending.dependencies().forEach(dependency -> e.addDependency(dependency, id));
            e.addDependency(id, DynamicTagManager.ID);
        });
    }

    private record PendingRegistry(DynamicRegistry<?> registry, List<Identifier> dependencies) {}

    /**
     * Bridges NeoForge's datapack-sync event onto the loader-neutral {@link SyncManagement#syncAll}.
     * Fabric's equivalent is {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS}.
     */
    public static void onDatapackSync(OnDatapackSyncEvent e) {
        SyncManagement.syncAll(e.getPlayer());
    }

    private static class Sender implements DynRegPlatform.SyncHandler {

        private static Consumer<CustomPacketPayload> target(@Nullable ServerPlayer player) {
            return player == null ? PacketDistributor::sendToAllPlayers : payload -> PacketDistributor.sendToPlayer(player, payload);
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
