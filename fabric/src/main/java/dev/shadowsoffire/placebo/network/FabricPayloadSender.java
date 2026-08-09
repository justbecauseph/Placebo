package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * {@link PayloadSender.Impl} for Fabric.
 * <p>
 * Fabric has no "send to everyone tracking this" call, so the destination is resolved with
 * {@link PlayerLookup} and each player is sent to individually -- which is what NeoForge's
 * {@code PacketDistributor} does underneath as well.
 */
public class FabricPayloadSender implements PayloadSender.Impl {

    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void toPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

}
