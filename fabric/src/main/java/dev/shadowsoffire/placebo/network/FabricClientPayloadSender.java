package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric's client-to-server payload dispatch. Loaded only by the Fabric client entrypoint. */
public class FabricClientPayloadSender implements ClientPayloadSender.Impl {

    @Override
    public void toServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
