package dev.shadowsoffire.placebo.crafting;

import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * An ingredient that matches on more than a set of items.
 * <p>
 * Vanilla's {@link Ingredient} is a {@code HolderSet<Item>} and nothing else, so both loaders extend it the
 * same way -- a dispatched custom codec alongside the vanilla one -- and both arrive at almost the same
 * interface. The only differences are the name of the type token and one inverted boolean:
 *
 * <table>
 * <tr><th>NeoForge {@code ICustomIngredient}</th><th>Fabric {@code CustomIngredient}</th></tr>
 * <tr><td>{@code test(ItemStack)}</td><td>identical</td></tr>
 * <tr><td>{@code items()}</td><td>identical</td></tr>
 * <tr><td>{@code display()}</td><td>identical, same default</td></tr>
 * <tr><td>{@code isSimple()}</td><td>{@code requiresTesting()} -- the negation</td></tr>
 * <tr><td>{@code getType()} to an {@code IngredientType}</td><td>{@code getSerializer()} to a
 * {@code CustomIngredientSerializer}</td></tr>
 * </table>
 *
 * So this is not an abstraction over two different designs, it is the one design both loaders already have,
 * with a name picked for it. Implementations here are plain common code; the platform wraps them.
 * <p>
 * {@link #toVanilla()} is the only part that cannot be common: each loader's wrapper type is the one its
 * patched {@code Ingredient.CODEC} knows how to recognise.
 */
public interface CustomIngredient {

    /**
     * Whether {@code stack} matches. Called for every stack when {@link #isSimple()} is false.
     */
    boolean test(ItemStack stack);

    /**
     * Every item this ingredient might accept -- used for display, and for recipe-book lookups.
     * <p>
     * Must not be empty: an empty ingredient invalidates the whole recipe on both loaders.
     */
    Stream<Holder<Item>> items();

    /**
     * Whether membership in {@link #items()} is the whole story. False when matching also depends on the
     * stack's components or on external state, which is the case for everything in this stack.
     * <p>
     * Fabric spells this {@code requiresTesting()}, which is the negation; the platform adapter flips it.
     */
    boolean isSimple();

    /**
     * The token this ingredient's codec was registered under, via
     * {@link dev.shadowsoffire.placebo.registry.DeferredHelper#ingredient}.
     */
    IngredientType<?> getType();

    /**
     * How this ingredient is drawn in a recipe slot. The default matches both loaders' -- and vanilla's
     * {@code Ingredient.display()} -- so overriding is only for a custom {@link SlotDisplay}.
     */
    default SlotDisplay display() {
        return new SlotDisplay.Composite(this.items().map(Ingredient::displayForSingleItem).toList());
    }

    /**
     * Wraps this into a vanilla {@link Ingredient} that the running loader's recipe codecs will accept.
     */
    default Ingredient toVanilla() {
        return IngredientType.toVanilla(this);
    }

}
