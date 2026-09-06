package dev.shadowsoffire.placebo.events;

import java.util.Objects;

import net.minecraft.world.entity.LivingEntity;

/**
 * Fixed Fabric dispatch slots for the two owned LivingHeal consumers.
 *
 * <p>The result uses the same packed raw-float representation as {@link FabricDamageDispatcher}: bit zero is
 * cancellation and the remaining bits retain the exact float payload, including non-finite values and signed
 * zero. The direct path reads its mode once per heal, so a public listener cannot cause a partial mixed dispatch.
 */
public final class FabricHealDispatcher {

    @FunctionalInterface
    public interface Handler {
        long handle(LivingEntity entity, float amount);
    }

    private static volatile Handler attributes;
    private static volatile Handler enchanting;

    private FabricHealDispatcher() {}

    public static synchronized void registerAttributes(Handler handler) {
        Handler current = attributes;
        if (current != null && current != handler) throw new IllegalStateException("Attributes healing handler was registered twice");
        if (current == null) attributes = Objects.requireNonNull(handler);
    }

    public static synchronized void registerEnchanting(Handler handler) {
        Handler current = enchanting;
        if (current != null && current != handler) throw new IllegalStateException("Enchanting healing handler was registered twice");
        if (current == null) enchanting = Objects.requireNonNull(handler);
    }

    /** Returns whether the owned direct chain is still complete and may be used. */
    public static boolean useDirectPath() {
        return PlaceboEvents.livingHealDirectAllowed();
    }

    /** Runs the owned healing chain in public event order: Attributes HIGH, then Enchanting LOW. */
    public static long dispatch(LivingEntity entity, float amount) {
        long result = FabricDamageDispatcher.pack(amount, false);
        Handler handler = attributes;
        if (handler != null) {
            result = handler.handle(entity, FabricDamageDispatcher.unpackAmount(result));
            if (FabricDamageDispatcher.isCancelled(result)) return result;
        }

        handler = enchanting;
        if (handler != null) {
            result = handler.handle(entity, FabricDamageDispatcher.unpackAmount(result));
        }
        return result;
    }

    /** Selects direct or complete public fallback mode once, then returns the vanilla-facing amount. */
    public static float apply(LivingEntity entity, float amount) {
        if (!useDirectPath()) return PlaceboEvents.fireLivingHeal(entity, amount);
        long result = dispatch(entity, amount);
        return FabricDamageDispatcher.isCancelled(result) ? 0F : FabricDamageDispatcher.unpackAmount(result);
    }

    /** Packs an owned handler result while retaining the raw amount bits and cancellation separately. */
    public static long result(float amount, boolean cancelled) {
        return FabricDamageDispatcher.pack(amount, cancelled);
    }
}
