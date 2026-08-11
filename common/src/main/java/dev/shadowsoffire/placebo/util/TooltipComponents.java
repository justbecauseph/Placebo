package dev.shadowsoffire.placebo.util;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;

/**
 * Turns tooltip text into the components a screen can draw.
 * <p>
 * Vanilla can do this -- {@code ClientTooltipComponent.create} over each line -- but NeoForge's
 * {@code ClientHooks.gatherTooltipComponents} additionally fires an event so <i>other</i> mods can contribute
 * custom components. Calling vanilla directly on NeoForge would quietly drop those, which is the sort of
 * substitution this port has been careful not to make.
 * <p>
 * So this is a one-method seam rather than a shared implementation, and it is the whole reason
 * {@link DrawsOnLeft} can be common.
 */
public class TooltipComponents {

    private static Impl impl;

    public static void setImpl(Impl impl) {
        TooltipComponents.impl = Objects.requireNonNull(impl);
    }

    public static List<ClientTooltipComponent> gather(List<FormattedText> lines, int x, int screenWidth, int screenHeight, Font font) {
        if (impl == null) {
            throw new IllegalStateException("No TooltipComponents implementation has been installed. "
                + "The platform's client entrypoint must call TooltipComponents.setImpl.");
        }
        return impl.gather(lines, x, screenWidth, screenHeight, font);
    }

    public interface Impl {

        List<ClientTooltipComponent> gather(List<FormattedText> lines, int x, int screenWidth, int screenHeight, Font font);
    }

}
