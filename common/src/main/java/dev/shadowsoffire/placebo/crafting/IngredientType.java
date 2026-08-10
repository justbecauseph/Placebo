package dev.shadowsoffire.placebo.crafting;

import java.util.Objects;

import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.registry.DeferredHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The codecs for one kind of {@link CustomIngredient}, and the token its instances report from
 * {@link CustomIngredient#getType()}.
 * <p>
 * Register with {@link dev.shadowsoffire.placebo.registry.DeferredHelper#ingredient}, which is where the two
 * loaders diverge: NeoForge's ingredient types are entries in one of its own registries and have to be
 * staged like any other registration, while Fabric's serializers are registered directly and immediately.
 * <p>
 * The stream codec is invariant in its buffer type on Fabric's side, so a codec written against a wider
 * buffer -- {@code StreamCodec<ByteBuf, T>}, which several of these are -- needs {@code .cast()} to fit.
 */
public record IngredientType<T extends CustomIngredient>(MapCodec<T> codec,
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {

    private static Impl impl;

    /**
     * Installed by the platform entrypoint, before any ingredient type is declared.
     */
    public static void setImpl(Impl impl) {
        IngredientType.impl = Objects.requireNonNull(impl);
    }

    private static Impl impl() {
        if (impl == null) {
            throw new IllegalStateException("No IngredientType implementation has been installed. "
                + "The platform entrypoint must call IngredientType.setImpl before any ingredient is declared.");
        }
        return impl;
    }

    /**
     * Called by {@link DeferredHelper#ingredient}; go through that rather than here.
     */
    public static <T extends CustomIngredient> void register(DeferredHelper owner, Identifier id,
        IngredientType<T> type) {
        impl().register(owner, id, type);
    }

    static Ingredient toVanilla(CustomIngredient ingredient) {
        return impl().toVanilla(ingredient);
    }

    public interface Impl {

        /**
         * Makes {@code type} known to the loader's ingredient dispatch under {@code id}.
         *
         * @param owner The helper the declaration came from. NeoForge's ingredient types are registry
         *              entries and have to be staged on that helper -- staging them anywhere else means
         *              they are flushed by the wrong mod's registration event, which compiles and
         *              type-checks perfectly and then never registers.
         */
        <T extends CustomIngredient> void register(DeferredHelper owner, Identifier id, IngredientType<T> type);

        /**
         * Wraps {@code ingredient} in whichever type the loader's patched {@code Ingredient.CODEC} accepts.
         */
        Ingredient toVanilla(CustomIngredient ingredient);
    }

}
