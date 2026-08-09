package dev.shadowsoffire.placebo;

import java.util.HashMap;


import dev.shadowsoffire.placebo.color.GradientColor;
import dev.shadowsoffire.placebo.commands.PlaceboCommand;
import dev.shadowsoffire.placebo.datagen.FieldOrderingFactory;
import dev.shadowsoffire.placebo.datagen.RegisterFieldOrderingsEvent;
import dev.shadowsoffire.placebo.dynreg.DynRegPayloads;
import dev.shadowsoffire.placebo.dynreg.NeoForgeDynReg;
import dev.shadowsoffire.placebo.attachment.DataAttachment;
import dev.shadowsoffire.placebo.attachment.NeoForgeDataAttachment;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.NeoForgeRegistryFactory;
import dev.shadowsoffire.placebo.registry.NeoForgeDeferredHelper;
import dev.shadowsoffire.placebo.dynreg.TagSyncPayload;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import dev.shadowsoffire.placebo.events.ResourceReloadEvent;
import dev.shadowsoffire.placebo.events.NeoForgeEventBridge;
import dev.shadowsoffire.placebo.network.NeoForgePayloadRegistrar;
import dev.shadowsoffire.placebo.network.NeoForgePayloadSender;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.network.PayloadSender;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.systems.gear.GearSetRegistry;
import dev.shadowsoffire.placebo.systems.mixes.MixRegistry;
import dev.shadowsoffire.placebo.tabs.NeoForgeTabFillContext;
import dev.shadowsoffire.placebo.tabs.TabFillingRegistry;
import dev.shadowsoffire.placebo.util.FakePlayerHelper;
import dev.shadowsoffire.placebo.util.MobSpawnHelper;
import dev.shadowsoffire.placebo.util.NeoForgeMobSpawnHelper;
import dev.shadowsoffire.placebo.util.NeoForgePersistentData;
import dev.shadowsoffire.placebo.util.PersistentData;
import dev.shadowsoffire.placebo.util.NeoForgeFakePlayerHelper;
import dev.shadowsoffire.placebo.util.PlaceboUtil;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@Mod(Placebo.MODID)
@SuppressWarnings("deprecation")
public class PlaceboNeoForge {

    public PlaceboNeoForge(IEventBus bus) {
        bus.register(this);
        NeoForgeDynReg.install();
        DeferredHelper.setFactory(NeoForgeDeferredHelper::new);
        DeferredHelper.setRegistryFactory(new NeoForgeRegistryFactory());
        NeoForge.EVENT_BUS.register(new NeoForgeEventBridge());
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(NeoForgeDynReg::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(this::serverReload);
        NeoForge.EVENT_BUS.addListener(this::serverStart);
        TextColor.NAMED_COLORS = new HashMap<>(TextColor.NAMED_COLORS);
        bus.addListener(NeoForgeTabFillContext::fillTabs);
        bus.register(new NeoForgePayloadRegistrar());
        PayloadSender.setImpl(new NeoForgePayloadSender());
        FakePlayerHelper.setImpl(new NeoForgeFakePlayerHelper());
        MobSpawnHelper.setImpl(new NeoForgeMobSpawnHelper());
        PersistentData.setImpl(new NeoForgePersistentData());
        // Attachments. Anonymous rather than a method reference: the factory method is generic.
        DeferredHelper.setAttachmentFactory(new DeferredHelper.AttachmentFactory() {

            @Override
            public <T> DataAttachment<T> create(Identifier id, java.util.function.Supplier<T> defaultValue,
                java.util.function.UnaryOperator<DataAttachment.Builder<T>> config) {
                return NeoForgeDataAttachment.build(defaultValue, config);
            }
        });

        PlaceboConfig.load();
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent e) {
        PayloadHelper.registerPayload(new ButtonClickPayload.Provider());
        PayloadHelper.registerPayload(new PatreonDisablePayload.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Start.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Content.Provider<>());
        PayloadHelper.registerPayload(new DynRegPayloads.End.Provider());
        PayloadHelper.registerPayload(new TagSyncPayload.Provider());
        e.enqueueWork(() -> {
            PlaceboUtil.registerCustomColor(GradientColor.RAINBOW);
        });
        GearSetRegistry.INSTANCE.registerToBus();
        MixRegistry.INSTANCE.registerToBus();
    }

    @SubscribeEvent
    public void registerFieldOrderings(RegisterFieldOrderingsEvent e) {
        e.register(FieldOrderingFactory.forType(MixRegistry.INSTANCE.getId(), b -> b.put("mix_type", 0)));
    }

    public void registerCommands(RegisterCommandsEvent e) {
        PlaceboCommand.register(e.getDispatcher(), e.getBuildContext());
    }

    public void serverReload(AddServerReloadListenersEvent e) {
        NeoForgeDynReg.addReloadListeners(e);
        e.addListener(Placebo.loc("placebo_reload_event"), (ResourceManagerReloadListener) res -> NeoForge.EVENT_BUS.post(new ResourceReloadEvent(res, LogicalSide.SERVER)));
        e.addListener(DynamicTagManager.ID, DynamicTagManager.INSTANCE);
    }

    public void serverStart(ServerAboutToStartEvent e) {
        MixRegistry.applyMixes();
    }

}
