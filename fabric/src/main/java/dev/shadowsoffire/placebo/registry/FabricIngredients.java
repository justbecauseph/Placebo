package dev.shadowsoffire.placebo.registry;

import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.crafting.CustomIngredient;
import dev.shadowsoffire.placebo.crafting.IngredientType;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * {@link IngredientType.Impl} over {@code fabric-recipe-api-v1}'s custom ingredients.
 * <p>
 * Mirrors {@code NeoForgeIngredients}, with two differences worth knowing. Fabric's serializers are not
 * registry entries, so registration is immediate and the declaring helper is unused. And Fabric asks
 * {@code requiresTesting()} where NeoForge asks {@code isSimple()} -- the same question, inverted.
 */
public class FabricIngredients implements IngredientType.Impl {

    /** Ours to Fabric's, so a wrapped ingredient can report the serializer Fabric's dispatch expects. */
    private static final Map<IngredientType<?>, CustomIngredientSerializer<?>> SERIALIZERS =
        new java.util.IdentityHashMap<>();

    @Override
    public <T extends CustomIngredient> void register(DeferredHelper owner, Identifier id, IngredientType<T> type) {
        Serializer serializer = new Serializer(id, type);
        SERIALIZERS.put(type, serializer);
        CustomIngredientSerializer.register(serializer);
    }

    @Override
    public Ingredient toVanilla(CustomIngredient ingredient) {
        return new Wrapped(ingredient).toVanilla();
    }

    /**
     * Presents a Placebo ingredient as one of Fabric's. A record so equality follows the delegate, which
     * matters because vanilla compares ingredients when deduplicating recipes.
     */
    public record Wrapped(CustomIngredient delegate)
        implements net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient {

        @Override
        public boolean test(ItemStack stack) {
            return this.delegate.test(stack);
        }

        @Override
        public Stream<Holder<Item>> items() {
            return this.delegate.items();
        }

        @Override
        public boolean requiresTesting() {
            // The negation of NeoForge's isSimple(): both mean "is membership in items() the whole story".
            return !this.delegate.isSimple();
        }

        @Override
        public SlotDisplay display() {
            return this.delegate.display();
        }

        @Override
        public CustomIngredientSerializer<?> getSerializer() {
            var serializer = SERIALIZERS.get(this.delegate.getType());
            if (serializer == null) {
                throw new IllegalStateException("Ingredient " + this.delegate.getClass().getName()
                    + " reports a type that was never registered through DeferredHelper#ingredient.");
            }
            return serializer;
        }
    }

    /**
     * Adapts one {@link IngredientType} to Fabric's serializer interface. The codecs are the Placebo ones
     * with {@link Wrapped} mapped over them, so the JSON shape is whatever the declaring mod wrote.
     */
    private record Serializer(Identifier id, IngredientType<? extends CustomIngredient> type)
        implements CustomIngredientSerializer<Wrapped> {

        @Override
        public Identifier getIdentifier() {
            return this.id;
        }

        // Raw casts because the wildcard on `type` cannot be captured into the Wrapped mapping. The
        // lambdas are written out rather than as `Wrapped::delegate` method references: through a raw
        // codec the target type is Object, and a zero-argument getter does not fit Function<Object, ?>.
        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public MapCodec<Wrapped> getCodec() {
            return ((MapCodec) this.type.codec())
                .xmap(i -> new Wrapped((CustomIngredient) i), w -> ((Wrapped) w).delegate());
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public StreamCodec<RegistryFriendlyByteBuf, Wrapped> getStreamCodec() {
            return ((StreamCodec) this.type.streamCodec())
                .map(i -> new Wrapped((CustomIngredient) i), w -> ((Wrapped) w).delegate());
        }
    }

}
