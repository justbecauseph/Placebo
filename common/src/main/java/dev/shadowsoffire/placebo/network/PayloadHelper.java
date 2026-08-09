package dev.shadowsoffire.placebo.network;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Collects payload providers, loader-neutrally.
 * <p>
 * Registration is two-phase and always was: mods call {@link #registerPayload} during setup, and the platform
 * later flushes everything into its own network registry. Only that flush is loader-specific, so it lives in
 * {@code NeoForgePayloadRegistrar} / {@code FabricPayloadRegistrar}; the bookkeeping and the duplicate/late
 * registration checks are shared.
 * <p>
 * Keeping this class and its signature is deliberate: every downstream mod calls
 * {@code PayloadHelper.registerPayload(...)}, and none of them needs to change.
 */
public class PayloadHelper {

    private static final Map<CustomPacketPayload.Type<?>, PayloadProvider<?>> ALL_PROVIDERS = new HashMap<>();
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
            locked = true;
            return java.util.List.copyOf(ALL_PROVIDERS.values());
        }
    }

}
