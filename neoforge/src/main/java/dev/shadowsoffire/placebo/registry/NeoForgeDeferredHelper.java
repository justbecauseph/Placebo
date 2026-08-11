package dev.shadowsoffire.placebo.registry;



import com.mojang.serialization.MapCodec;

import dev.architectury.platform.hooks.EventBusesHooks;
import dev.shadowsoffire.placebo.loot.LootModifier;
import dev.shadowsoffire.placebo.loot.NeoForgeLootModifier;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * NeoForge half of {@link DeferredHelper}.
 * <p>
 * Everything here depends on something NeoForge adds to vanilla and Fabric does not have:
 * <ul>
 * <li>attachments, global loot modifiers, data maps, {@code IContainerFactory} menus,
 * and {@code RegistryBuilder}-created registries -- NeoForge-only systems
 * <li>{@code BlockEntityType#getValidBlocks}, {@code MappedRegistry#unfreeze},
 * {@code RecipeType#simple(Identifier)} and the no-arg {@code CreativeModeTab#builder()} -- NeoForge
 * additions to vanilla classes, invisible in imports
 * </ul>
 * Each is a Phase 2b design item rather than a rename.
 */
public class NeoForgeDeferredHelper extends DeferredHelper {

    /**
     * Fake registry key used only to build the data map's own ResourceKey. It does not point at a real
     * registry -- NeoForge data maps are not registry entries.
     */
    static final ResourceKey<Registry<DataMapType<?, ?>>> DATA_MAP_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, "data_map_type"));

    /** Registries staged for the {@link NewRegistryEvent}. */
    private final java.util.List<Registry<?>> pendingRegistries = new java.util.ArrayList<>();

    /** Data map types staged for the {@link RegisterDataMapTypesEvent}. */
    private final java.util.List<DataMapType<?, ?>> pendingDataMaps = new java.util.ArrayList<>();

    public NeoForgeDeferredHelper(String modid) {
        super(modid);
        // Hooks the @SubscribeEvent methods below onto the owning mod's bus. Every consumer used to do this
        // itself with `bus.register(R)`, which meant each one needed an IEventBus in hand -- and that single
        // import was the last thing keeping some registry-object classes on the platform side. Doing it here
        // is also harder to forget: a helper that is never registered silently loses its custom registries
        // and data maps.
        EventBusesHooks.whenAvailable(modid, bus -> bus.register(this));
    }

    <T> void registerRegistry(ResourceKey<? extends Registry<T>> key, Registry<T> registry) {
        this.pendingRegistries.add(registry);
    }

    protected <K, V> void registerDataMap(ResourceKey<? extends DataMapType<?, ?>> key, DataMapType<K, V> type) {
        this.pendingDataMaps.add(type);
    }

    @SubscribeEvent
    public void registerRegistries(NewRegistryEvent e) {
        this.pendingRegistries.forEach(e::register);
        this.pendingRegistries.clear();
    }

    @SubscribeEvent
    public void registerDataMaps(RegisterDataMapTypesEvent e) {
        this.pendingDataMaps.forEach(e::register);
        this.pendingDataMaps.clear();
    }




    /**
     * Registers a codec for an {@link IGlobalLootModifier} and returns it.
     */
    @Override
    protected void registerLootModifier(Identifier id, MapCodec<? extends LootModifier> codec) {
        MapCodec<NeoForgeLootModifier> wrapped = NeoForgeLootModifier.wrap(codec);
        this.register(id.getPath(), NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, () -> wrapped);
    }


}
