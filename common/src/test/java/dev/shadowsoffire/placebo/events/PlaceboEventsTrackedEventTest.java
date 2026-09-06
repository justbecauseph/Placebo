package dev.shadowsoffire.placebo.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.architectury.event.Event;
import dev.architectury.event.EventPriority;

final class PlaceboEventsTrackedEventTest {

    @FunctionalInterface
    private interface Listener {
        void invoke();
    }

    @Test
    void externalRegistrationPublishesFallbackBeforeDelegateMutation() throws Exception {
        ControlledDelegate delegate = new ControlledDelegate();
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        event.invoker();

        Listener listener = () -> {};
        delegate.blockNextRegister();
        AtomicReference<Throwable> registrationFailure = new AtomicReference<>();
        Thread registration = new Thread(() -> {
            try {
                event.register(EventPriority.HIGH, listener);
            }
            catch (Throwable failure) {
                registrationFailure.set(failure);
            }
        });
        registration.start();

        await(delegate.registerEntered);
        assertFalse(delegate.hasListener(listener), "the delegate changed before its registration gate opened");
        assertFalse(event.directAllowed(), "a racing dispatch still observed direct mode before delegate mutation");

        AtomicBoolean directObserved = new AtomicBoolean();
        Thread dispatch = new Thread(() -> {
            directObserved.set(event.directAllowed());
            event.invoker().invoke();
        });
        dispatch.start();
        join(dispatch);
        assertFalse(directObserved.get(), "the concurrent dispatch bypassed fallback mode");

        delegate.allowRegister.countDown();
        join(registration);
        assertNull(registrationFailure.get(), "external registration failed unexpectedly");
        assertTrue(delegate.isRegistered(listener), "the delegate did not receive the completed registration");
        assertFalse(event.directAllowed(), "external registration returned with direct mode enabled");

        delegate.blockNextUnregister();
        AtomicReference<Throwable> unregisterFailure = new AtomicReference<>();
        Thread unregister = new Thread(() -> {
            try {
                event.unregister(listener);
            }
            catch (Throwable failure) {
                unregisterFailure.set(failure);
            }
        });
        unregister.start();

        await(delegate.unregisterEntered);
        assertFalse(event.directAllowed(), "direct mode was restored before delegate removal");
        delegate.allowUnregister.countDown();
        join(unregister);
        assertNull(unregisterFailure.get(), "external unregister failed unexpectedly");
        assertFalse(delegate.isRegistered(listener), "the delegate retained the removed listener");
        assertTrue(event.directAllowed(), "the last external unregister did not restore direct mode");
    }

    @Test
    void clearDisablesDirectModeBeforeDelegateClearAndKeepsItDisabled() throws Exception {
        ControlledDelegate delegate = new ControlledDelegate();
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        Listener listener = () -> {};
        event.register(listener);
        assertFalse(event.directAllowed());

        delegate.blockNextClear();
        AtomicReference<Throwable> clearFailure = new AtomicReference<>();
        Thread clear = new Thread(() -> {
            try {
                event.clearListeners();
            }
            catch (Throwable failure) {
                clearFailure.set(failure);
            }
        });
        clear.start();

        await(delegate.clearEntered);
        assertFalse(event.directAllowed(), "clear published direct mode while delegate clear was pending");
        delegate.allowClear.countDown();
        join(clear);
        assertNull(clearFailure.get(), "clearListeners failed unexpectedly");
        assertFalse(delegate.isRegistered(listener), "clearListeners retained the old listener");
        assertFalse(event.directAllowed(), "clearListeners silently restored direct mode");

        event.registerInternal(EventPriority.NORMAL, () -> {});
        assertFalse(event.directAllowed(), "an owned registration silently re-enabled direct mode after clear");

        event.register(() -> {});
        assertFalse(event.directAllowed(), "a later external registration silently re-enabled direct mode after clear");
    }

    @Test
    void failedExternalRegistrationRollsBackModeAndBookkeeping() {
        ControlledDelegate delegate = new ControlledDelegate();
        IllegalStateException expected = new IllegalStateException("registration rejected");
        delegate.registerFailure = expected;
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        Listener listener = () -> {};

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> event.register(listener));
        assertEquals(expected, actual);
        assertTrue(event.directAllowed(), "a failed first external registration left fallback mode published");
        assertFalse(event.isRegistered(listener), "a failed registration was recorded on the delegate");
    }

    @Test
    void removingAnOwnedListenerPermanentlyDisablesDirectMode() {
        ControlledDelegate delegate = new ControlledDelegate();
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        Listener listener = () -> {};

        event.registerInternal(EventPriority.HIGH, listener);
        assertTrue(event.directAllowed(), "an owned registration unexpectedly disabled direct mode");
        event.unregisterInternal(listener);
        assertFalse(event.directAllowed(), "removing an owned listener restored an incomplete direct chain");
    }

    @Test
    void listenerCountsAndModesDistinguishOwnedAndExternalRegistrations() {
        ControlledDelegate delegate = new ControlledDelegate();
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        Listener owned = () -> {};
        Listener external = () -> {};

        assertEquals(0, event.listenerCount());
        assertFalse(event.hasListeners());
        assertTrue(event.directAllowed());

        event.registerInternal(EventPriority.HIGH, owned);
        assertEquals(1, event.listenerCount());
        assertTrue(event.hasListeners());
        assertFalse(event.hasExternalListeners());
        assertTrue(event.directAllowed(), "owned adapters must not force fallback");

        event.register(EventPriority.LOW, external);
        assertEquals(2, event.listenerCount());
        assertTrue(event.hasExternalListeners());
        assertFalse(event.directAllowed(), "an external listener must force compatibility mode");

        event.unregister(external);
        assertEquals(1, event.listenerCount());
        assertTrue(event.hasListeners());
        assertFalse(event.hasExternalListeners());
        assertTrue(event.directAllowed(), "removing the last external listener should restore direct mode");

        event.unregisterInternal(owned);
        assertEquals(0, event.listenerCount());
        assertFalse(event.hasListeners());
        assertFalse(event.directAllowed(), "removing an owned adapter must fail closed");
    }

    @Test
    void mobDespawnPublicRegistrationFallsBackAndRestoresAfterUnregister() {
        PlaceboEvents.MobDespawn external = ctx -> ctx.setResult(PlaceboEvents.DespawnResult.ALLOW);
        assertTrue(PlaceboEvents.mobDespawnDirectAllowed(), "the fresh despawn bridge should start in direct mode");

        PlaceboEvents.MOB_DESPAWN.register(EventPriority.NORMAL, external);
        try {
            assertFalse(PlaceboEvents.mobDespawnDirectAllowed(),
                "a public despawn listener did not publish compatibility fallback mode");
            assertEquals(PlaceboEvents.DespawnResult.ALLOW,
                PlaceboEvents.fireMobDespawn(null, null),
                "the public despawn listener was not visible through the fallback bridge");
        }
        finally {
            PlaceboEvents.MOB_DESPAWN.unregister(external);
        }

        assertTrue(PlaceboEvents.mobDespawnDirectAllowed(),
            "removing the last public despawn listener did not restore direct mode");
    }

    @Test
    void delegateOrderingAndFirstDuplicateRemovalRemainArchitecturyCompatible() {
        ControlledDelegate delegate = new ControlledDelegate();
        PlaceboEvents.TrackedEvent<Listener> event = new PlaceboEvents.TrackedEvent<>(delegate);
        List<String> order = new ArrayList<>();
        Listener duplicate = () -> order.add("first");
        Listener second = () -> order.add("second");

        event.register(EventPriority.LOWEST, duplicate);
        event.register(EventPriority.HIGHEST, duplicate);
        event.register(EventPriority.NORMAL, second);
        event.invoker().invoke();
        assertEquals(List.of("first", "second", "first"), order);
        assertTrue(event.isRegistered(duplicate));

        order.clear();
        event.unregister(duplicate);
        event.invoker().invoke();
        assertEquals(List.of("first", "second"), order);
        assertTrue(event.isRegistered(duplicate));
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "the controlled delegate did not reach its gate");
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(Duration.ofSeconds(5));
        assertFalse(thread.isAlive(), "the tracked event operation did not complete");
    }

    private static final class ControlledDelegate implements Event<Listener> {

        private final List<Entry> listeners = new ArrayList<>();
        private volatile List<Entry> publishedListeners = List.of();
        private volatile Listener invoker;
        private volatile RuntimeException registerFailure;
        private CountDownLatch registerEntered;
        private CountDownLatch allowRegister;
        private CountDownLatch unregisterEntered;
        private CountDownLatch allowUnregister;
        private CountDownLatch clearEntered;
        private CountDownLatch allowClear;

        @Override
        public synchronized Listener invoker() {
            Listener current = this.invoker;
            if (current == null) {
                List<Entry> snapshot = new ArrayList<>(this.listeners);
                snapshot.sort(Comparator.comparingInt(entry -> entry.priority().ordinal()));
                List<Listener> ordered = snapshot.stream().map(Entry::listener).toList();
                current = () -> ordered.forEach(Listener::invoke);
                this.invoker = current;
            }
            return current;
        }

        @Override
        public void register(Listener listener) {
            register(EventPriority.NORMAL, listener);
        }

        @Override
        public synchronized void register(EventPriority priority, Listener listener) {
            CountDownLatch entered = this.registerEntered;
            if (entered != null) {
                this.registerEntered = null;
                entered.countDown();
                awaitGate(this.allowRegister);
                this.allowRegister = null;
            }
            if (this.registerFailure != null) throw this.registerFailure;
            this.listeners.add(new Entry(priority, listener));
            this.publishedListeners = List.copyOf(this.listeners);
            this.invoker = null;
        }

        @Override
        public synchronized void unregister(Listener listener) {
            CountDownLatch entered = this.unregisterEntered;
            if (entered != null) {
                this.unregisterEntered = null;
                entered.countDown();
                awaitGate(this.allowUnregister);
                this.allowUnregister = null;
            }
            for (int i = 0; i < this.listeners.size(); i++) {
                if (java.util.Objects.equals(this.listeners.get(i).listener(), listener)) {
                    this.listeners.remove(i);
                    this.publishedListeners = List.copyOf(this.listeners);
                    this.invoker = null;
                    return;
                }
            }
        }

        @Override
        public synchronized boolean isRegistered(Listener listener) {
            return this.listeners.stream().anyMatch(entry -> java.util.Objects.equals(entry.listener(), listener));
        }

        @Override
        public synchronized void clearListeners() {
            CountDownLatch entered = this.clearEntered;
            if (entered != null) {
                this.clearEntered = null;
                entered.countDown();
                awaitGate(this.allowClear);
                this.allowClear = null;
            }
            this.listeners.clear();
            this.publishedListeners = List.of();
            this.invoker = null;
        }

        void blockNextRegister() {
            this.registerEntered = new CountDownLatch(1);
            this.allowRegister = new CountDownLatch(1);
        }

        void blockNextUnregister() {
            this.unregisterEntered = new CountDownLatch(1);
            this.allowUnregister = new CountDownLatch(1);
        }

        void blockNextClear() {
            this.clearEntered = new CountDownLatch(1);
            this.allowClear = new CountDownLatch(1);
        }

        boolean hasListener(Listener listener) {
            return this.publishedListeners.stream()
                .anyMatch(entry -> java.util.Objects.equals(entry.listener(), listener));
        }

        private static void awaitGate(CountDownLatch gate) {
            try {
                if (!gate.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("controlled delegate gate timed out");
                }
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("controlled delegate gate interrupted", interrupted);
            }
        }

        private record Entry(EventPriority priority, Listener listener) {}
    }
}
