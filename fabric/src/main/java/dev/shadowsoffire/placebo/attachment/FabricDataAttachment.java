package dev.shadowsoffire.placebo.attachment;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * {@link DataAttachment} over {@code fabric-data-attachment-api-v1}.
 * <p>
 * Fabric has no "shouldSave" predicate, so the two-argument {@code persistent} saves unconditionally here.
 * The declarations that pass one do so to keep default values out of the save file -- a size optimisation,
 * not a behaviour.
 */
public record FabricDataAttachment<T>(AttachmentType<T> type) implements DataAttachment<T> {

    public static <T> FabricDataAttachment<T> build(Identifier id, Supplier<T> defaultValue,
        UnaryOperator<DataAttachment.Builder<T>> config) {
        BuilderImpl<T> builder = new BuilderImpl<>(defaultValue);
        config.apply(builder);
        return new FabricDataAttachment<>(AttachmentRegistry.create(id, builder::apply));
    }

    @Override
    public T get(Entity holder) {
        return ((AttachmentTarget) holder).getAttachedOrCreate(this.type);
    }

    @Override
    public void set(Entity holder, T value) {
        ((AttachmentTarget) holder).setAttached(this.type, value);
    }

    @Override
    public boolean has(Entity holder) {
        return ((AttachmentTarget) holder).hasAttached(this.type);
    }

    @Override
    public T get(BlockEntity holder) {
        return ((AttachmentTarget) holder).getAttachedOrCreate(this.type);
    }

    @Override
    public void set(BlockEntity holder, T value) {
        ((AttachmentTarget) holder).setAttached(this.type, value);
    }

    @Override
    public boolean has(BlockEntity holder) {
        return ((AttachmentTarget) holder).hasAttached(this.type);
    }

    private static class BuilderImpl<T> implements DataAttachment.Builder<T> {

        private final Supplier<T> defaultValue;
        private MapCodec<T> codec;
        private boolean copyOnDeath;
        private StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;

        BuilderImpl(Supplier<T> defaultValue) {
            this.defaultValue = defaultValue;
        }

        void apply(AttachmentRegistry.Builder<T> b) {
            b.initializer(this.defaultValue);
            // MapCodec#codec wraps the same field name NeoForge writes, so both loaders store one shape.
            if (this.codec != null) b.persistent(this.codec.codec());
            if (this.copyOnDeath) b.copyOnDeath();
            if (this.streamCodec != null) b.syncWith(this.streamCodec, AttachmentSyncPredicate.all());
        }

        @Override
        public DataAttachment.Builder<T> persistent(MapCodec<T> codec) {
            this.codec = codec;
            return this;
        }

        @Override
        public DataAttachment.Builder<T> persistent(MapCodec<T> codec, Predicate<? super T> shouldSave) {
            return this.persistent(codec);
        }

        @Override
        public DataAttachment.Builder<T> syncAll(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            this.streamCodec = streamCodec;
            return this;
        }

        @Override
        public DataAttachment.Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }
    }

}
