package dev.shadowsoffire.placebo.network;

/**
 * Which thread a payload handler runs on. Loader-neutral counterpart to NeoForge's {@code HandlerThread}.
 */
public enum HandlerThread {

    /**
     * Dispatch to the main game thread. Correct for anything touching world or player state.
     */
    MAIN,

    /**
     * Run directly on the network thread. Only for handlers that touch nothing the game thread owns.
     */
    NETWORK

}
