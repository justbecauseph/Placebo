package dev.shadowsoffire.placebo.tabs;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adapts NeoForge's {@code BuildCreativeModeTabContentsEvent} onto {@link TabFillContext}.
 */
public record NeoForgeTabFillContext(BuildCreativeModeTabContentsEvent event) implements TabFillContext {

    @Override
    public ResourceKey<CreativeModeTab> tabKey() {
        return this.event.getTabKey();
    }

    @Override
    public HolderLookup.Provider registries() {
        return this.event.getParameters().holders();
    }

    @Override
    public void accept(ItemStack stack, TabVisibility visibility) {
        this.event.accept(stack, visibility);
    }

    /**
     * Entry point for the mod event bus; replaces the old {@code TabFillingRegistry::fillTabs} listener.
     */
    public static void fillTabs(BuildCreativeModeTabContentsEvent e) {
        TabFillingRegistry.fillTabs(e.getTab(), new NeoForgeTabFillContext(e));
    }

}
