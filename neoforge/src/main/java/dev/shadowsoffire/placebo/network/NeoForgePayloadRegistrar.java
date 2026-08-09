package dev.shadowsoffire.placebo.network;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;

import dev.shadowsoffire.placebo.Placebo;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Flushes {@link PayloadHelper}'s providers into NeoForge's network registry, and dispatches incoming
 * payloads to them.
 * <p>
 * The provider bookkeeping lives in {@link PayloadHelper} (common); only this half is loader-specific.
 */
public class NeoForgePayloadRegistrar {

    @SubscribeEvent
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void registerProviders(RegisterPayloadHandlersEvent event) {
        for (PayloadProvider prov : PayloadHelper.drain()) {
            NetworkRegistry.register(prov.getType(), prov.getCodec(), new PayloadHandler(prov), new PayloadHandler(prov), prov.getSupportedProtocols(), prov.getFlow(), prov.getVersion(), prov.isOptional());
        }
    }

    private static class PayloadHandler<T extends CustomPacketPayload> implements IPayloadHandler<T> {

        private PayloadProvider<T> provider;
        private Optional<PacketFlow> flow;
        private List<ConnectionProtocol> protocols;

        private PayloadHandler(PayloadProvider<T> provider) {
            this.provider = provider;
            this.flow = provider.getFlow();
            this.protocols = provider.getSupportedProtocols();
            Preconditions.checkArgument(!this.protocols.isEmpty(), "The payload registration for " + provider.getType().id() + " must specify at least one valid protocol.");
        }

        @Override
        public void handle(T payload, IPayloadContext context) {
            if (this.flow.isPresent() && this.flow.get() != context.flow()) {
                Placebo.LOGGER.error("Received a payload {} on the incorrect side.", payload.type().id());
                return;
            }

            if (!this.protocols.contains(context.protocol())) {
                Placebo.LOGGER.error("Received a payload {} on the incorrect protocol.", payload.type().id());
                return;
            }

            if (context.flow() == PacketFlow.CLIENTBOUND) {
                switch (provider.getHandlerThread()) {
                    case MAIN -> context.enqueueWork(() -> this.provider.handleClient(payload, new NeoForgePayloadContext(context)));
                    case NETWORK -> this.provider.handleClient(payload, new NeoForgePayloadContext(context));
                }
            }
            else {
                switch (provider.getHandlerThread()) {
                    case MAIN -> context.enqueueWork(() -> this.provider.handleServer(payload, new NeoForgePayloadContext(context)));
                    case NETWORK -> this.provider.handleServer(payload, new NeoForgePayloadContext(context));
                }
            }
        }
    }

}
