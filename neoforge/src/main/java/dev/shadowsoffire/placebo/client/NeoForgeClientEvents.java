package dev.shadowsoffire.placebo.client;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.PlaceboClient;
import dev.shadowsoffire.placebo.events.ResourceReloadEvent;
import dev.shadowsoffire.placebo.network.ClientPayloadSender;
import dev.shadowsoffire.placebo.network.NeoForgeClientPayloadSender;
import dev.shadowsoffire.placebo.patreon.TrailsManager;
import dev.shadowsoffire.placebo.util.NeoForgeTooltipComponents;
import dev.shadowsoffire.placebo.util.TooltipComponents;
import dev.shadowsoffire.placebo.patreon.WingsManager;
import dev.shadowsoffire.placebo.patreon.wings.Wing;
import dev.shadowsoffire.placebo.patreon.wings.WingLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * NeoForge's client wiring. The state it feeds lives in {@link PlaceboClient}, which is common; this class is
 * only the subscriptions, and {@code PlaceboFabricClient} is its counterpart.
 * <p>
 * The two scroll handlers are not redundant. NeoForge delivers a scroll through a screen event when a screen
 * has focus and a raw input event when none does, and a tooltip can be showing in either case, so both feed
 * the same decision in {@link PlaceboClient#scroll}.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Placebo.MODID)
public class NeoForgeClientEvents {

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        TooltipComponents.setImpl(new NeoForgeTooltipComponents());
        ClientPayloadSender.setImpl(new NeoForgeClientPayloadSender());
        TrailsManager.init();
        WingsManager.init();
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::tick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::tooltip);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::scroll);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::scroll2);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WingsManager.WING_LOC, Wing::createLayer);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.registerCategory(PlaceboClient.KEY_CATEGORY);
        e.register(TrailsManager.TOGGLE);
        e.register(WingsManager.TOGGLE);
    }

    @SubscribeEvent
    public static void clientResource(AddClientReloadListenersEvent e) {
        e.addListener(Placebo.loc("client_reload_event"), (ResourceManagerReloadListener) res -> NeoForge.EVENT_BUS.post(new ResourceReloadEvent(res, LogicalSide.CLIENT)));
    }

    @SubscribeEvent
    public static void addLayers(AddLayers e) {
        Wing.INSTANCE = new Wing(e.getEntityModels().bakeLayer(WingsManager.WING_LOC));
        for (PlayerModelType s : e.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = e.getPlayerRenderer(s);
            if (renderer != null) {
                renderer.addLayer(new WingLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent e) {
        WingLayer.registerModifier(e);
    }

    public static void tick(ClientTickEvent.Post e) {
        PlaceboClient.tick();
        TrailsManager.tick();
        TrailsManager.handleKeybind();
        WingsManager.handleKeybind();
    }

    public static void scroll(ScreenEvent.MouseScrolled.Pre e) {
        if (PlaceboClient.scroll(e.getScrollDeltaY())) {
            e.setCanceled(true);
        }
    }

    public static void scroll2(InputEvent.MouseScrollingEvent e) {
        if (PlaceboClient.scroll(e.getScrollDeltaY())) {
            e.setCanceled(true);
        }
    }

    public static void tooltip(ItemTooltipEvent e) {
        PlaceboClient.setTooltipItem(e.getItemStack());
    }
}
