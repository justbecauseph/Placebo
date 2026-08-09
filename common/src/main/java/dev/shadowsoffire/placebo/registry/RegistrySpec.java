package dev.shadowsoffire.placebo.registry;

import java.util.function.Consumer;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * How a mod-defined registry should be built, loader-neutrally.
 * <p>
 * Sized from the three custom registries in this stack: all three are synced, one has a default entry, and
 * one rebuilds a cache once its contents are known. NeoForge's {@code RegistryBuilder} and Fabric's
 * {@code FabricRegistryBuilder} both offer considerably more; neither surface is worth mirroring for three
 * callers.
 */
public interface RegistrySpec<T> {

    /**
     * Sends the registry's contents to clients on join, so ids match on both sides.
     */
    RegistrySpec<T> synced();

    /**
     * Makes this a defaulted registry: unknown ids resolve to this entry rather than null.
     */
    RegistrySpec<T> defaultKey(Identifier id);

    /**
     * Runs once the registry's contents are known, for callers that cache something derived from them.
     * <p>
     * NeoForge fires this at bake, exactly once. Fabric has no bake step, so it runs after each entry is
     * added instead -- more often, same end state. That is only safe for callbacks that rebuild from the
     * whole registry rather than accumulate, which is what "on bake" tends to mean anyway; the one caller
     * here rebuilds a sorted list.
     */
    RegistrySpec<T> onBake(Consumer<Registry<T>> callback);

}
