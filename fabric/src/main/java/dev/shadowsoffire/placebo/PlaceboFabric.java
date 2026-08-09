package dev.shadowsoffire.placebo;

import java.util.HashMap;

import dev.shadowsoffire.placebo.commands.PlaceboCommand;
import dev.shadowsoffire.placebo.dynreg.DynRegPayloads;
import dev.shadowsoffire.placebo.dynreg.FabricDynReg;
import dev.shadowsoffire.placebo.dynreg.TagSyncPayload;
import dev.shadowsoffire.placebo.network.FabricPayloadRegistrar;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.FabricDeferredHelper;
import dev.shadowsoffire.placebo.systems.gear.GearSetRegistry;
import dev.shadowsoffire.placebo.tabs.FabricTabFillContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.TextColor;

/**
 * Fabric entrypoint, the counterpart to {@code PlaceboNeoForge}.
 * <p>
 * Still missing relative to NeoForge, each for a stated reason:
 *
 * <table>
 * <tr><th>NeoForge does</th><th>Fabric status</th></tr>
 * <tr><td>{@code PatreonDisablePayload}</td>
 * <td>Reaches {@code TrailsManager}/{@code WingsManager}, which are client cosmetics code still in
 * {@code neoforge/}. ({@code ButtonClickPayload} turned out to be fully portable and is registered below.)</td></tr>
 * <tr><td>{@code MixRegistry}</td><td>Platform-side: it reaches into {@code PotionBrewing} internals.</td></tr>
 * <tr><td>{@code GradientColor.RAINBOW}</td><td>Platform-side with the rest of the colour handling.</td></tr>
 * <tr><td>Dynamic tag <em>loading</em></td><td>Needs {@code TagFile.remove()}, a NeoForge added field. Tag <em>syncing</em> works.</td></tr>
 * </table>
 *
 * <h2>Registration ordering</h2>
 * {@link PayloadHelper#drain} locks registration, so this initializer must run after every mod that registers
 * payloads. Fabric orders {@code main} entrypoints by mod dependency, and Placebo's dependents all depend on
 * it -- so a downstream mod registering from its own initializer runs first. A mod registering later now
 * throws rather than being silently dropped, which is the intent.
 */
public class PlaceboFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PlaceboConfig.load();

        // The base DeferredHelper is fully loader-neutral; only the 16 platform-only methods are missing here.
        DeferredHelper.setFactory(FabricDeferredHelper::new);

        // Vanilla hands out an immutable map and Placebo adds named colours to it. The access widener makes
        // the field writable on both loaders.
        TextColor.NAMED_COLORS = new HashMap<>(TextColor.NAMED_COLORS);

        // Installs the dynreg hooks, including the datapack-sync listener.
        FabricDynReg.install();

        // Placebo's own dynamic-registry payloads. These MUST be registered before the drain below -- that
        // ordering is the entire reason registration and flushing are two phases.
        PayloadHelper.registerPayload(new DynRegPayloads.Start.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Content.Provider<>());
        PayloadHelper.registerPayload(new DynRegPayloads.End.Provider());
        PayloadHelper.registerPayload(new TagSyncPayload.Provider());
        PayloadHelper.registerPayload(new ButtonClickPayload.Provider());

        // Gear sets are a plain dynamic registry, so they work as soon as dynreg does.
        GearSetRegistry.INSTANCE.registerToBus();

        // PlaceboCommand is already common and takes vanilla types, so this is a direct wire-up.
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> PlaceboCommand.register(dispatcher, ctx));

        // Creative tab filling. One global listener, so filler registration order does not matter.
        FabricTabFillContext.install();

        // Flush everything registered so far into Fabric's networking API.
        FabricPayloadRegistrar.register();

        Placebo.LOGGER.info("Placebo (Fabric) initialized.");
    }

}
