package dev.shadowsoffire.placebo.network;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Collects payload providers, loader-neutrally.
 * <p>
 * NeoForge registration is two-phase: mods collect providers during setup and its payload event later drains
 * them. Fabric initializes dependencies before dependents, so Placebo cannot drain during its own initializer;
 * its platform bridge installs an immediate registrar instead and every later provider is registered as it is
 * added. Duplicate checking remains shared.
 * <p>
 * Keeping this class and its signature is deliberate: every downstream mod calls
 * {@code PayloadHelper.registerPayload(...)}, and none of them needs to change.
 */
public class PayloadHelper {

    private static final Map<CustomPacketPayload.Type<?>, PayloadProvider<?>> ALL_PROVIDERS = new HashMap<>();
    private static Consumer<PayloadProvider<?>> immediateRegistrar;
    private static boolean locked = false;

    /**
     * Registers a payload using {@link PayloadProvider}.
     *
     * @param prov An instance of the payload provider.
     */
    public static <T extends CustomPacketPayload> void registerPayload(PayloadProvider<T> prov) {
        Preconditions.checkNotNull(prov);
        synchronized (ALL_PROVIDERS) {
            if (locked) {
                throw new UnsupportedOperationException("Attempted to register a payload provider after registration has finished.");
            }
            if (ALL_PROVIDERS.containsKey(prov.getType())) {
                throw new UnsupportedOperationException("Attempted to register payload provider with duplicate ID: " + prov.getType().id());
            }
            ALL_PROVIDERS.put(prov.getType(), prov);
            if (immediateRegistrar != null) {
                immediateRegistrar.accept(prov);
            }
        }
    }

    /**
     * Installs a platform registrar that receives existing providers and every provider registered later.
     * Fabric uses this because dependency entrypoints run before the mods that depend on them.
     */
    public static void setImmediateRegistrar(Consumer<PayloadProvider<?>> registrar) {
        Preconditions.checkNotNull(registrar);
        synchronized (ALL_PROVIDERS) {
            if (locked || immediateRegistrar != null) {
                throw new IllegalStateException("A payload registrar has already been installed or registration has finished.");
            }
            immediateRegistrar = registrar;
            ALL_PROVIDERS.values().forEach(registrar);
        }
    }

    /**
     * All registered providers, for the platform to flush into its network registry.
     * <p>
     * Calling this locks registration: anything registered afterwards would never reach the network layer, so
     * it throws rather than being silently dropped.
     */
    public static Collection<PayloadProvider<?>> drain() {
        synchronized (ALL_PROVIDERS) {
            if (immediateRegistrar != null) {
                throw new IllegalStateException("Payload providers are already being registered immediately.");
            }
            locked = true;
            return java.util.List.copyOf(ALL_PROVIDERS.values());
        }
    }

}
