package dev.shadowsoffire.placebo.loot;

import java.util.List;
import java.util.function.Predicate;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * A modifier applied to loot after a table has rolled, loader-neutrally.
 * <p>
 * This is NeoForge's {@code LootModifier} with the same shape, and it is here for the same reason
 * {@code AttributeTooltipContext} is: <b>every type it carries is vanilla</b>. NeoForge's version is NeoForge's
 * only by where it lives, so reproducing it costs nothing and each platform adapts the registration rather than
 * the model.
 * <p>
 * The two platforms reach it very differently, which is worth knowing before extending this:
 * <ul>
 * <li><b>NeoForge</b> has a whole subsystem — a registry of serializers, a datapack loader, an ordering pass —
 * and Placebo's modifiers are wrapped into it. Nothing here changes on that loader.
 * <li><b>Fabric</b> has none of it. Placebo loads the same JSON itself and runs the result on
 * {@code LootTableEvents.MODIFY_DROPS}, which is the one place a drop-time hook with a {@link LootContext}
 * exists. See {@code FabricLootModifiers}.
 * </ul>
 */
public abstract class LootModifier {

    /** NeoForge's default; kept identical so shared JSON sorts the same on both loaders. */
    public static final int DEFAULT_PRIORITY = 1000;

    public static final Codec<LootItemCondition[]> LOOT_CONDITIONS_CODEC =
        LootItemCondition.DIRECT_CODEC.listOf().xmap(list -> list.toArray(LootItemCondition[]::new), List::of);

    protected final LootItemCondition[] conditions;
    protected final int priority;
    private final Predicate<LootContext> combinedConditions;

    protected LootModifier(LootItemCondition[] conditions, int priority) {
        this.conditions = conditions;
        this.combinedConditions = AllOfCondition.allOf(List.of(conditions));
        this.priority = priority;
    }

    /**
     * The first two fields of every modifier's codec, so subclasses can {@code .and(...)} their own onto it.
     */
    protected static <T extends LootModifier> Products.P2<RecordCodecBuilder.Mu<T>, LootItemCondition[], Integer> codecStart(RecordCodecBuilder.Instance<T> instance) {
        return instance.group(
            LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(lm -> lm.conditions),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(lm -> lm.priority));
    }

    /**
     * Applies this modifier if its conditions match. Called by the platform, not by mods.
     */
    public final ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        return this.combinedConditions.test(context) ? this.doApply(generatedLoot, context) : generatedLoot;
    }

    public int priority() {
        return this.priority;
    }

    /**
     * The codec this modifier was registered with, used to dispatch on the {@code type} field.
     */
    public abstract MapCodec<? extends LootModifier> codec();

    protected abstract ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context);

}
