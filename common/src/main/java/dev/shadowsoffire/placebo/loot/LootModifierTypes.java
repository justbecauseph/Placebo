package dev.shadowsoffire.placebo.loot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;

/**
 * The id-to-codec map behind {@code DeferredHelper#lootModifier}.
 * <p>
 * NeoForge already keeps one of these — a real registry of serializers — so on that loader this is only a
 * record of what was registered. Fabric has nothing equivalent, and this <i>is</i> the dispatch table its
 * loader reads.
 * <p>
 * Kept in insertion order so a Fabric run reports unknown types against a stable list.
 */
public class LootModifierTypes {

    private static final Map<Identifier, MapCodec<? extends LootModifier>> TYPES = new LinkedHashMap<>();

    public static void register(Identifier id, MapCodec<? extends LootModifier> codec) {
        MapCodec<? extends LootModifier> existing = TYPES.putIfAbsent(id, codec);
        if (existing != null && existing != codec) {
            throw new IllegalStateException("Duplicate loot modifier type: " + id);
        }
    }

    @Nullable
    public static MapCodec<? extends LootModifier> get(Identifier id) {
        return TYPES.get(id);
    }

    public static Map<Identifier, MapCodec<? extends LootModifier>> all() {
        return Map.copyOf(TYPES);
    }

}
