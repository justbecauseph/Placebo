package dev.shadowsoffire.placebo.dynreg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * NeoForge implementation of {@link ReloadContext}, built from the context and registry lookup that
 * {@code ContextAwareReloadListener} injects into every reload listener.
 */
public record NeoForgeReloadContext(HolderLookup.Provider registries, ICondition.IContext context) implements ReloadContext {

    @Override
    public DynamicOps<JsonElement> ops() {
        return new ConditionalOps<>(this.registries.createSerializationContext(JsonOps.INSTANCE), this.context);
    }

    @Override
    public boolean conditionsMatch(JsonObject obj) {
        return ICondition.conditionsMatched(this.ops(), obj);
    }

}
