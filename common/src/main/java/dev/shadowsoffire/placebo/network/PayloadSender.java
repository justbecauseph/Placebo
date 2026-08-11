package dev.shadowsoffire.placebo.network;

import java.util.Objects;

import dev.architectury.utils.GameInstance;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Sends payloads server-to-client, loader-neutrally. The counterpart to {@link PayloadHelper}, which only
 * registers them.
 * <p>
 * Sized from what the stack actually calls: 18 uses of NeoForge's {@code PacketDistributor} resolve to three
 * destinations, and only two of them need the platform.
 * {@link #toAllPlayers(CustomPacketPayload)} is built on {@link #toPlayer} here rather than delegated, keeping
 * the platform surface to dispatch alone -- the same division {@link PayloadHelper} already uses.
 * <p>
 * <b>Client-to-server is not here.</b> The stack's nine of those go through NeoForge's
 * {@code ClientPacketDistributor}, and both loaders' send-to-server entry points are client-only classes --
 * touching one from here would load client code on a dedicated server. Those call sites are all client-side
 * already, so they get their own sender once these repos have client source sets.
 * <p>
 * Deliberately separate from {@code PayloadHelper}: mixing a runtime send API into a startup-time registry
 * invites calling the wrong one at the wrong time.
 */
public class PayloadSender {

    private static Impl impl;

    /**
     * Installed by the platform entrypoint before any payload can be sent.
     */
    public static void setImpl(Impl impl) {
        PayloadSender.impl = Objects.requireNonNull(impl);
    }

    private static Impl impl() {
        if (impl == null) {
            throw new IllegalStateException("No PayloadSender implementation has been installed. "
                + "The platform entrypoint must call PayloadSender.setImpl before any payload is sent.");
        }
        return impl;
    }

    /**
     * Sends a payload to one player. Server to client.
     */
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        impl().toPlayer(player, payload);
    }

    /**
     * Sends a payload to every player tracking the given chunk -- the ones who could see whatever it describes.
     */
    public static void toPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload) {
        impl().toPlayersTrackingChunk(level, pos, payload);
    }

    /**
     * Sends a payload to every connected player.
     * <p>
     * Common rather than platform: iterating the player list is vanilla, and both loaders' own versions do the
     * same thing underneath.
     */
    public static void toAllPlayers(CustomPacketPayload payload) {
        MinecraftServer server = GameInstance.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            toPlayer(player, payload);
        }
    }

    /**
     * The dispatch the platform has to supply. Everything else is built on top of it.
     */
    public interface Impl {

        void toPlayer(ServerPlayer player, CustomPacketPayload payload);

        void toPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload);
    }

}
