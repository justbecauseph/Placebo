package dev.shadowsoffire.placebo.dynreg.tag;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;

/**
 * Placebo's own tag file model: vanilla's {@link TagFile} plus the {@code remove} list.
 * <p>
 * This exists because {@code remove} is a NeoForge <em>addition</em> to vanilla's {@code TagFile} record.
 * An access widener cannot add a field, so for a long time this looked like a hard blocker on dynamic tag
 * loading for Fabric.
 * <p>
 * It is not: nothing requires vanilla's record at all. The JSON is the contract, and decoding it with a codec
 * Placebo owns -- built from the <em>public</em> {@code TagEntry.CODEC} -- reads exactly the same files on
 * both loaders, including {@code remove}. Vanilla ignores the extra key; NeoForge users see no change.
 */
public record TagFileData(List<TagEntry> entries, boolean replace, List<TagEntry> remove) {

    public static final Codec<TagFileData> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            TagEntry.CODEC.listOf().fieldOf("values").forGetter(TagFileData::entries),
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(TagFileData::replace),
            TagEntry.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(TagFileData::remove))
        .apply(inst, TagFileData::new));

}
