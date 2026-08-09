package dev.shadowsoffire.placebo.registry;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * {@link DeferredHelper.RegistryFactory} over NeoForge's {@code RegistryBuilder}, so registries are built and
 * registered exactly as before.
 */
public class NeoForgeRegistryFactory implements DeferredHelper.RegistryFactory {

    @Override
    public <T> Registry<T> create(DeferredHelper owner, ResourceKey<Registry<T>> key,
        UnaryOperator<RegistrySpec<T>> config) {
        SpecImpl<T> spec = new SpecImpl<>(new RegistryBuilder<>(key));
        config.apply(spec);
        Registry<T> registry = spec.inner.create();
        ((NeoForgeDeferredHelper) owner).registerRegistry(key, registry);
        return registry;
    }

    private static class SpecImpl<T> implements RegistrySpec<T> {

        private RegistryBuilder<T> inner;

        SpecImpl(RegistryBuilder<T> inner) {
            this.inner = inner;
        }

        @Override
        public RegistrySpec<T> synced() {
            this.inner = this.inner.sync(true);
            return this;
        }

        @Override
        public RegistrySpec<T> defaultKey(Identifier id) {
            this.inner = this.inner.defaultKey(id);
            return this;
        }

        @Override
        public RegistrySpec<T> onBake(Consumer<Registry<T>> callback) {
            this.inner = this.inner.onBake(callback::accept);
            return this;
        }
    }

}
