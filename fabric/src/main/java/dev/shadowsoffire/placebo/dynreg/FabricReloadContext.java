package dev.shadowsoffire.placebo.dynreg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;

/**
 * Fabric's {@link ReloadContext}.
 * <p>
 * This is where the two loaders genuinely differ, and it is the reason {@code ReloadContext} separates
 * {@link #ops()} from {@link #conditionsMatch}. NeoForge's {@code ConditionalOps} is a {@link DynamicOps}
 * <em>wrapper</em> that strips condition-failing entries <em>during</em> decode, so on that side the two are
 * the same object. Fabric evaluates conditions <em>before</em> decode, against a separate registry of
 * condition types, so here {@code ops()} is plain serialization ops and the condition check stands alone.
 * <p>
 * Fabric applies conditions automatically for {@code SimpleJsonResourceReloadListener}, but
 * {@link DynamicRegistry} extends {@code SimplePreparableReloadListener} and scans JSON itself, so it has to
 * ask explicitly.
 */
public record FabricReloadContext(HolderLookup.Provider registries) implements ReloadContext {

    @Override
    public DynamicOps<JsonElement> ops() {
        return this.registries.createSerializationContext(JsonOps.INSTANCE);
    }

    @Override
    public boolean conditionsMatch(JsonObject obj) {
        JsonElement conditions = obj.get(ResourceConditions.CONDITIONS_KEY);
        if (conditions == null) {
            return true;
        }
        // CONDITION_CODEC accepts a single condition or an array, matching what Fabric's own loader accepts.
        // The lookup is only consulted by registry/tag conditions; passing null makes those report false
        // rather than throw, which is Fabric's documented behaviour for a missing lookup.
        return ResourceCondition.CONDITION_CODEC
            .parse(JsonOps.INSTANCE, conditions)
            .getOrThrow(err -> new IllegalStateException("Invalid " + ResourceConditions.CONDITIONS_KEY + ": " + err))
            .test(null);
    }

}
