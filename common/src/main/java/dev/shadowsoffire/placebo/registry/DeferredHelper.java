package dev.shadowsoffire.placebo.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntity;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType.TickSide;
import dev.shadowsoffire.placebo.util.DeferredSet;
import net.minecraft.advancements.triggers.CriterionTrigger;
import dev.architectury.registry.registries.DeferredRegister;
import dev.shadowsoffire.placebo.attachment.DataAttachment;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.EntityFactory;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Helper class that acts as a single point of entry for deferred registration of all registry entries.
 * <p>
 * Provides methods for the most common types of objects, as well as {@link #custom(String, ResourceKey, Supplier)} for other types.
 * <p>
 * Registration factories will only be invoked during registration for the target registry, using the same semantics of {@link DeferredRegister}.
 */
public class DeferredHelper {

    /**
     * Fake resource key for the root registry, used to hold {@link Registry registries} in {@link #objects} until the {@link NewRegistryEvent}.
     */
    protected static final ResourceKey<? extends Registry<?>> ROOT_REGISTRY_KEY = ResourceKey.createRegistryKey(Registries.ROOT_REGISTRY_NAME);

    protected final String modid;

    /** One Architectury {@link DeferredRegister} per target registry, created on demand. */
    protected final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> registers = new java.util.LinkedHashMap<>();

    /** Everything staged through this helper, so {@link #getRegisteredObjects} can report it. */
    protected final Map<ResourceKey<? extends Registry<?>>, List<RegistrySupplier<?>>> suppliers = new java.util.LinkedHashMap<>();

    /**
     * Creates a new DeferredHelper. DeferredHelpers must be registered to the mod event bus via {@link IEventBus#register}
     *
     * @param modid The modid of the owning mod.
     * @return A new DeferredHelper.
     */
    public static DeferredHelper create(String modid) {
        return FACTORY.apply(modid);
    }

    /**
     * Platform factory for {@link #create(String)}. NeoForge installs one returning
     * {@code NeoForgeDeferredHelper}, which adds the registration types that only exist there --
     * attachments, custom ingredients, global loot modifiers, data maps, and menus with extra data.
     */
    private static java.util.function.Function<String, DeferredHelper> FACTORY = DeferredHelper::new;

    public static void setFactory(java.util.function.Function<String, DeferredHelper> factory) {
        FACTORY = factory;
    }

    protected DeferredHelper(String modid) {
        this.modid = modid;
    }

    /**
     * Creates a mod-defined {@link Registry} in this helper's namespace, and registers it with the loader.
     */
    public <T> Registry<T> registry(String path, UnaryOperator<RegistrySpec<T>> config) {
        ResourceKey<Registry<T>> key = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(this.modid, path));
        return REGISTRY_FACTORY.create(this, key, config);
    }

    /**
     * Platform factory for {@link #registry}.
     */
    private static RegistryFactory REGISTRY_FACTORY = new RegistryFactory() {

        @Override
        public <T> Registry<T> create(DeferredHelper owner, ResourceKey<Registry<T>> key,
            UnaryOperator<RegistrySpec<T>> config) {
            throw new IllegalStateException("No registry factory installed; the platform entrypoint must call "
                + "DeferredHelper.setRegistryFactory before any registry is declared.");
        }
    };

    public static void setRegistryFactory(RegistryFactory factory) {
        REGISTRY_FACTORY = java.util.Objects.requireNonNull(factory);
    }

    public interface RegistryFactory {

        /**
         * @param owner The helper the registry was declared on. NeoForge stages the registry there so it is
         *              flushed by that helper's own {@code NewRegistryEvent} listener, rather than on
         *              whichever instance happened to build the factory.
         */
        <T> Registry<T> create(DeferredHelper owner, ResourceKey<Registry<T>> key,
            UnaryOperator<RegistrySpec<T>> config);
    }

    /**
     * Registers a {@link DataAttachment} with the given default value.
     * <p>
     * Common because both loaders have the concept and this stack only ever attaches to entities and block
     * entities, which both support. See {@code DataAttachment} for the one case that would not have
     * transferred -- ItemStack -- and why none exists here.
     */
    public <T> DataAttachment<T> attachment(String path, Supplier<T> defaultValue,
        UnaryOperator<DataAttachment.Builder<T>> config) {
        return ATTACHMENT_FACTORY.create(Identifier.fromNamespaceAndPath(this.modid, path), defaultValue, config);
    }

    /**
     * Platform factory for {@link #attachment}. Installed by the platform entrypoint, like {@link #FACTORY}.
     */
    private static AttachmentFactory ATTACHMENT_FACTORY = new AttachmentFactory() {

        @Override
        public <T> DataAttachment<T> create(Identifier id, Supplier<T> defaultValue,
            UnaryOperator<DataAttachment.Builder<T>> config) {
            // Not a lambda: the method is generic, and a generic method cannot be a lambda target.
            throw new IllegalStateException("No attachment factory installed; the platform entrypoint must "
                + "call DeferredHelper.setAttachmentFactory before any attachment is declared.");
        }
    };

    public static void setAttachmentFactory(AttachmentFactory factory) {
        ATTACHMENT_FACTORY = java.util.Objects.requireNonNull(factory);
    }

    public interface AttachmentFactory {
        <T> DataAttachment<T> create(Identifier id, Supplier<T> defaultValue,
            UnaryOperator<DataAttachment.Builder<T>> config);
    }

    /**
     * Flushes every staged registration. The platform entrypoint calls this once during setup; Architectury
     * handles the per-loader timing from there.
     */
    public void registerAll() {
        this.registers.values().forEach(DeferredRegister::register);
    }


    /**
     * Registers a {@link Block} using a supplier.
     */
    public <T extends Block> RegistrySupplier<T> block(String path, Supplier<T> factory) {
        return this.register(path, Registries.BLOCK, factory);
    }

    /**
     * Registers a {@link Block} with a reference to its constructor, configuring a new {@link Block.Properties} instance with the supplied operator.
     */
    public <T extends Block> RegistrySupplier<T> block(String path, Function<Block.Properties, T> ctor, UnaryOperator<Block.Properties> properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(this.modid, path));
        return this.block(path, () -> ctor.apply(properties.apply(Block.Properties.of()).setId(key)));
    }

    /**
     * Registers a {@link Fluid} using a supplier.
     */
    public <T extends Fluid> RegistrySupplier<T> fluid(String path, Supplier<T> factory) {
        return this.register(path, Registries.FLUID, factory);
    }

    /**
     * Registers an {@link Item} using a supplier.
     */
    public <T extends Item> RegistrySupplier<T> item(String path, Supplier<T> factory) {
        return this.register(path, Registries.ITEM, factory);
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, configuring a new {@link Item.Properties} instance with the supplied operator.
     */
    public <T extends Item> RegistrySupplier<T> item(String path, Function<Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(this.modid, path));
        return item(path, () -> ctor.apply(properties.apply(new Item.Properties()).setId(key)));
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, using a default {@link Item.Properties} instance.
     */
    public <T extends Item> RegistrySupplier<T> item(String path, Function<Item.Properties, T> ctor) {
        return item(path, ctor, UnaryOperator.identity());
    }

    /**
     * Registers a subclass of {@link BlockItem} given a target block, the constructor, and an {@link Item.Properties} factory.
     */
    public <T extends BlockItem> RegistrySupplier<T> blockItem(String path, Holder<Block> block, BiFunction<Block, Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(this.modid, path));
        return item(path, () -> ctor.apply(block.value(), properties.apply(new Item.Properties().useBlockDescriptionPrefix()).setId(key)));
    }

    /**
     * Registers a {@link BlockItem} given a target block and an {@link Item.Properties} factory.
     */
    public RegistrySupplier<BlockItem> blockItem(String path, Holder<Block> block, UnaryOperator<Item.Properties> properties) {
        return blockItem(path, block, BlockItem::new, properties);
    }

    /**
     * Registers a {@link BlockItem} given a target block, using a default {@link Item.Properties} instance.
     */
    public RegistrySupplier<BlockItem> blockItem(String path, Holder<Block> block) {
        return blockItem(path, block, UnaryOperator.identity());
    }

    // ---------------------------------------------------------------------------------------------
    // Supplier-based blockItem overloads.
    //
    // Prefer these when the target block is one of your own registrations. A RegistrySupplier resolves
    // asHolder() through the registrar, which returns null until registration has run -- so calling it
    // in the static initializer that declares your block items bakes in a null and NPEs later, at the
    // point the item is actually built. Taking the supplier itself defers the whole question.
    //
    // Holder does not extend Supplier, so these do not collide with the Holder overloads above; keep
    // those for vanilla blocks, which are already registered.
    // ---------------------------------------------------------------------------------------------

    /**
     * Registers a subclass of {@link BlockItem} given a target block supplier, the constructor, and an
     * {@link Item.Properties} factory.
     */
    public <T extends BlockItem> RegistrySupplier<T> blockItem(String path, Supplier<? extends Block> block, BiFunction<Block, Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(this.modid, path));
        return item(path, () -> ctor.apply(block.get(), properties.apply(new Item.Properties().useBlockDescriptionPrefix()).setId(key)));
    }

    /**
     * Registers a {@link BlockItem} given a target block supplier and an {@link Item.Properties} factory.
     */
    public RegistrySupplier<BlockItem> blockItem(String path, Supplier<? extends Block> block, UnaryOperator<Item.Properties> properties) {
        return blockItem(path, block, BlockItem::new, properties);
    }

    /**
     * Registers a {@link BlockItem} given a target block supplier, using a default {@link Item.Properties} instance.
     */
    public RegistrySupplier<BlockItem> blockItem(String path, Supplier<? extends Block> block) {
        return blockItem(path, block, UnaryOperator.identity());
    }

    /**
     * Registers a {@link MobEffect} using a supplier.
     */
    public <T extends MobEffect> RegistrySupplier<T> effect(String path, Supplier<T> factory) {
        return this.register(path, Registries.MOB_EFFECT, factory);
    }

    /**
     * Registers a {@link SoundEvent} using a supplier.
     */
    public RegistrySupplier<SoundEvent> sound(String path, Supplier<SoundEvent> factory) {
        return this.register(path, Registries.SOUND_EVENT, factory);
    }

    /**
     * Immediately creates and stages for registration a {@link SoundEvent} using the given path via {@link SoundEvent#createVariableRangeEvent}.
     */
    public SoundEvent sound(String path) {
        SoundEvent sound = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(this.modid, path));
        this.sound(path, () -> sound);
        return sound;
    }

    /**
     * Registers a {@link Potion} using a supplier.
     */
    public <T extends Potion> RegistrySupplier<T> potion(String path, Supplier<T> factory) {
        return this.register(path, Registries.POTION, factory);
    }

    /**
     * Registers a {@link Potion} containing only one mob effect, with the language key of the underlying mob effect.
     */
    public RegistrySupplier<Potion> singlePotion(String path, Supplier<MobEffectInstance> factory) {
        return this.register(path, Registries.POTION, () -> {
            MobEffectInstance inst = factory.get();
            Identifier key = inst.getEffect().unwrapKey().orElseThrow().identifier();
            return new Potion(key.toLanguageKey(), inst);
        });
    }

    /**
     * Registers a {@link Potion} containing multiple mob effects, with a language key automatically generated from the path.
     */
    public RegistrySupplier<Potion> multiPotion(String path, Supplier<List<MobEffectInstance>> factory) {
        String key = Identifier.fromNamespaceAndPath(this.modid, path).toLanguageKey("potion");
        return this.register(path, Registries.POTION, () -> new Potion(key, factory.get().toArray(new MobEffectInstance[0])));
    }

    /**
     * Registers an {@link EntityType} using a supplier.
     */
    public <U extends Entity, T extends EntityType<U>> RegistrySupplier<T> entity(String path, Supplier<T> factory) {
        return this.register(path, Registries.ENTITY_TYPE, factory);
    }

    /**
     * Registers an {@link EntityType} given the {@link EntityFactory}, {@link MobCategory}, and a function to configure the type.
     */
    public <T extends Entity> RegistrySupplier<EntityType<T>> entity(String path, EntityFactory<T> factory, MobCategory category, UnaryOperator<EntityType.Builder<T>> op) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(this.modid, path));
        return this.entity(path, () -> op.apply(EntityType.Builder.of(factory, category)).build(key));
    }




    /**
     * Registers a {@link ParticleType} using a supplier.
     */
    public <U extends ParticleOptions, T extends ParticleType<U>> RegistrySupplier<T> particle(String path, Supplier<T> factory) {
        return this.register(path, Registries.PARTICLE_TYPE, factory);
    }

    /**
     * Registers a {@link SimpleParticleType}.
     */
    public SimpleParticleType simpleParticle(String path, boolean overrideLimit) {
        var type = new SimpleParticleType(overrideLimit);
        this.register(path, Registries.PARTICLE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link ParticleType} with custom serialization. Both the codec and stream codec must be provided.
     */
    public <T extends ParticleOptions> ParticleType<T> particle(String path, boolean overrideLimit, Function<ParticleType<T>, MapCodec<T>> codec,
        Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec) {
        var type = new ParticleType<T>(overrideLimit){

            @Override
            public MapCodec<T> codec() {
                return codec.apply(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return streamCodec.apply(this);
            }

        };

        this.register(path, Registries.PARTICLE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link MenuType} using a supplier.
     */
    public <U extends AbstractContainerMenu, T extends MenuType<U>> T menuType(String path, T type) {
        this.register(path, Registries.MENU, () -> type);
        return type;
    }



    /**
     * Registers a {@link RecipeType} using a supplier.
     */
    public <C extends RecipeInput, U extends Recipe<C>, T extends RecipeType<U>> RegistrySupplier<T> recipe(String path, Supplier<T> factory) {
        return this.register(path, Registries.RECIPE_TYPE, factory);
    }


    /**
     * Registers a {@link RecipeSerializer} using a supplier.
     */
    public <I extends RecipeInput, R extends Recipe<I>> RegistrySupplier<RecipeSerializer<R>> recipeSerializer(String path, Supplier<RecipeSerializer<R>> factory) {
        return this.register(path, Registries.RECIPE_SERIALIZER, factory);
    }

    /**
     * Registers an {@link Attribute} using a supplier.
     */
    public <T extends Attribute> RegistrySupplier<T> attribute(String path, Supplier<T> factory) {
        return this.register(path, Registries.ATTRIBUTE, factory);
    }

    /**
     * Registers a {@link RangedAttribute}.
     */
    public RegistrySupplier<RangedAttribute> rangedAttribute(String path, double defaultValue, double min, double max) {
        String key = Identifier.fromNamespaceAndPath(this.modid, path).toLanguageKey("attribute");
        return this.attribute(path, () -> new RangedAttribute(key, defaultValue, min, max));
    }

    /**
     * Registers a {@link StatType} using a supplier.
     */
    public <S, U extends StatType<S>, T extends StatType<U>> RegistrySupplier<T> stat(String path, Supplier<T> factory) {
        return this.register(path, Registries.STAT_TYPE, factory);
    }

    /**
     * Creates a custom stat with the given path and formatter.<br>
     * Calling {@link StatType#get} on {@link Stats#CUSTOM} is required for full registration, for some reason.
     *
     * @see Stats#makeCustomStat
     */
    public Identifier customStat(String path, StatFormatter formatter) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        this.register(path, Registries.CUSTOM_STAT, () -> id, key -> {
            Stats.CUSTOM.get(key, formatter);
        });
        return id;
    }

    /**
     * Registers a {@link Feature} using a supplier.
     */
    public <U extends FeatureConfiguration, T extends Feature<U>> RegistrySupplier<T> feature(String path, Supplier<T> factory) {
        return this.register(path, Registries.FEATURE, factory);
    }


    /**
     * Registers an {@linkplain DataComponentType enchantment effect component} that is configured with the supplied operator.
     * <p>
     * Immediately constructs the {@link DataComponentType} and returns it. Registration is deferred until the appropriate time.
     */
    public <T> DataComponentType<T> enchantmentEffect(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        this.register(path, Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link DataComponentType} that is configured with the supplied operator.
     * <p>
     * Immediately constructs the {@link DataComponentType} and returns it. Registration is deferred until the appropriate time.
     */
    public <T> DataComponentType<T> component(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        this.register(path, Registries.DATA_COMPONENT_TYPE, () -> type);
        return type;
    }



    /**
     * Registers a {@link LootPoolEntryType} and returns it.
     */
    public <T extends LootPoolEntryContainer> MapCodec<T> lootPoolEntry(String path, MapCodec<T> codec) {
        this.register(path, Registries.LOOT_POOL_ENTRY_TYPE, () -> codec);
        return codec;
    }


    /**
     * Registers a codec for a {@link LootItemCondition} and returns the new {@link LootItemConditionType}.
     */
    public <T extends LootItemCondition> MapCodec<T> lootCondition(String path, MapCodec<T> codec) {
        this.register(path, Registries.LOOT_CONDITION_TYPE, () -> codec);
        return codec;
    }


    /**
     * Registers a {@link CriterionTrigger} and returns it.
     */
    public <T extends CriterionTrigger<?>> T criteriaTrigger(String path, T trigger) {
        this.register(path, Registries.TRIGGER_TYPE, () -> trigger);
        return trigger;
    }

    /**
     * Registers an {@link ItemSubPredicate.Type} and returns it.
     */
    public <T extends DataComponentPredicate> DataComponentPredicate.Type<T> componentPredicate(String path, Codec<T> codec) {
        DataComponentPredicate.Type<T> type = new DataComponentPredicate.ConcreteType<>(codec);
        this.register(path, Registries.DATA_COMPONENT_PREDICATE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link StructureProcessor} codec and returns it.
     * <p>
     * As of MC 26.2, {@link Registries#STRUCTURE_PROCESSOR} holds {@link MapCodec MapCodecs} directly instead of
     * {@code StructureProcessorType} instances, and {@link StructureProcessor#codec()} replaced {@code getType()}.
     */
    public <T extends StructureProcessor> MapCodec<T> structureProcessor(String path, MapCodec<T> codec) {
        this.register(path, Registries.STRUCTURE_PROCESSOR, () -> codec);
        return codec;
    }


    /**
     * Registers a custom object to the target registry using a supplier.
     * <p>
     * This method must have a different name than {@link #custom(String, ResourceKey, T)} to resolve generic inference issues with javac.
     */
    public <R, T extends R> RegistrySupplier<T> customDH(String path, ResourceKey<? extends Registry<R>> registry, Supplier<T> factory) {
        return this.register(path, registry, factory);
    }

    /**
     * Stages a custom object for registration to the target registry.
     * <p>
     * This method should be preferred over {@link #custom(String, ResourceKey, Supplier)} when the object's creation does not need to be deferred.
     */
    public <R, T extends R> T custom(String path, ResourceKey<? extends Registry<R>> registry, T object) {
        this.register(path, registry, () -> object);
        return object;
    }

    /**
     * Returns a list of all objects for a given registry that were registered by this {@link DeferredHelper}.
     * <p>
     * If registration for the target registry has not happened yet, this list will be empty.
     */
    @ApiStatus.Experimental
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <R> List<Holder<R>> getRegisteredObjects(ResourceKey<? extends Registry<R>> key) {
        return (List) this.suppliers.getOrDefault(key, List.of()).stream()
            .map(RegistrySupplier::asHolder)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /**
     * Registers a {@link CreativeModeTab} that is configured with the supplied operator.
     * <p>
     * Uses the vanilla two-arg builder. NeoForge's no-arg {@code builder()} is exactly
     * {@code new Builder(Row.TOP, 0)}, so this is behaviour-identical and works on both loaders.
     */
    public RegistrySupplier<CreativeModeTab> creativeTab(String path, UnaryOperator<CreativeModeTab.Builder> operator) {
        return this.register(path, Registries.CREATIVE_MODE_TAB, () -> operator.apply(CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)).build());
    }

    /**
     * Stages the supplier for registration, invoking {@code callback} once the object has been created.
     *
     * @return A supplier for the registered object. Unlike NeoForge's {@code DeferredHolder} this is only
     *         resolvable after {@link #registerAll()} has run -- it cannot be dereferenced at class-init.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <R, T extends R> RegistrySupplier<T> register(String path, ResourceKey<? extends Registry<R>> regKey, Supplier<T> factory, @Nullable Consumer<T> callback) {
        DeferredRegister<R> register = (DeferredRegister<R>) this.registers.computeIfAbsent(regKey,
            k -> DeferredRegister.create(this.modid, (ResourceKey) k));

        Supplier<T> wrapped = callback == null ? factory : () -> {
            T value = factory.get();
            callback.accept(value);
            return value;
        };

        RegistrySupplier<T> supplier = register.register(path, wrapped);
        this.suppliers.computeIfAbsent(regKey, k -> new ArrayList<>()).add(supplier);
        return supplier;
    }

    /**
     * Stages the supplier for registration.
     */
    protected <R, T extends R> RegistrySupplier<T> register(String path, ResourceKey<? extends Registry<R>> regKey, Supplier<T> factory) {
        return this.register(path, regKey, factory, null);
    }


    protected static record Registrar<T>(Identifier id, Supplier<T> factory, @Nullable Consumer<T> callback) {
        protected Registrar(Identifier id, Supplier<T> factory) {
            this(id, factory, null);
        }
    }

}
