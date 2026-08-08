package dev.shadowsoffire.placebo.dynreg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;

/**
 * Everything a {@link DynamicRegistry} reload needs from the platform, made explicit.
 * <p>
 * On NeoForge all of this arrived implicitly: NeoForge patches {@code SimplePreparableReloadListener} to
 * extend {@code ContextAwareReloadListener}, which has {@code injectContext(ICondition.IContext,
 * HolderLookup.Provider)} called for it by the reload machinery and exposes {@code getRegistryLookup()},
 * {@code getContext()} and {@code makeConditionalOps()}. None of that is visible in imports, and none of it
 * exists on Fabric -- which is why the dependency survived the import scan in
 * {@code porting/reference/phase2-spikes.md} and only surfaced when the class was moved to common.
 *
 * <p>Implementations:
 * <ul>
 * <li><b>NeoForge</b> wraps {@code ConditionalOps} and {@code ICondition.conditionsMatched}.
 * <li><b>Fabric</b> (Phase 2b) pairs a plain {@code RegistryOps} with
 * {@code fabric-resource-conditions-api-v1}. Fabric checks conditions <i>before</i> decoding rather than
 * stripping them during decode, which is exactly why {@link #conditionsMatch} is a separate call here
 * instead of being folded into {@link #ops()}.
 * </ul>
 */
public interface ReloadContext {

    /**
     * The registry lookup for this reload, used to build serialization contexts and resolve holders.
     */
    HolderLookup.Provider registries();

    /**
     * The ops to decode entries with. On NeoForge this is a {@code ConditionalOps}; on Fabric a plain
     * {@code RegistryOps}. Callers must not assume either.
     */
    DynamicOps<JsonElement> ops();

    /**
     * Whether the entry's embedded load conditions (if any) are satisfied. An entry with no conditions
     * always matches.
     */
    boolean conditionsMatch(JsonObject obj);

}
