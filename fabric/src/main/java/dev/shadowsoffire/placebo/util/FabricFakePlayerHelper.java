package dev.shadowsoffire.placebo.util;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link FakePlayerHelper.Impl} for Fabric, over {@code fabric-events-interaction-v0}'s {@code FakePlayer}.
 * <p>
 * Its no-profile {@code get} uses the same default UUID NeoForge does, which is also the constant
 * {@code GatewayEntity} falls back to when it cannot resolve a summoner.
 */
public class FabricFakePlayerHelper implements FakePlayerHelper.Impl {

    @Override
    public ServerPlayer get(ServerLevel level, GameProfile profile) {
        return FakePlayer.get(level, profile);
    }

    @Override
    public ServerPlayer getDefault(ServerLevel level) {
        return FakePlayer.get(level);
    }

}
