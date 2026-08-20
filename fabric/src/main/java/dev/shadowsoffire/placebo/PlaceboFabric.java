package dev.shadowsoffire.placebo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.shadowsoffire.placebo.attachment.DataAttachment;
import dev.shadowsoffire.placebo.attachment.FabricDataAttachment;
import dev.shadowsoffire.placebo.commands.PlaceboCommand;
import dev.shadowsoffire.placebo.color.GradientColor;
import dev.shadowsoffire.placebo.crafting.IngredientType;
import dev.shadowsoffire.placebo.dynreg.DynRegPayloads;
import dev.shadowsoffire.placebo.dynreg.FabricDynReg;
import dev.shadowsoffire.placebo.dynreg.TagSyncPayload;
import dev.shadowsoffire.placebo.loot.FabricLootModifiers;
import dev.shadowsoffire.placebo.network.FabricPayloadRegistrar;
import dev.shadowsoffire.placebo.network.FabricPayloadSender;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.network.PayloadSender;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.FabricDataMaps;
import dev.shadowsoffire.placebo.registry.FabricDeferredHelper;
import dev.shadowsoffire.placebo.registry.FabricIngredients;
import dev.shadowsoffire.placebo.registry.FabricRegistryFactory;
import dev.shadowsoffire.placebo.systems.gear.GearSetRegistry;
import dev.shadowsoffire.placebo.systems.mixes.MixRegistry;
import dev.shadowsoffire.placebo.tabs.FabricTabFillContext;
import dev.shadowsoffire.placebo.util.FabricFakePlayerHelper;
import dev.shadowsoffire.placebo.util.FabricMobSpawnHelper;
import dev.shadowsoffire.placebo.util.FabricPersistentData;
import dev.shadowsoffire.placebo.util.FakePlayerHelper;
import dev.shadowsoffire.placebo.util.MobSpawnHelper;
import dev.shadowsoffire.placebo.util.PersistentData;
import dev.shadowsoffire.placebo.util.PlaceboTaskQueue;
import dev.shadowsoffire.placebo.util.PlaceboUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Fabric entrypoint, the counterpart to {@code PlaceboNeoForge}.
 * <p>
 * Loader-neutral systems keep their policy in common while this class supplies Fabric lifecycle and API adapters.
 * Brewing mixes use the same dynamic registry and vanilla {@code PotionBrewing} mutation policy as NeoForge;
 * only discovery of the live client/server brewing instances is loader-specific.
 *
 * <h2>Registration ordering</h2>
 * Dependent entrypoints call {@link #bootstrap()} before touching common registration code because Fabric's
 * main entrypoint ordering is not a dependency initialization contract.
 */
public class PlaceboFabric implements ModInitializer {

    private static boolean initialized;
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        bootstrap();
    }

    /** Installs all Fabric services exactly once, including when a dependent mod initializes first. */
    public static synchronized void bootstrap() {
        if (initialized) return;
        initialized = true;

        PlaceboConfig.load();

        // Install the Fabric implementation before any dependent registry hub is initialized.
        DeferredHelper.setFactory(FabricDeferredHelper::new);
        DeferredHelper.setRegistryFactory(new FabricRegistryFactory());
        DeferredHelper.setDataMapFactory(new FabricDataMaps());
        FabricDataMaps.registerReloadListener();
        // Global loot modifiers: NeoForge has a whole subsystem, Fabric has none, so Placebo reads the
        // same JSON and applies it on LootTableEvents.MODIFY_DROPS.
        FabricLootModifiers.install();
        IngredientType.setImpl(new FabricIngredients());

        // Vanilla hands out an immutable map and Placebo adds named colours to it. The access widener makes
        // the field writable on both loaders.
        TextColor.NAMED_COLORS = new HashMap<>(TextColor.NAMED_COLORS);
        PlaceboUtil.registerCustomColor(GradientColor.RAINBOW);

        // Installs the dynreg hooks, including the datapack-sync listener.
        FabricDynReg.install();
        MixRegistry.setBrewingResolver(PlaceboFabric::resolveBrewing);

        // Fabric initializes Placebo before its dependents. Register providers immediately so dependent mods
        // can keep using PayloadHelper from their own entrypoints.
        FabricPayloadRegistrar.install();

        // Placebo's own dynamic-registry and UI payloads.
        PayloadHelper.registerPayload(new DynRegPayloads.Start.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Content.Provider<>());
        PayloadHelper.registerPayload(new DynRegPayloads.End.Provider());
        PayloadHelper.registerPayload(new TagSyncPayload.Provider());
        PayloadHelper.registerPayload(new FabricDataMaps.SyncPayload.Provider());
        PayloadHelper.registerPayload(new ButtonClickPayload.Provider());
        PayloadHelper.registerPayload(new PatreonDisablePayload.Provider());

        // Gear sets are a plain dynamic registry, so they work as soon as dynreg does.
        GearSetRegistry.INSTANCE.registerToBus();
        MixRegistry.INSTANCE.registerToBus();

        // PlaceboCommand is already common and takes vanilla types, so this is a direct wire-up.
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> PlaceboCommand.register(dispatcher, ctx));

        // Creative tab filling. One global listener, so filler registration order does not matter.
        FabricTabFillContext.install();

        // Outbound dispatch is called for the rest of the session.
        PayloadSender.setImpl(new FabricPayloadSender());
        FakePlayerHelper.setImpl(new FabricFakePlayerHelper());
        MobSpawnHelper.setImpl(new FabricMobSpawnHelper());
        // The half of the spawn-cancelled flag that acts on it. NeoForge does the same thing from its own
        // EntityJoinLevelEvent handler; Architectury's EntityEvent.ADD is the loader-neutral spelling, and
        // returning interruptFalse is what stops the entity being added.
        EntityEvent.ADD.register((entity, level) -> {
            if (entity instanceof Mob mob && MobSpawnHelper.isSpawnCancelled(mob)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        PersistentData.setImpl(new FabricPersistentData());
        // Attachments. Anonymous rather than a method reference: the factory method is generic.
        DeferredHelper.setAttachmentFactory(new DeferredHelper.AttachmentFactory() {

            @Override
            public <T> DataAttachment<T> create(Identifier id, java.util.function.Supplier<T> defaultValue,
                java.util.function.UnaryOperator<DataAttachment.Builder<T>> config) {
                return FabricDataAttachment.build(id, defaultValue, config);
            }
        });

        // PlaceboTaskQueue: clear on server start/stop, tick every server tick.
        LifecycleEvent.SERVER_STARTED.register(startedServer -> {
            server = startedServer;
            MixRegistry.applyMixes();
            PlaceboTaskQueue.onServerStart();
        });
        LifecycleEvent.SERVER_STOPPED.register(stoppedServer -> {
            server = null;
            PlaceboTaskQueue.onServerStop();
        });
        TickEvent.SERVER_POST.register(server -> PlaceboTaskQueue.tick());

        Placebo.LOGGER.info("Placebo (Fabric) initialized.");
    }

    private static List<PotionBrewing> resolveBrewing() {
        List<PotionBrewing> registries = new ArrayList<>();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            PotionBrewing clientBrewing = PlaceboClient.getBrewingRegistry();
            if (clientBrewing != null) registries.add(clientBrewing);
        }
        if (server != null) registries.add(server.potionBrewing());
        return registries;
    }

}
