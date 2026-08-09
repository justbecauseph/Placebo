package dev.shadowsoffire.placebo.network;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Adapts NeoForge's {@code IPayloadContext} onto the loader-neutral {@link PayloadContext}.
 * <p>
 * {@code flow()} and {@code protocol()} are deliberately not exposed: only {@link PayloadHelper}'s dispatch
 * reads them, and that is platform-side anyway.
 */
public record NeoForgePayloadContext(IPayloadContext delegate) implements PayloadContext {

    @Override
    public Player player() {
        return this.delegate.player();
    }

    @Override
    public void enqueueWork(Runnable action) {
        this.delegate.enqueueWork(action);
    }

    @Override
    public void disconnect(Component reason) {
        this.delegate.connection().disconnect(reason);
    }

}
