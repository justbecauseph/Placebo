package dev.shadowsoffire.placebo.events;

import java.util.Objects;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Fixed Fabric-only gameplay-enchantment hooks used by the Apotheosis stack.
 *
 * <p>This is intentionally a pair of owned slots, not a general event bus.  The
 * dispatcher always invokes the Apotheosis slot first and the Apothic Enchanting
 * slot second, so loader/mod initialization order cannot change the result.</p>
 *
 * <p>The single-enchantment path stays primitive.  The iteration paths receive
 * the map that vanilla already read and copy it only when a registered consumer
 * can change it.  In particular, a nullable slot-iteration read remains null on
 * a bare stack.</p>
 */
public final class EnchantmentLevelHooks {

    private static Handler apotheosis;
    private static Handler apothicEnchanting;

    private EnchantmentLevelHooks() {}

    /** Registers Apotheosis's fixed, first-priority slot. */
    public static void registerApotheosis(Handler handler) {
        register("Apotheosis", handler, true);
    }

    /** Registers Apothic Enchanting's fixed, second-priority slot. */
    public static void registerApothicEnchanting(Handler handler) {
        register("Apothic Enchanting", handler, false);
    }

    private static void register(String name, Handler handler, boolean first) {
        Objects.requireNonNull(handler, name + " enchantment-level handler");
        Handler current = first ? apotheosis : apothicEnchanting;
        if (current != null && current != handler) {
            throw new IllegalStateException(name + " enchantment-level handler was registered twice");
        }
        if (first) {
            apotheosis = handler;
        }
        else {
            apothicEnchanting = handler;
        }
    }

    /**
     * Applies the fixed handler order to a single gameplay lookup.
     *
     * <p>{@link ItemEnchantments.Mutable#set} is the reference event path for
     * this lookup and treats absent/non-positive values as zero and caps values
     * at 255.  Normalize the vanilla result the same way before passing it to
     * the consumers.</p>
     */
    public static int modifySingle(ItemInstance stack, Holder<Enchantment> enchantment, int level) {
        int effective = level > 0 ? Math.min(255, level) : 0;

        Handler apoth = apotheosis;
        if (apoth != null && apoth.isRelevant(stack)) {
            effective = apoth.modifySingle(stack, enchantment, effective);
        }

        Handler clamp = apothicEnchanting;
        if (clamp != null && clamp.isRelevant(stack)) {
            effective = clamp.modifySingle(stack, enchantment, effective);
        }

        return effective;
    }

    /**
     * Applies the fixed handler order to a whole gameplay enchantment map.
     *
     * <p>A null map is the actual result of vanilla's slot overload when the
     * component is absent.  A handler may turn that into a map only when its
     * map-aware relevance predicate says it has work to do.</p>
     */
    public static ItemEnchantments modifyAll(ItemInstance stack, ItemEnchantments enchantments) {
        Handler apoth = apotheosis;
        boolean applyApotheosis = apoth != null && apoth.isRelevant(stack, enchantments);

        Handler clamp = apothicEnchanting;
        // Apotheosis may raise an in-range level above a strict cap, so the
        // clamp must still run after an Apotheosis contribution.
        boolean applyClamp = clamp != null && (applyApotheosis || clamp.isRelevant(stack, enchantments));

        if (!applyApotheosis && !applyClamp) {
            return enchantments;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
            enchantments == null ? ItemEnchantments.EMPTY : enchantments);
        if (applyApotheosis) {
            apoth.modifyAll(stack, mutable);
        }
        if (applyClamp) {
            clamp.modifyAll(stack, mutable);
        }
        return mutable.toImmutable();
    }

    /** A fixed consumer slot for the four vanilla gameplay-enchantment seams. */
    public interface Handler {

        /** Cheap predicate for the primitive single-level path. */
        boolean isRelevant(ItemInstance stack);

        /** Cheap predicate for copy-on-write map paths; the map may be null. */
        default boolean isRelevant(ItemInstance stack, ItemEnchantments enchantments) {
            return isRelevant(stack);
        }

        /** Changes one queried level without creating an enchantment map. */
        default int modifySingle(ItemInstance stack, Holder<Enchantment> enchantment, int level) {
            return level;
        }

        /** Changes the mutable map supplied by the dispatcher. */
        void modifyAll(ItemInstance stack, ItemEnchantments.Mutable enchantments);
    }
}
