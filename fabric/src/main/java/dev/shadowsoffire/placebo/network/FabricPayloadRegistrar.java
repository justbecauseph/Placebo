package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Flushes {@link PayloadHelper}'s providers into Fabric's networking API. Counterpart to
 * {@code NeoForgePayloadRegistrar}.
 * <p>
 * Two differences from NeoForge worth knowing:
 * <ul>
 * <li>Fabric registers the codec per direction ({@code PayloadTypeRegistry.clientboundPlay} / {@code serverboundPlay}) rather
 * than passing a flow alongside one registration, so a provider's {@link PayloadProvider#getFlow} decides
 * which side to register. An absent flow means both.
 * <li>Fabric has no per-handler thread selection: its receivers already run on the main thread. That makes
 * {@link HandlerThread#MAIN} the natural behaviour and {@link HandlerThread#NETWORK} unimplementable here,
 * so it is ignored rather than faked.
 * </ul>
 */
public class FabricPayloadRegistrar {

    private FabricPayloadRegistrar() {}

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void register() {
        for (PayloadProvider prov : PayloadHelper.drain()) {
            PacketFlow flow = (PacketFlow) prov.getFlow().orElse(null);

            if (flow == null || flow == PacketFlow.CLIENTBOUND) {
                PayloadTypeRegistry.clientboundPlay().register(prov.getType(), prov.getCodec());
                registerClient(prov);
            }
            if (flow == null || flow == PacketFlow.SERVERBOUND) {
                PayloadTypeRegistry.serverboundPlay().register(prov.getType(), prov.getCodec());
                ServerPlayNetworking.registerGlobalReceiver(prov.getType(),
                    (payload, ctx) -> prov.handleServer(payload, new ServerContext(ctx)));
            }
        }
    }

    /**
     * Split out so the client-only Fabric API type is not loaded on a dedicated server.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerClient(PayloadProvider prov) {
        ClientPlayNetworking.registerGlobalReceiver(prov.getType(),
            (payload, ctx) -> prov.handleClient(payload, new ClientContext(ctx)));
    }

    private record ServerContext(ServerPlayNetworking.Context delegate) implements PayloadContext {

        @Override
        public Player player() {
            return this.delegate.player();
        }

        @Override
        public void enqueueWork(Runnable action) {
            this.delegate.server().execute(action);
        }

        @Override
        public void disconnect(Component reason) {
            this.delegate.player().connection.disconnect(reason);
        }
    }

    private record ClientContext(ClientPlayNetworking.Context delegate) implements PayloadContext {

        @Override
        public Player player() {
            return this.delegate.player();
        }

        @Override
        public void enqueueWork(Runnable action) {
            this.delegate.client().execute(action);
        }

        @Override
        public void disconnect(Component reason) {
            this.delegate.responseSender().disconnect(reason);
        }
    }

}
