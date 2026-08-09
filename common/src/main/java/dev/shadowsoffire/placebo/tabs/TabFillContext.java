package dev.shadowsoffire.placebo.tabs;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral replacement for NeoForge's {@code BuildCreativeModeTabContentsEvent}, as seen by an
 * {@link ITabFiller}.
 * <p>
 * Sized from what fillers across the stack actually use: {@link #accept}, the registry lookup, and the tab
 * key. Fabric's {@code ItemGroupEvents} exposes an entries collector rather than an event object, so this
 * interface is what lets one filler implementation serve both.
 */
public interface TabFillContext {

    /**
     * The tab being filled.
     */
    ResourceKey<CreativeModeTab> tabKey();

    /**
     * Registry lookup for the datapack contents in effect. Fillers that resolve holders -- enchantments, for
     * instance -- need this, and it is not reachable from a static context during tab building.
     */
    HolderLookup.Provider registries();

    /**
     * Adds a stack to the tab.
     */
    void accept(ItemStack stack, TabVisibility visibility);

    /**
     * Adds a stack to both the parent tab and search.
     */
    default void accept(ItemStack stack) {
        this.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS);
    }

}
