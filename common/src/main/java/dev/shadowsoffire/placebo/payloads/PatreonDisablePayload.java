package dev.shadowsoffire.placebo.payloads;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.network.PayloadContext;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import dev.shadowsoffire.placebo.network.PayloadSender;
import dev.shadowsoffire.placebo.patreon.TrailsManager;
import dev.shadowsoffire.placebo.patreon.WingsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/** Synchronizes whether a Patreon trail or wing is hidden for a player. */
public record PatreonDisablePayload(CosmeticType cosmetic, UUID id) implements CustomPacketPayload {

    public static final Type<PatreonDisablePayload> TYPE = new Type<>(Placebo.loc("patreon_disable"));

    public static final StreamCodec<FriendlyByteBuf, PatreonDisablePayload> CODEC = StreamCodec.composite(
        CosmeticType.STREAM_CODEC, PatreonDisablePayload::cosmetic,
        UUIDUtil.STREAM_CODEC, PatreonDisablePayload::id,
        PatreonDisablePayload::new);

    @Override
    public Type<PatreonDisablePayload> type() {
        return TYPE;
    }

    public static enum CosmeticType {
        TRAILS,
        WINGS;

        public static final IntFunction<CosmeticType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, CosmeticType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
    }

    public static class Provider implements PayloadProvider<PatreonDisablePayload> {

        @Override
        public Type<PatreonDisablePayload> getType() {
            return TYPE;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, PatreonDisablePayload> getCodec() {
            return CODEC;
        }

        @Override
        public void handleServer(PatreonDisablePayload msg, PayloadContext ctx) {
            PayloadSender.toAllPlayers(new PatreonDisablePayload(msg.cosmetic(), ctx.player().getUUID()));
        }

        @Override
        public void handleClient(PatreonDisablePayload msg, PayloadContext ctx) {
            Set<UUID> set = switch (msg.cosmetic()) {
                case TRAILS -> TrailsManager.DISABLED;
                case WINGS -> WingsManager.DISABLED;
            };

            if (set.contains(msg.id)) {
                set.remove(msg.id);
            }
            else {
                set.add(msg.id);
            }
        }

        @Override
        public List<ConnectionProtocol> getSupportedProtocols() {
            return List.of(ConnectionProtocol.PLAY);
        }

        @Override
        public Optional<PacketFlow> getFlow() {
            return Optional.empty();
        }

        @Override
        public String getVersion() {
            return "1";
        }
    }
}
