package dev.shadowsoffire.placebo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** NeoForge's client-to-server payload dispatch. Loaded only by {@code NeoForgeClientEvents}. */
public class NeoForgeClientPayloadSender implements ClientPayloadSender.Impl {

    @Override
    public void toServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
