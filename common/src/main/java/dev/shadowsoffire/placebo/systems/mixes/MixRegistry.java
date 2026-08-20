package dev.shadowsoffire.placebo.systems.mixes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;
import dev.shadowsoffire.placebo.dynreg.RegistrySerializer;
import dev.shadowsoffire.placebo.systems.mixes.JsonMix.Type;
import net.minecraft.world.item.alchemy.PotionBrewing;

/** Loader-neutral brewing-mix data and mutation policy. */
public class MixRegistry extends DynamicRegistry<JsonMix<?>> {

    public static final MixRegistry INSTANCE = new MixRegistry();

    private static Supplier<? extends Iterable<@Nullable PotionBrewing>> brewingResolver = List::of;

    public MixRegistry() {
        super(Placebo.LOGGER, Placebo.loc("brewing_mixes"), RegistrySerializer.synced(JsonMix.CODEC));
    }

    /** Installs the loader's view of the current client and server brewing registries. */
    public static void setBrewingResolver(Supplier<? extends Iterable<@Nullable PotionBrewing>> resolver) {
        brewingResolver = Objects.requireNonNull(resolver, "brewing resolver");
    }

    @Override
    protected void beginReload(ReloadType type) {
        resolveBrewing().forEach(this::removeAll);
        super.beginReload(type);
    }

    @Override
    protected void onReload(ReloadType type) {
        resolveBrewing().forEach(this::addAll);
        super.onReload(type);
    }

    /** Applies loaded mixes after the first server reload, which runs before a server brewing registry exists. */
    public static void applyMixes() {
        resolveBrewing().forEach(INSTANCE::addAll);
    }

    private static List<PotionBrewing> resolveBrewing() {
        Set<PotionBrewing> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<PotionBrewing> registries = new ArrayList<>();
        for (PotionBrewing brewing : brewingResolver.get()) {
            if (brewing != null && seen.add(brewing)) {
                registries.add(brewing);
            }
        }
        return registries;
    }

    @SuppressWarnings("unchecked")
    private static List<PotionBrewing.Mix<?>> getMixList(PotionBrewing brewing, Type type) {
        return (List<PotionBrewing.Mix<?>>) (Object) switch (type) {
            case POTION -> brewing.potionMixes;
            case CONTAINER -> brewing.containerMixes;
        };
    }

    private static void makeMutable(PotionBrewing brewing) {
        brewing.containerMixes = new ArrayList<>(brewing.containerMixes);
        brewing.potionMixes = new ArrayList<>(brewing.potionMixes);
    }

    private void removeAll(PotionBrewing brewing) {
        makeMutable(brewing);
        this.getValues().forEach(mix -> getMixList(brewing, mix.type()).remove(mix.mix()));
    }

    private void addAll(PotionBrewing brewing) {
        makeMutable(brewing);
        this.getValues().forEach(mix -> {
            List<PotionBrewing.Mix<?>> mixes = getMixList(brewing, mix.type());
            if (!mixes.contains(mix.mix())) {
                mixes.add(mix.mix());
            }
        });
    }
}
