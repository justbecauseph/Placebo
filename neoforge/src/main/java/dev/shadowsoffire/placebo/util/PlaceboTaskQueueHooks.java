package dev.shadowsoffire.placebo.util;

import dev.shadowsoffire.placebo.Placebo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires NeoForge's server-lifecycle and tick events into {@link PlaceboTaskQueue}.
 * The queue logic itself lives in {@code common/} and is equally reachable on Fabric.
 */
@EventBusSubscriber(modid = Placebo.MODID)
public class PlaceboTaskQueueHooks {

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        PlaceboTaskQueue.tick();
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        PlaceboTaskQueue.onServerStart();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        PlaceboTaskQueue.onServerStop();
    }

}
