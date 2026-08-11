package dev.shadowsoffire.placebo.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** A loader-neutral ingredient whose match also requires a minimum stack size. */
public record SizedIngredient(Ingredient ingredient, int count) {

    public static final Codec<SizedIngredient> NESTED_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Ingredient.CODEC.fieldOf("ingredient").forGetter(SizedIngredient::ingredient),
        ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(SizedIngredient::count))
        .apply(inst, SizedIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SizedIngredient> STREAM_CODEC = StreamCodec.composite(
        Ingredient.CONTENTS_STREAM_CODEC, SizedIngredient::ingredient,
        ByteBufCodecs.VAR_INT, SizedIngredient::count,
        SizedIngredient::new);

    public SizedIngredient {
        if (count <= 0) throw new IllegalArgumentException("Size must be positive");
    }

    public static SizedIngredient of(ItemLike item, int count) {
        return new SizedIngredient(Ingredient.of(item), count);
    }

    public boolean test(ItemStack stack) {
        return this.ingredient.test(stack) && stack.getCount() >= this.count;
    }
}
