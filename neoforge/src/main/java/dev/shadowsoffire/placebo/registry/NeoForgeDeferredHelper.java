package dev.shadowsoffire.placebo.registry;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.architectury.platform.hooks.EventBusesHooks;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntity;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType.TickSide;
import dev.shadowsoffire.placebo.menu.MenuUtil;
import net.minecraft.world.inventory.MenuType.MenuSupplier;
import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import dev.shadowsoffire.placebo.util.DeferredSet;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.network.IContainerFactory;
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
 * <li>attachments, custom ingredients, global loot modifiers, data maps, {@code IContainerFactory} menus,
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
    private static final ResourceKey<Registry<DataMapType<?, ?>>> DATA_MAP_KEY =
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
     * Registers a {@link MenuType} for the provided {@link IContainerFactory}.
     */
    public <T extends AbstractContainerMenu> MenuType<T> menuWithData(String path, IContainerFactory<T> factory) {
        return this.menuType(path, MenuUtil.bufType(factory));
    }



    /**
     * Registers a codec for an {@link IGlobalLootModifier} and returns it.
     */
    public <T extends IGlobalLootModifier> MapCodec<T> lootModifier(String path, MapCodec<T> codec) {
        this.register(path, NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, () -> codec);
        return codec;
    }

    /**
     * Registers an {@link IngredientType} and returns it.
     */
    public <T extends ICustomIngredient> IngredientType<T> ingredient(String path, IngredientType<T> type) {
        this.register(path, NeoForgeRegistries.Keys.INGREDIENT_TYPES, () -> type);
        return type;
    }

    /**
     * Creates and returns a {@link DataMapType} for the {@code targetRegistry}.
     * <p>
     * The data map type will be automatically registered during the {@link RegisterDataMapTypesEvent}.
     *
     * @param <K>            The key type of the data map, which is also the type of the target registry.
     * @param <V>            The value type of the data map.
     * @param path           The path of the resource location for the data map type. The map will always use the {@link #modid} as the namespace.
     * @param targetRegistry The registry that the data map is for.
     * @param codec          The codec used to de/serialize the data map objects.
     * @param config         A builder config used to specify other values.
     * @return The newly created data map type.
     */
    @SuppressWarnings("unchecked") // DataMapType has a bug in that it expects ResourceKey<Registry<K>> instead of ? extends Registry.
    public <K, V> DataMapType<K, V> dataMap(String path, ResourceKey<? extends Registry<K>> targetRegistry, Codec<V> codec, UnaryOperator<DataMapType.Builder<V, K>> config) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        ResourceKey<? extends DataMapType<?, ?>> registryKey = ResourceKey.create(DATA_MAP_KEY, id);
        DataMapType<K, V> dataMapType = config.apply(DataMapType.builder(id, (ResourceKey<Registry<K>>) targetRegistry, codec)).build();
        this.registerDataMap(registryKey, dataMapType);
        return dataMapType;
    }

    /**
     * Registers a {@link MenuType} for the provided {@link MenuSupplier}.
     */
    public <T extends AbstractContainerMenu> MenuType<T> menu(String path, MenuSupplier<T> factory) {
        return this.menuType(path, MenuUtil.type(factory));
    }

    /**
     * Registers a {@link MenuType} for the provided {@link PosFactory}.
     */
    public <T extends AbstractContainerMenu> MenuType<T> menuWithPos(String path, PosFactory<T> factory) {
        return this.menuType(path, MenuUtil.posType(factory));
    }


    /**
     * Registers a {@link BlockEntityType} given the {@link BlockEntitySupplier} and a supplier to the set of valid blocks.
     */
    public <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> blockEntity(String path, BlockEntitySupplier<T> factory, Supplier<Set<Block>> validBlocks) {
        return this.register(path, Registries.BLOCK_ENTITY_TYPE, () -> new BlockEntityType<T>(factory, validBlocks.get()));
    }

    /**
     * Registers a {@link BlockEntityType} given the {@link BlockEntitySupplier} and a vararg array of valid blocks.
     * <p>
     * Immediately constructs the {@link BlockEntityType} and returns it. Registration is deferred until the appropriate time. The set of valid blocks will not
     * attempt to be resolved until registration.
     */
    @SafeVarargs
    public final <T extends BlockEntity> BlockEntityType<T> blockEntity(String path, BlockEntitySupplier<T> factory, Holder<Block>... validBlocks) {
        return this.eagerBlockEntity(path, factory, () -> Arrays.stream(validBlocks).map(Holder::value).collect(Collectors.toSet()));
    }

    /**
     * Registers a {@link BlockEntityType} given the {@link BlockEntitySupplier} and a vararg array of block suppliers.
     * <p>
     * Prefer this over the {@link Holder} vararg form for your own blocks: {@code asHolder()} returns null until
     * registration has run, and these calls sit in static initializers, so the holder captured here would be null
     * and would NPE when the valid-block set resolves. Taking the suppliers defers that entirely.
     */
    @SafeVarargs
    public final <T extends BlockEntity> BlockEntityType<T> blockEntity(String path, BlockEntitySupplier<T> factory, Supplier<? extends Block>... validBlocks) {
        return this.eagerBlockEntity(path, factory, () -> Arrays.stream(validBlocks).map(Supplier::get).collect(Collectors.toSet()));
    }

    /**
     * Shared implementation for the vararg {@code blockEntity} overloads.
     * <p>
     * Immediately constructs the {@link BlockEntityType} and returns it. Registration is deferred until the appropriate
     * time. The set of valid blocks will not attempt to be resolved until registration.
     */
    private <T extends BlockEntity> BlockEntityType<T> eagerBlockEntity(String path, BlockEntitySupplier<T> factory, Supplier<Set<Block>> validBlocks) {
        unfreezeBETypeRegistry();
        BlockEntityType<T> type = new BlockEntityType<>(factory, new DeferredSet<>(validBlocks));
        this.register(path, Registries.BLOCK_ENTITY_TYPE, () -> {
            type.getValidBlocks(); // Force resolution of the DeferredSet during registration
            return type;
        });
        return type;
    }

    /**
     * Registers a {@link TickingBlockEntityType} for a {@link TickingBlockEntity} given the {@link BlockEntitySupplier}, the target {@link TickSide}, and a vararg
     * array of valid blocks.
     * <p>
     * Immediately constructs the {@link BlockEntityType} and returns it. Registration is deferred until the appropriate time. The set of valid blocks will not
     * attempt to be resolved until registration.
     */
    @SafeVarargs
    public final <T extends BlockEntity & TickingBlockEntity> TickingBlockEntityType<T> tickingBlockEntity(String path, BlockEntitySupplier<T> factory, TickSide side, Holder<Block>... validBlocks) {
        return this.tickingBlockEntity(path, factory, side, () -> Arrays.stream(validBlocks).map(Holder::value).collect(Collectors.toSet()));
    }

    /**
     * Registers a {@link TickingBlockEntityType} given a vararg array of block suppliers.
     * <p>
     * Prefer this over the {@link Holder} vararg form for your own blocks -- see the note on
     * {@link #blockEntity(String, BlockEntitySupplier, Supplier...)}. {@code asHolder()} returns null until
     * registration has run, and these calls sit in static initializers.
     */
    @SafeVarargs
    public final <T extends BlockEntity & TickingBlockEntity> TickingBlockEntityType<T> tickingBlockEntity(String path, BlockEntitySupplier<T> factory, TickSide side, Supplier<? extends Block>... validBlocks) {
        return this.tickingBlockEntity(path, factory, side, () -> Arrays.stream(validBlocks).map(Supplier::get).collect(Collectors.toSet()));
    }

    /** Shared implementation for the vararg {@code tickingBlockEntity} overloads. */
    private <T extends BlockEntity & TickingBlockEntity> TickingBlockEntityType<T> tickingBlockEntity(String path, BlockEntitySupplier<T> factory, TickSide side, Supplier<Set<Block>> validBlocks) {
        unfreezeBETypeRegistry();
        TickingBlockEntityType<T> type = new TickingBlockEntityType<>(factory, new DeferredSet<>(validBlocks), side);
        this.register(path, Registries.BLOCK_ENTITY_TYPE, () -> {
            type.getValidBlocks(); // Force resolution of the DeferredSet during registration
            return type;
        });
        return type;
    }

    /**
     * Registers a {@link RecipeType} using {@link RecipeType#simple(Identifier)}.
     * <p>
     * Immediately constructs the {@link RecipeType} and returns it. Registration is deferred until the appropriate time.
     */
    public <C extends RecipeInput, U extends Recipe<C>> RecipeType<U> recipe(String path) {
        RecipeType<U> type = RecipeType.simple(Identifier.fromNamespaceAndPath(this.modid, path));
        this.recipe(path, () -> type);
        return type;
    }


    /**
     * BE Types have an intrusive holder, so on top of {@link DeferredSet}, we also need to unfreeze the registry to construct them.
     */
    @SuppressWarnings("deprecation")
    private static void unfreezeBETypeRegistry() {
        ((MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE).unfreeze(false);
    }

}
