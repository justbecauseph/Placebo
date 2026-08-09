package dev.shadowsoffire.placebo.registry;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * {@link DeferredHelper.RegistryFactory} over {@code fabric-registry-sync-v0}.
 * <p>
 * The one shape mismatch is {@code onBake}: Fabric has no bake step, so the callback is attached to
 * {@code RegistryEntryAddedCallback} and runs after each entry instead. See {@link RegistrySpec#onBake} for
 * why that is equivalent for the callbacks this stack actually uses.
 */
public class FabricRegistryFactory implements DeferredHelper.RegistryFactory {

    @Override
    public <T> Registry<T> create(DeferredHelper owner, ResourceKey<Registry<T>> key,
        UnaryOperator<RegistrySpec<T>> config) {
        SpecImpl<T> spec = new SpecImpl<>();
        config.apply(spec);

        Registry<T> registry = spec.defaultId != null
            ? FabricRegistryBuilder.createDefaulted(key, spec.defaultId)
                .attribute(spec.synced ? RegistryAttribute.SYNCED : RegistryAttribute.MODDED)
                .buildAndRegister()
            : FabricRegistryBuilder.create(key)
                .attribute(spec.synced ? RegistryAttribute.SYNCED : RegistryAttribute.MODDED)
                .buildAndRegister();

        if (spec.onBake != null) {
            Consumer<Registry<T>> callback = spec.onBake;
            RegistryEntryAddedCallback.event(registry).register((raw, id, obj) -> callback.accept(registry));
        }
        return registry;
    }

    private static class SpecImpl<T> implements RegistrySpec<T> {

        private boolean synced;
        private Identifier defaultId;
        private Consumer<Registry<T>> onBake;

        @Override
        public RegistrySpec<T> synced() {
            this.synced = true;
            return this;
        }

        @Override
        public RegistrySpec<T> defaultKey(Identifier id) {
            this.defaultId = id;
            return this;
        }

        @Override
        public RegistrySpec<T> onBake(Consumer<Registry<T>> callback) {
            this.onBake = callback;
            return this;
        }
    }

}
