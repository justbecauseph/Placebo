package dev.shadowsoffire.placebo.events;

import java.util.Objects;

import dev.architectury.event.EventPriority;
import dev.shadowsoffire.placebo.events.PlaceboEvents.LivingDamagePostContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fixed Fabric dispatch slots for the incoming- and post-mitigation-damage paths.
 * <p>
 * The slots are deliberately explicit rather than a second event bus: their order is part of the gameplay
 * contract, and the normal path must not sort listeners, inspect mods, or allocate a mutable event context.
 * Incoming slots return a primitive packed {@code long}: bits 1 through 32 hold the raw float bits and bit zero
 * is cancellation (the upper 31 bits remain unused). This preserves negative values and non-finite payloads
 * even when a handler leaves the amount unchanged.
 * <p>
 * Registration is a startup operation. Volatile fields publish the handlers to the damage mixin, and repeated
 * registration of a slot is idempotent (the first owner remains authoritative).
 */
public final class FabricDamageDispatcher {

    private static final long CANCEL_MASK = 1L;

    @FunctionalInterface
    public interface Handler {
        long handle(LivingEntity entity, DamageSource source, float amount);
    }

    /** The one owned PRE consumer returns a nonnegative pre-hit health, or {@code -1} when it does not apply. */
    @FunctionalInterface
    public interface PreHandler {
        float handle(LivingEntity entity, DamageSource source, float originalDamage, float mitigatedDamage);
    }

    /** Fixed POST consumer shape; all values are captured before the invocation enters this method. */
    @FunctionalInterface
    public interface PostHandler {
        void handle(LivingEntity target, DamageSource source, float originalDamage, float inflictedDamage,
            float healthDamage, float preHealth);
    }

    private static volatile Handler attributesProjectile;
    private static volatile Handler attributesCritical;
    private static volatile Handler attributesDodge;
    private static volatile Handler gateways;
    private static volatile Handler apotheosis;
    private static volatile Handler attributesMelee;
    private static volatile PreHandler attributesPre;
    private static volatile PostHandler attributesPost;
    private static volatile PostHandler apotheosisPost;
    private static volatile PostHandler enchantingPost;

    private static final PlaceboEvents.LivingDamagePost POST_FALLBACK = FabricDamageDispatcher::dispatchPostFallback;
    private static boolean postFallbackInstalled;

    private FabricDamageDispatcher() {}

    public static synchronized void registerAttributesProjectile(Handler handler) {
        Handler current = attributesProjectile;
        if (current != null && current != handler) throw new IllegalStateException("Attributes projectile handler was registered twice");
        if (current == null) attributesProjectile = Objects.requireNonNull(handler);
    }

    public static synchronized void registerAttributesCritical(Handler handler) {
        Handler current = attributesCritical;
        if (current != null && current != handler) throw new IllegalStateException("Attributes critical handler was registered twice");
        if (current == null) attributesCritical = Objects.requireNonNull(handler);
    }

    public static synchronized void registerAttributesDodge(Handler handler) {
        Handler current = attributesDodge;
        if (current != null && current != handler) throw new IllegalStateException("Attributes dodge handler was registered twice");
        if (current == null) attributesDodge = Objects.requireNonNull(handler);
    }

    public static synchronized void registerGateways(Handler handler) {
        Handler current = gateways;
        if (current != null && current != handler) throw new IllegalStateException("Gateways handler was registered twice");
        if (current == null) gateways = Objects.requireNonNull(handler);
    }

    public static synchronized void registerApotheosis(Handler handler) {
        Handler current = apotheosis;
        if (current != null && current != handler) throw new IllegalStateException("Apotheosis handler was registered twice");
        if (current == null) apotheosis = Objects.requireNonNull(handler);
    }

    public static synchronized void registerAttributesMelee(Handler handler) {
        Handler current = attributesMelee;
        if (current != null && current != handler) throw new IllegalStateException("Attributes melee handler was registered twice");
        if (current == null) attributesMelee = Objects.requireNonNull(handler);
    }

    public static synchronized void registerAttributesPre(PreHandler handler) {
        PreHandler current = attributesPre;
        if (current != null && current != handler) throw new IllegalStateException("Attributes PRE handler was registered twice");
        if (current == null) attributesPre = Objects.requireNonNull(handler);
    }

    public static synchronized void registerAttributesPost(PostHandler handler) {
        PostHandler current = attributesPost;
        if (current != null && current != handler) throw new IllegalStateException("Attributes POST handler was registered twice");
        if (current == null) attributesPost = Objects.requireNonNull(handler);
    }

    public static synchronized void registerApotheosisPost(PostHandler handler) {
        PostHandler current = apotheosisPost;
        if (current != null && current != handler) throw new IllegalStateException("Apotheosis POST handler was registered twice");
        if (current == null) apotheosisPost = Objects.requireNonNull(handler);
    }

    public static synchronized void registerEnchantingPost(PostHandler handler) {
        PostHandler current = enchantingPost;
        if (current != null && current != handler) throw new IllegalStateException("Enchanting POST handler was registered twice");
        if (current == null) enchantingPost = Objects.requireNonNull(handler);
    }

    /**
     * Installs Placebo's single internal POST fallback adapter. It remains at NORMAL so external priorities
     * can surround the complete owned chain, while this method's fixed slots preserve owned order within it.
     */
    public static synchronized void installLivingDamagePostFallback() {
        if (postFallbackInstalled) return;
        PlaceboEvents.registerLivingDamagePostInternal(EventPriority.NORMAL, POST_FALLBACK);
        postFallbackInstalled = true;
    }

    /** Returns whether the public event is still in its owned, direct-dispatch mode. */
    public static boolean useDirectPath() {
        return PlaceboEvents.incomingDamageDirectAllowed();
    }

    /**
     * Runs the fixed slots in the exact public-event order. The caller must check {@link #useDirectPath()}
     * first; when it is false it must construct the public {@code IncomingDamageContext} instead.
     */
    public static long dispatch(LivingEntity entity, DamageSource source, float amount) {
        Handler handler = attributesProjectile;
        long result = pack(amount, false);
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
            if (isCancelled(result)) return result;
        }

        handler = attributesCritical;
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
            if (isCancelled(result)) return result;
        }

        handler = attributesDodge;
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
            if (isCancelled(result)) return result;
        }

        handler = gateways;
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
            if (isCancelled(result)) return result;
        }

        handler = apotheosis;
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
            if (isCancelled(result)) return result;
        }

        handler = attributesMelee;
        if (handler != null) {
            result = handler.handle(entity, source, unpackAmount(result));
        }
        return result;
    }

    /** Runs the one owned PRE slot and returns its primitive pre-hit-health snapshot. */
    public static float dispatchPre(LivingEntity entity, DamageSource source, float originalDamage, float mitigatedDamage) {
        PreHandler handler = attributesPre;
        return handler == null ? -1F : handler.handle(entity, source, originalDamage, mitigatedDamage);
    }

    /**
     * Runs fixed owned POST slots in their gameplay order: Attributes, Apotheosis, then Apothic Enchanting.
     * The caller is responsible for selecting direct versus public fallback mode and for skipping zero health
     * damage. No event context is created here.
     */
    public static void dispatchPost(LivingEntity target, DamageSource source, float originalDamage, float inflictedDamage,
        float healthDamage, float preHealth) {
        PostHandler handler = attributesPost;
        if (handler != null) handler.handle(target, source, originalDamage, inflictedDamage, healthDamage, preHealth);

        handler = apotheosisPost;
        if (handler != null) handler.handle(target, source, originalDamage, inflictedDamage, healthDamage, preHealth);

        handler = enchantingPost;
        if (handler != null) handler.handle(target, source, originalDamage, inflictedDamage, healthDamage, preHealth);
    }

    private static void dispatchPostFallback(LivingDamagePostContext context) {
        dispatchPost(context.getEntity(), context.getSource(), context.getOriginalDamage(), context.getInflictedDamage(),
            context.getHealthDamage(), context.getPreDamageHealth());
    }

    /** Packs a raw amount and cancellation bit without canonicalizing NaN payloads. */
    public static long pack(float amount, boolean cancelled) {
        long bits = ((long) Float.floatToRawIntBits(amount) & 0xFFFF_FFFFL) << 1;
        return bits | (cancelled ? CANCEL_MASK : 0L);
    }

    public static float unpackAmount(long packed) {
        return Float.intBitsToFloat((int) ((packed >>> 1) & 0xFFFF_FFFFL));
    }

    public static boolean isCancelled(long packed) {
        return (packed & CANCEL_MASK) != 0L;
    }

}
