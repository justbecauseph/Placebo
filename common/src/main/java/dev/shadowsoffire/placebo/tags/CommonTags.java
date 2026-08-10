package dev.shadowsoffire.placebo.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * The {@code c:} tags this stack uses, as plain vanilla {@link TagKey}s.
 * <p>
 * Both loaders ship a conventional-tag vocabulary -- NeoForge's {@code Tags}, Fabric's
 * {@code ConventionalItemTags} and friends -- and the whole point of the convention is that the two agree.
 * Checked, for every entry below: the paths are identical, only the Java constant names differ
 * ({@code Tags.Items.GEMS_DIAMOND} against {@code ConventionalItemTags.DIAMOND_GEMS}). So neither class is
 * needed to name one; a {@code TagKey} under the {@code c} namespace is the tag, on both loaders.
 * <p>
 * That makes this a vocabulary, not a seam -- there is nothing to dispatch and no platform half. It exists
 * because referring to a shared tag through one loader's holder class was quietly making 15 files
 * platform-bound.
 * <p>
 * Sized from callers: only tags this stack actually uses are here. Anything genuinely NeoForge-only stays
 * out -- {@code neoforge:enchanting_fuels} is the one such tag in the stack, and it has no counterpart to
 * be neutral about.
 */
public class CommonTags {

    public static class Items {

        public static final TagKey<Item> BONES = tag("bones");
        public static final TagKey<Item> BOOKSHELVES = tag("bookshelves");
        public static final TagKey<Item> ENDER_PEARLS = tag("ender_pearls");
        public static final TagKey<Item> GEMS_DIAMOND = tag("gems/diamond");
        public static final TagKey<Item> GEMS_QUARTZ = tag("gems/quartz");
        public static final TagKey<Item> GLASS_BLOCKS = tag("glass_blocks");
        public static final TagKey<Item> INGOTS_COPPER = tag("ingots/copper");
        public static final TagKey<Item> INGOTS_GOLD = tag("ingots/gold");
        public static final TagKey<Item> INGOTS_IRON = tag("ingots/iron");
        public static final TagKey<Item> INGOTS_NETHERITE = tag("ingots/netherite");
        public static final TagKey<Item> RAW_MATERIALS_COPPER = tag("raw_materials/copper");
        public static final TagKey<Item> STORAGE_BLOCKS_EMERALD = tag("storage_blocks/emerald");
        public static final TagKey<Item> STORAGE_BLOCKS_GOLD = tag("storage_blocks/gold");
        public static final TagKey<Item> STORAGE_BLOCKS_IRON = tag("storage_blocks/iron");
        public static final TagKey<Item> STRINGS = tag("strings");
        public static final TagKey<Item> TOOLS_SHEAR = tag("tools/shear");
        public static final TagKey<Item> TOOLS_SHIELD = tag("tools/shield");

        private static TagKey<Item> tag(String path) {
            return CommonTags.tag(Registries.ITEM, path);
        }
    }

    public static class Blocks {

        public static final TagKey<Block> OBSIDIANS = tag("obsidians");
        public static final TagKey<Block> ORES_COPPER = tag("ores/copper");

        private static TagKey<Block> tag(String path) {
            return CommonTags.tag(Registries.BLOCK, path);
        }
    }

    public static class Biomes {

        public static final TagKey<Biome> IS_COLD_OVERWORLD = tag("is_cold/overworld");
        public static final TagKey<Biome> IS_DRY_OVERWORLD = tag("is_dry/overworld");
        public static final TagKey<Biome> IS_SNOWY = tag("is_snowy");
        public static final TagKey<Biome> IS_WET_OVERWORLD = tag("is_wet/overworld");

        private static TagKey<Biome> tag(String path) {
            return CommonTags.tag(Registries.BIOME, path);
        }
    }

    public static class EntityTypes {

        public static final TagKey<EntityType<?>> BOSSES = tag("bosses");
        public static final TagKey<EntityType<?>> CAPTURING_NOT_SUPPORTED = tag("capturing_not_supported");

        private static TagKey<EntityType<?>> tag(String path) {
            return CommonTags.tag(Registries.ENTITY_TYPE, path);
        }
    }

    private static <T> TagKey<T> tag(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String path) {
        return TagKey.create(registry, Identifier.fromNamespaceAndPath("c", path));
    }

}
