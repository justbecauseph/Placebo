package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

/**
 * NeoForge patches vanilla {@code SimplePreparableReloadListener} to extend
 * {@link ContextAwareReloadListener}, which is handed the reload's registry lookup and condition context and
 * exposes them through {@code protected final} accessors. Placebo's {@code DynamicRegistry} used to reach
 * them implicitly by inheritance; now that it lives in common it cannot, so the platform reads them here and
 * hands them over as a {@code ReloadContext}.
 */
@Mixin(value = ContextAwareReloadListener.class, remap = false)
public interface ContextAwareReloadListenerAccessor {

    @Invoker("getRegistryLookup")
    HolderLookup.Provider placebo$getRegistryLookup();

    @Invoker("getContext")
    ICondition.IContext placebo$getContext();

}
