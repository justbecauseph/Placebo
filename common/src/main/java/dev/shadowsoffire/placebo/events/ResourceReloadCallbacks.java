package dev.shadowsoffire.placebo.events;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loader-neutral resource-reload callbacks for consumers that do not need a platform event bus. */
public final class ResourceReloadCallbacks {

    public static final Event<Client> CLIENT = EventFactory.createLoop();

    private ResourceReloadCallbacks() {}

    public static void fireClient(ResourceManager resources) {
        CLIENT.invoker().reload(resources);
    }

    @FunctionalInterface
    public interface Client {
        void reload(ResourceManager resources);
    }
}
