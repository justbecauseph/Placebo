package dev.shadowsoffire.placebo.registry;

import java.util.Map;
import java.util.stream.Stream;

import dev.shadowsoffire.placebo.crafting.CustomIngredient;
import dev.shadowsoffire.placebo.crafting.IngredientType;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@link IngredientType.Impl} over NeoForge's {@code ICustomIngredient}.
 * <p>
 * The two interfaces line up almost exactly, so this is a wrapper rather than a translation. The only real
 * work is that NeoForge's ingredient types are entries in one of its registries, so they have to be staged
 * on the declaring helper and flushed with everything else.
 */
public class NeoForgeIngredients implements IngredientType.Impl {

    /**
     * Ours to NeoForge's, so a wrapped ingredient can report the type NeoForge's dispatch expects.
     * Identity-keyed because {@link IngredientType} is a record over two codecs, and codec equality is not
     * something to rely on.
     */
    private static final Map<IngredientType<?>, net.neoforged.neoforge.common.crafting.IngredientType<?>> TYPES =
        new java.util.IdentityHashMap<>();

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends CustomIngredient> void register(DeferredHelper owner, Identifier id, IngredientType<T> type) {
        var neoType = new net.neoforged.neoforge.common.crafting.IngredientType<Wrapped>(
            type.codec().xmap(Wrapped::new, w -> (T) w.delegate()),
            type.streamCodec().map(Wrapped::new, w -> (T) w.delegate()));
        TYPES.put(type, neoType);
        // Staged on the declaring helper, not on a helper of our own: NeoForge flushes each helper's
        // registrations from that helper's own event listener.
        owner.register(id.getPath(), NeoForgeRegistries.Keys.INGREDIENT_TYPES, () -> neoType);
    }

    @Override
    public Ingredient toVanilla(CustomIngredient ingredient) {
        return new Wrapped(ingredient).toVanilla();
    }

    /**
     * Presents a Placebo ingredient as one of NeoForge's. A record so equality follows the delegate, which
     * matters because vanilla compares ingredients when deduplicating recipes.
     */
    public record Wrapped(CustomIngredient delegate) implements ICustomIngredient {

        @Override
        public boolean test(ItemStack stack) {
            return this.delegate.test(stack);
        }

        @Override
        public Stream<Holder<Item>> items() {
            return this.delegate.items();
        }

        @Override
        public boolean isSimple() {
            return this.delegate.isSimple();
        }

        @Override
        public SlotDisplay display() {
            return this.delegate.display();
        }

        @Override
        public net.neoforged.neoforge.common.crafting.IngredientType<?> getType() {
            var type = TYPES.get(this.delegate.getType());
            if (type == null) {
                throw new IllegalStateException("Ingredient " + this.delegate.getClass().getName()
                    + " reports a type that was never registered through DeferredHelper#ingredient.");
            }
            return type;
        }
    }

}
