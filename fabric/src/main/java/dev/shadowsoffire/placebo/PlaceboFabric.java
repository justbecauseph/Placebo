package dev.shadowsoffire.placebo;

import dev.shadowsoffire.placebo.dynreg.FabricDynReg;
import dev.shadowsoffire.placebo.network.FabricPayloadRegistrar;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.FabricDeferredHelper;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint, the counterpart to {@code PlaceboNeoForge}.
 * <p>
 * <b>Phase 2b is in progress: this installs registration only.</b> What the NeoForge entrypoint additionally
 * does, and what each one is waiting on:
 *
 * <table>
 * <tr><th>NeoForge does</th><th>Fabric status</th></tr>
 * <tr><td>{@code NeoForgeDynReg.install()}</td>
 * <td>Done -- see {@code FabricDynReg}. Dynamic tag <em>loading</em> is still missing (it needs NeoForge's
 * {@code TagFile.remove()}); tag syncing works.</td></tr>
 * <tr><td>Command registration</td><td>Straightforward: {@code CommandRegistrationCallback}.</td></tr>
 * <tr><td>{@code TextColor.NAMED_COLORS} rewrite</td><td>Portable -- it is a vanilla field reached through the access widener.</td></tr>
 * <tr><td>{@code TabFillingRegistry::fillTabs}</td><td>Needs {@code ItemGroupEvents}.</td></tr>
 * </table>
 *
 * <h2>Registration ordering</h2>
 * {@link dev.shadowsoffire.placebo.network.PayloadHelper#drain} locks registration, so this initializer must
 * run after every mod that registers payloads. Fabric orders {@code main} entrypoints by mod dependency, and
 * Placebo's dependents all depend on it -- so if a downstream mod registers from its own initializer, that
 * runs first. A mod registering later would now throw rather than be silently dropped, which is the intent.
 */
public class PlaceboFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // The base DeferredHelper is fully loader-neutral; only the 16 platform-only methods are missing here.
        DeferredHelper.setFactory(FabricDeferredHelper::new);

        // Dynamic registries: install before payloads are flushed, since installing registers the sync hooks.
        FabricDynReg.install();

        // Flush whatever payloads have been registered by now into Fabric's networking API. Mods register
        // during their own initializer, so this has to run after them -- see the note on ordering below.
        FabricPayloadRegistrar.register();

        Placebo.LOGGER.info("Placebo (Fabric) initialized.");
    }

}
