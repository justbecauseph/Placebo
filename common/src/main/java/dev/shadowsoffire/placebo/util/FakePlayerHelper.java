package dev.shadowsoffire.placebo.util;

import java.util.Objects;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Obtains fake players, loader-neutrally.
 * <p>
 * Both loaders ship the concept and their factories take the same arguments -- NeoForge's
 * {@code FakePlayerFactory} and Fabric's {@code FakePlayer} (in {@code fabric-events-interaction-v0}) -- so
 * this is a two-method adapter over types that already agree, not a reimplementation. They even share a
 * default UUID.
 * <p>
 * Asking <i>whether</i> a player is fake does not belong here: Architectury already answers it with
 * {@code PlayerHooks.isFake}, and wrapping that would be indirection for its own sake.
 * <p>
 * Returns {@link ServerPlayer} rather than either loader's {@code FakePlayer} subclass, which is all any
 * caller in this stack uses.
 */
public class FakePlayerHelper {

    private static Impl impl;

    /**
     * Installed by the platform entrypoint.
     */
    public static void setImpl(Impl impl) {
        FakePlayerHelper.impl = Objects.requireNonNull(impl);
    }

    private static Impl impl() {
        if (impl == null) {
            throw new IllegalStateException("No FakePlayerHelper implementation has been installed. "
                + "The platform entrypoint must call FakePlayerHelper.setImpl before a fake player is requested.");
        }
        return impl;
    }

    /**
     * The fake player for a given profile, usually a real player's UUID and name so that drops and
     * advancements can still be attributed to them.
     */
    public static ServerPlayer get(ServerLevel level, GameProfile profile) {
        return impl().get(level, profile);
    }

    /**
     * The loader's anonymous fake player, for actions with no player behind them.
     * <p>
     * The two loaders use slightly different profiles for this one -- NeoForge's is named {@code [Minecraft]} --
     * so do not depend on the name. The UUID is the same on both.
     */
    public static ServerPlayer getDefault(ServerLevel level) {
        return impl().getDefault(level);
    }

    public interface Impl {

        ServerPlayer get(ServerLevel level, GameProfile profile);

        ServerPlayer getDefault(ServerLevel level);
    }

}
