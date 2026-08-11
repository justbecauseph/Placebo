package dev.shadowsoffire.placebo.network;

import java.util.Objects;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sends a payload from a client to its server.
 * <p>
 * This is deliberately separate from {@link PayloadSender}: each loader exposes this operation from a
 * client-only class, so putting it on the server-to-client sender would make dedicated-server classloading
 * depend on client networking. Callers are client code and install their implementation from the client
 * entrypoint.
 */
public class ClientPayloadSender {

    private static Impl impl;

    private ClientPayloadSender() {}

    /** Installs the loader's client networking implementation. */
    public static void setImpl(Impl impl) {
        ClientPayloadSender.impl = Objects.requireNonNull(impl);
    }

    /** Sends a payload to the server connected to this client. */
    public static void toServer(CustomPacketPayload payload) {
        if (impl == null) {
            throw new IllegalStateException("No ClientPayloadSender implementation has been installed. "
                + "The client entrypoint must call ClientPayloadSender.setImpl before any payload is sent.");
        }
        impl.toServer(payload);
    }

    /** The one client-only operation each loader must supply. */
    public interface Impl {

        void toServer(CustomPacketPayload payload);
    }
}
