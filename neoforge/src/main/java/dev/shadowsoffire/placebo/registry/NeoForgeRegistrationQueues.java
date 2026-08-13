package dev.shadowsoffire.placebo.registry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.architectury.platform.hooks.EventBusesHooks;
import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * NeoForge registration queues for helpers created before the platform helper factory is installed.
 * <p>
 * FML constructs mod classes in parallel, so a dependent mod can retain a common {@link DeferredHelper}
 * while Placebo's NeoForge entrypoint installs the registry and data-map factories. Queue the NeoForge-only
 * values by their real owner instead of assuming that the owner is a {@link NeoForgeDeferredHelper}.
 */
final class NeoForgeRegistrationQueues {

    private static final Map<DeferredHelper, Queue> QUEUES = new IdentityHashMap<>();

    private NeoForgeRegistrationQueues() {}

    static synchronized void registerRegistry(DeferredHelper owner, Registry<?> registry) {
        queue(owner).registries.add(registry);
    }

    static synchronized void registerDataMap(DeferredHelper owner, DataMapType<?, ?> type) {
        queue(owner).dataMaps.add(type);
    }

    private static Queue queue(DeferredHelper owner) {
        Queue queue = QUEUES.get(owner);
        if (queue == null) {
            queue = new Queue();
            QUEUES.put(owner, queue);
            Queue registeredQueue = queue;
            EventBusesHooks.whenAvailable(owner.modid, bus -> {
                bus.addListener((NewRegistryEvent event) -> flushRegistries(registeredQueue, event));
                bus.addListener((RegisterDataMapTypesEvent event) -> flushDataMaps(registeredQueue, event));
            });
        }
        return queue;
    }

    private static synchronized void flushRegistries(Queue queue, NewRegistryEvent event) {
        queue.registries.forEach(event::register);
        queue.registries.clear();
    }

    private static synchronized void flushDataMaps(Queue queue, RegisterDataMapTypesEvent event) {
        queue.dataMaps.forEach(event::register);
        queue.dataMaps.clear();
    }

    private static class Queue {

        private final List<Registry<?>> registries = new ArrayList<>();
        private final List<DataMapType<?, ?>> dataMaps = new ArrayList<>();
    }
}
