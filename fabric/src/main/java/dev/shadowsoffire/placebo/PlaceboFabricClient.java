package dev.shadowsoffire.placebo;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.shadowsoffire.placebo.util.FabricTooltipComponents;
import dev.shadowsoffire.placebo.util.TooltipComponents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/**
 * Fabric's client entrypoint, the counterpart to {@code NeoForgeClientEvents} -- and the first
 * {@code ClientModInitializer} in this stack.
 * <p>
 * It feeds the same three things NeoForge's wiring does, into the same common {@link PlaceboClient}: the tick
 * count, what is under the cursor, and scroll while a tooltip is showing. Everything that reads them is
 * already loader-neutral, which is why {@code GradientColor} could move to {@code :common} the moment this
 * existed.
 *
 * <h2>Not yet ported</h2>
 * <ul>
 * <li><b>The Patreon cosmetics</b> -- {@code TrailsManager}, {@code WingsManager}, {@code WingLayer}. They are
 * entity-layer rendering, which is a separate design item from client <i>state</i>, and nothing else depends
 * on them.
 * <li><b>Key mappings.</b> Architectury's {@code KeyMappingRegistry} covers this; the two mappings that exist
 * belong to the cosmetics above, so they move together.
 * <li><b>The client reload listener</b>, which fires Placebo's own {@code ResourceReloadEvent} for client
 * resources. {@code ResourceManagerHelper} for {@code PackType.CLIENT_RESOURCES} is the equivalent.
 * </ul>
 *
 * <h2>The scroll pair</h2>
 * NeoForge feeds the scroll decision from two events, a screen one and a raw input one, because a tooltip can
 * be showing with or without a focused screen. Fabric's screen events are per-screen rather than global, so
 * the equivalent is registered as screens open. There is no raw-input half here: on Fabric a tooltip only
 * renders inside a screen, so the second NeoForge event has nothing to correspond to.
 */
public class PlaceboFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TooltipComponents.setImpl(new FabricTooltipComponents());

        ClientTickEvent.CLIENT_POST.register(mc -> PlaceboClient.tick());

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> PlaceboClient.setTooltipItem(stack));

        ScreenEvents.BEFORE_INIT.register((mc, screen, width, height) -> ScreenMouseEvents.allowMouseScroll(screen)
            .register((s, mouseX, mouseY, horizontal, vertical) -> !PlaceboClient.scroll(vertical)));

        Placebo.LOGGER.info("Placebo (Fabric) client initialized.");
    }

}
