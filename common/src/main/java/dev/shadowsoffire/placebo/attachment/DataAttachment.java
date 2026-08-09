package dev.shadowsoffire.placebo.attachment;

import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A piece of mutable side-data attached to an entity or block entity, loader-neutrally.
 * <p>
 * NeoForge attaches data via {@code AttachmentType} and methods it adds to vanilla holders
 * ({@code entity.getData(TYPE)}); Fabric has {@code fabric-data-attachment-api-v1}, whose targets are
 * {@code Entity}, {@code BlockEntity}, {@code Level} and {@code ChunkAccess}. Every holder in this stack is
 * one of the first two, so the two APIs line up and this is an adapter rather than a rewrite.
 * <p>
 * <b>Not {@code ItemStack}.</b> Fabric has no ItemStack attachments -- items carry data components instead,
 * which are immutable and part of the stack's identity. Nothing here attaches to an ItemStack; the call sites
 * that look like they do are on {@code ItemEntity}. If that ever changes, it is a design question, not an
 * overload.
 * <p>
 * The accessors live on the attachment rather than the holder because vanilla has no common supertype for
 * "thing that can hold data" -- NeoForge adds one, Fabric mixes one in, and neither exists in common.
 */
public interface DataAttachment<T> {

    T get(Entity holder);

    void set(Entity holder, T value);

    boolean has(Entity holder);

    T get(BlockEntity holder);

    void set(BlockEntity holder, T value);

    boolean has(BlockEntity holder);

    /**
     * Configures an attachment as it is registered. Sized from what this stack's twelve declarations use.
     */
    interface Builder<T> {

        /**
         * Saves the attachment with the holder.
         * <p>
         * Takes a {@link MapCodec} rather than a {@code Codec} because NeoForge's serializer only accepts
         * one, and the field name inside it is part of the on-disk format -- changing it would orphan the
         * data in existing worlds. Fabric, which wants a plain codec, gets {@link MapCodec#codec()}, so both
         * loaders write the same shape.
         */
        Builder<T> persistent(MapCodec<T> codec);

        /**
         * As {@link #persistent(MapCodec)}, but skips writing values the predicate rejects -- used to keep
         * defaults out of the save file.
         */
        Builder<T> persistent(MapCodec<T> codec, Predicate<? super T> shouldSave);

        /**
         * Keeps the value across a player respawn or a mob conversion.
         */
        Builder<T> copyOnDeath();

        /**
         * Syncs the value to every player who can see the holder.
         * <p>
         * Both loaders can sync to a subset -- NeoForge via a {@code BiPredicate}, Fabric via
         * {@code AttachmentSyncPredicate} -- but the one declaration in this stack that syncs does so
         * unconditionally, so that is all this offers. Growing it later is a smaller change than guessing
         * at a predicate type that has to mean the same thing on both.
         */
        Builder<T> syncAll(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec);
    }

}
