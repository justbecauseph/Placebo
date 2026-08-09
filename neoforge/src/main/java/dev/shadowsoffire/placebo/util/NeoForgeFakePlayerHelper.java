package dev.shadowsoffire.placebo.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@link FakePlayerHelper.Impl} for NeoForge -- a pass-through to {@code FakePlayerFactory}, so fake players
 * are the same instances, cached the same way, as before.
 */
public class NeoForgeFakePlayerHelper implements FakePlayerHelper.Impl {

    @Override
    public ServerPlayer get(ServerLevel level, GameProfile profile) {
        return FakePlayerFactory.get(level, profile);
    }

    @Override
    public ServerPlayer getDefault(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

}
