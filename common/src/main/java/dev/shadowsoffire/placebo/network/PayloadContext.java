package dev.shadowsoffire.placebo.network;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-neutral replacement for NeoForge's {@code IPayloadContext}.
 * <p>
 * Deliberately small. The surface here is what payload handlers across the whole Shadows stack actually use
 * -- {@link #player()} in eleven places, {@link #enqueueWork} in two, {@link #disconnect} in one. NeoForge's
 * {@code flow()} and {@code protocol()} are not here because only the dispatch layer reads them, and that
 * stays platform-side.
 */
public interface PayloadContext {

    /**
     * The player this payload concerns: the receiving player on the client, the sending player on the server.
     */
    Player player();

    /**
     * Runs the given action on the main thread.
     * <p>
     * Handlers declared {@link HandlerThread#MAIN} are already dispatched there, so this is only needed when a
     * {@link HandlerThread#NETWORK} handler has to touch game state.
     */
    void enqueueWork(Runnable action);

    /**
     * Disconnects the connection this payload arrived on, with the given reason.
     */
    void disconnect(Component reason);

}
