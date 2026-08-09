package dev.shadowsoffire.placebo;

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
 * <td>Blocked. The {@code DynRegPlatform} hooks are implementable -- {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS}
 * is an exact analogue of {@code OnDatapackSyncEvent}, and Architectury's {@code ReloadListenerRegistry} covers
 * listener registration -- but the {@code SyncHandler} sends {@code DynRegPayloads}, and Placebo's whole
 * networking layer ({@code PayloadProvider}, {@code PayloadHelper}) is still NeoForge-side. See below.</td></tr>
 * <tr><td>Command registration</td><td>Straightforward: {@code CommandRegistrationCallback}.</td></tr>
 * <tr><td>{@code TextColor.NAMED_COLORS} rewrite</td><td>Portable -- it is a vanilla field reached through the access widener.</td></tr>
 * <tr><td>{@code TabFillingRegistry::fillTabs}</td><td>Needs {@code ItemGroupEvents}.</td></tr>
 * </table>
 *
 * <h2>The networking prerequisite</h2>
 * The dynamic-registry sync payloads are portable data -- records over {@code CustomPacketPayload} and
 * {@code StreamCodec}, both vanilla. Their NeoForge coupling is confined to the <em>handlers</em>
 * ({@code IPayloadContext}) and one {@code ConnectionType.NEOFORGE}. So the split is the same one that
 * {@code ReloadContext} already makes: move the payload records and stream codecs to {@code :common}, keep
 * registration and handling behind a platform interface. That refactor is the real content of {@code P2-B-1},
 * and it gates {@code dynreg} on Fabric -- which in turn gates every affix, gem, rarity, invader and elite.
 */
public class PlaceboFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // The base DeferredHelper is fully loader-neutral; only the 16 platform-only methods are missing here.
        DeferredHelper.setFactory(FabricDeferredHelper::new);
        Placebo.LOGGER.info("Placebo (Fabric) initialized -- registration only; dynreg is not yet wired up.");
    }

}
