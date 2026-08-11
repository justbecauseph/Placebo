package dev.shadowsoffire.placebo.util;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

/**
 * {@link TooltipComponents.Impl} for NeoForge. Goes through {@code ClientHooks} rather than vanilla so that
 * other mods' contributed tooltip components still appear.
 */
public class NeoForgeTooltipComponents implements TooltipComponents.Impl {

    @Override
    public List<ClientTooltipComponent> gather(List<FormattedText> lines, int x, int screenWidth, int screenHeight, Font font) {
        return ClientHooks.gatherTooltipComponents(ItemStack.EMPTY, lines, x, screenWidth, screenHeight, font);
    }

}
