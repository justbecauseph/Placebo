package dev.shadowsoffire.placebo.tabs;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;

/**
 * Adapts Fabric's creative tab output onto {@link TabFillContext}.
 * <p>
 * Note the module is {@code fabric-creative-tab-api-v1} at 26.2, not the older {@code fabric-item-group-api-v1}.
 * <p>
 * {@code MODIFY_OUTPUT_ALL} hands over the tab <em>instance</em> rather than its key, so the key is resolved
 * through {@code BuiltInRegistries.CREATIVE_MODE_TAB}. Using one global listener rather than
 * {@code modifyOutputEvent(key)} per tab keeps registration order irrelevant: fillers can be registered at
 * any point without needing a listener to already exist for their tab.
 */
public record FabricTabFillContext(ResourceKey<CreativeModeTab> tabKey, FabricCreativeModeTabOutput output) implements TabFillContext {

    @Override
    public HolderLookup.Provider registries() {
        return this.output.getContext().holders();
    }

    @Override
    public void accept(ItemStack stack, TabVisibility visibility) {
        this.output.accept(stack, visibility);
    }

    public static void install() {
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
            BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab)
                .ifPresent(key -> TabFillingRegistry.fillTabs(tab, new FabricTabFillContext(key, output)));
        });
    }

}
