package dev.shadowsoffire.placebo.util;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;

/**
 * {@link TooltipComponents.Impl} for Fabric: vanilla's own conversion, one component per line.
 * <p>
 * Fabric has no equivalent of NeoForge's gather event. Its {@code TooltipComponentCallback} converts an
 * item's {@code TooltipComponent} into a client one, which is a different question -- these lines are plain
 * text and belong to no item. So other mods cannot contribute here on Fabric, and that is a real difference
 * rather than an oversight; it is recorded here because the two loaders will render subtly different tooltips
 * in a pack that has such a mod.
 */
public class FabricTooltipComponents implements TooltipComponents.Impl {

    @Override
    public List<ClientTooltipComponent> gather(List<FormattedText> lines, int x, int screenWidth, int screenHeight, Font font) {
        return lines.stream()
            .map(line -> ClientTooltipComponent.create(Language.getInstance().getVisualOrder(line)))
            .toList();
    }

}
