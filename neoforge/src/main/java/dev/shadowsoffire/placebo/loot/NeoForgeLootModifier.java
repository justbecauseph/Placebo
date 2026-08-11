package dev.shadowsoffire.placebo.loot;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Presents a Placebo {@link LootModifier} to NeoForge's global loot modifier system.
 * <p>
 * NeoForge's subsystem does all the real work on that loader -- loading the JSON, ordering by priority,
 * applying to drops. It only insists that the registered object implement {@link IGlobalLootModifier}, which
 * is a type {@code :common} cannot name. So the codec registered into NeoForge's serializer registry decodes
 * a Placebo modifier and wraps it in one of these; everything downstream of that is NeoForge's own.
 * <p>
 * The wrapper's {@link #codec()} returns the wrapping codec rather than the inner one, because that is what
 * NeoForge dispatches on when re-encoding.
 */
public record NeoForgeLootModifier(LootModifier wrapped, MapCodec<NeoForgeLootModifier> codec) implements IGlobalLootModifier {

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        return this.wrapped.apply(generatedLoot, context);
    }

    @Override
    public int priority() {
        return this.wrapped.priority();
    }

    /**
     * Wraps {@code codec} so NeoForge can register it. The result is self-referential -- each decoded wrapper
     * has to know the codec that produced it -- which is why this is a method rather than an xmap at the call
     * site.
     */
    /**
     * Wrapper codec by the inner codec it wraps, so datagen can present a Placebo modifier to NeoForge's
     * provider without knowing which wrapper was built for it.
     */
    private static final Map<MapCodec<?>, MapCodec<NeoForgeLootModifier>> WRAPPERS = new IdentityHashMap<>();

    /**
     * Wraps a modifier instance for NeoForge's datagen provider, which takes {@link IGlobalLootModifier}s.
     */
    public static NeoForgeLootModifier of(LootModifier modifier) {
        MapCodec<NeoForgeLootModifier> wrapper = WRAPPERS.get(modifier.codec());
        if (wrapper == null) {
            throw new IllegalStateException("Loot modifier " + modifier.getClass().getName()
                + " was never registered through DeferredHelper#lootModifier, so it has no NeoForge codec.");
        }
        return new NeoForgeLootModifier(modifier, wrapper);
    }

    @SuppressWarnings("unchecked")
    public static <T extends LootModifier> MapCodec<NeoForgeLootModifier> wrap(MapCodec<T> codec) {
        MapCodec<NeoForgeLootModifier>[] holder = new MapCodec[1];
        // The downcast on the encode side is safe by construction: a wrapper only ever holds the modifier
        // this very codec decoded.
        holder[0] = codec.xmap(inner -> new NeoForgeLootModifier(inner, holder[0]), wrapper -> (T) wrapper.wrapped());
        WRAPPERS.put(codec, holder[0]);
        return holder[0];
    }

}
