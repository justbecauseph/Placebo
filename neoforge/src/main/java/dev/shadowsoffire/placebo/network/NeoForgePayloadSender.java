package dev.shadowsoffire.placebo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@link PayloadSender.Impl} for NeoForge -- a straight pass-through to {@code PacketDistributor}, so nothing
 * about how packets travel on NeoForge changes.
 */
public class NeoForgePayloadSender implements PayloadSender.Impl {

    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void toPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, pos, payload);
    }

}
