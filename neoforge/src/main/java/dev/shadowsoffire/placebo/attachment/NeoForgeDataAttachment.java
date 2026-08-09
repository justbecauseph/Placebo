package dev.shadowsoffire.placebo.attachment;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * {@link DataAttachment} over NeoForge's own {@code AttachmentType}, so nothing about how this data is stored,
 * copied or saved changes -- including the field names already written into existing worlds.
 */
public record NeoForgeDataAttachment<T>(AttachmentType<T> type) implements DataAttachment<T> {

    /**
     * Builds the NeoForge type from the common builder calls, then wraps it.
     */
    public static <T> NeoForgeDataAttachment<T> build(Supplier<T> defaultValue, UnaryOperator<DataAttachment.Builder<T>> config) {
        BuilderImpl<T> builder = new BuilderImpl<>(AttachmentType.builder(defaultValue));
        config.apply(builder);
        return new NeoForgeDataAttachment<>(builder.inner.build());
    }

    @Override
    public T get(Entity holder) {
        return holder.getData(this.type);
    }

    @Override
    public void set(Entity holder, T value) {
        holder.setData(this.type, value);
    }

    @Override
    public boolean has(Entity holder) {
        return holder.hasData(this.type);
    }

    @Override
    public T get(BlockEntity holder) {
        return holder.getData(this.type);
    }

    @Override
    public void set(BlockEntity holder, T value) {
        holder.setData(this.type, value);
    }

    @Override
    public boolean has(BlockEntity holder) {
        return holder.hasData(this.type);
    }

    private static class BuilderImpl<T> implements DataAttachment.Builder<T> {

        private AttachmentType.Builder<T> inner;

        BuilderImpl(AttachmentType.Builder<T> inner) {
            this.inner = inner;
        }

        @Override
        public DataAttachment.Builder<T> persistent(MapCodec<T> codec) {
            this.inner = this.inner.serialize(codec);
            return this;
        }

        @Override
        public DataAttachment.Builder<T> persistent(MapCodec<T> codec, Predicate<? super T> shouldSave) {
            this.inner = this.inner.serialize(codec, shouldSave);
            return this;
        }

        @Override
        public DataAttachment.Builder<T> syncAll(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            this.inner = this.inner.sync((holder, player) -> true, streamCodec);
            return this;
        }

        @Override
        public DataAttachment.Builder<T> copyOnDeath() {
            this.inner = this.inner.copyOnDeath();
            return this;
        }
    }

}
