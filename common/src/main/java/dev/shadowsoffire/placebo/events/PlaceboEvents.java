package dev.shadowsoffire.placebo.events;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import dev.shadowsoffire.placebo.util.MobSpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Loader-neutral events for the cases NeoForge has and Fabric does not.
 * <p>
 * Around 23 NeoForge events across this stack have no Fabric equivalent (see
 * {@code porting/reference/event-mapping.md}). Rather than each mod mixing into vanilla itself, Placebo
 * declares the event here and each platform fires it:
 * <ul>
 * <li><b>NeoForge</b> bridges from its own event — no mixin, no behaviour change, and the NeoForge event
 * keeps working for anyone else listening to it.
 * <li><b>Fabric</b> mixes into the vanilla method NeoForge patches, and fires the same event.
 * </ul>
 * Consumers in {@code common} see one API and never learn which loader they are on.
 * <p>
 * Built on Architectury's {@link EventFactory} rather than a hand-rolled bus: it is already a common
 * dependency, and {@link EventResult} gives cancellation semantics for free.
 * <p>
 * <b>Events that map cleanly are not here.</b> If Fabric or Architectury already has an equivalent, common
 * code should use that directly — adding a passthrough would be indirection for its own sake. This class is
 * only for the gaps.
 */
public class PlaceboEvents {

    /**
     * Fired when a {@link LivingEntity} is about to be healed, before the health change is applied.
     * <p>
     * NeoForge counterpart: {@code LivingHealEvent}. Vanilla site: {@code LivingEntity#heal}.
     * <p>
     * Listeners may change the amount via {@link Heal#setAmount}, and returning
     * {@link EventResult#interruptFalse()} cancels the heal entirely.
     */
    public static final Event<Heal> LIVING_HEAL = EventFactory.createEventResult();

    @FunctionalInterface
    public interface Heal {
        EventResult heal(HealContext ctx);
    }

    /**
     * Mutable state for {@link #LIVING_HEAL}. Mirrors what NeoForge's event exposes, which is all the
     * existing handlers across the stack actually use: the entity and a settable amount.
     */
    public static final class HealContext {

        private final LivingEntity entity;
        private float amount;

        public HealContext(LivingEntity entity, float amount) {
            this.entity = entity;
            this.amount = amount;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public float getAmount() {
            return this.amount;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }
    }

    /**
     * Fires {@link #LIVING_HEAL}. Called by the platform bridge, not by mods.
     *
     * @return the amount to heal by, or a value {@code <= 0} if the heal was cancelled.
     */
    public static float fireLivingHeal(LivingEntity entity, float amount) {
        HealContext ctx = new HealContext(entity, amount);
        EventResult result = LIVING_HEAL.invoker().heal(ctx);
        return result.isFalse() ? 0F : ctx.getAmount();
    }

    /**
     * Fired when a dying {@link LivingEntity} is about to award experience, before the orbs are spawned.
     * <p>
     * NeoForge counterpart: {@code LivingExperienceDropEvent}. Vanilla site: {@code LivingEntity#dropExperience},
     * around the {@code getExperienceReward} call.
     * <p>
     * Note this is <i>only</i> the death drop. {@code getExperienceReward} is also consulted by the sculk
     * catalyst to size its bloom; NeoForge does not fire there, and neither do we.
     * <p>
     * Returning {@link EventResult#interruptFalse()} drops no experience.
     */
    public static final Event<ExperienceDrop> LIVING_EXPERIENCE_DROP = EventFactory.createEventResult();

    @FunctionalInterface
    public interface ExperienceDrop {
        EventResult drop(ExperienceDropContext ctx);
    }

    /**
     * Mutable state for {@link #LIVING_EXPERIENCE_DROP}.
     */
    public static final class ExperienceDropContext {

        private final LivingEntity entity;
        private final @Nullable Player attackingPlayer;
        private final int originalAmount;
        private int amount;

        public ExperienceDropContext(LivingEntity entity, @Nullable Player attackingPlayer, int amount) {
            this.entity = entity;
            this.attackingPlayer = attackingPlayer;
            this.originalAmount = amount;
            this.amount = amount;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        /**
         * The player credited with the kill, or null if the entity was not killed by a player.
         */
        public @Nullable Player getAttackingPlayer() {
            return this.attackingPlayer;
        }

        /**
         * The amount before any listener changed it. Useful for listeners that want to scale rather than set.
         */
        public int getOriginalAmount() {
            return this.originalAmount;
        }

        public int getAmount() {
            return this.amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    /**
     * Fires {@link #LIVING_EXPERIENCE_DROP}. Called by the platform bridge, not by mods.
     *
     * @return the amount of experience to award, or {@code 0} if the drop was cancelled.
     */
    public static int fireLivingExperienceDrop(LivingEntity entity, @Nullable Player attackingPlayer, int amount) {
        ExperienceDropContext ctx = new ExperienceDropContext(entity, attackingPlayer, amount);
        EventResult result = LIVING_EXPERIENCE_DROP.invoker().drop(ctx);
        return result.isFalse() ? 0 : ctx.getAmount();
    }

    /**
     * Fired every tick that a {@link LivingEntity} is actively using an item, before the remaining duration is
     * decremented.
     * <p>
     * NeoForge counterpart: {@code LivingEntityUseItemEvent.Tick}. Vanilla site: {@code LivingEntity#updateUsingItem}.
     * <p>
     * <b>Not cancellable.</b> NeoForge's event is, but cancelling it means "stop using the item", which the two
     * loaders reach by different routes. No handler in this stack cancels, so the event is sized to what they do
     * use: read and adjust the duration. Growing it later is a smaller change than getting the semantics subtly
     * different on one loader now.
     */
    public static final Event<ItemUseTick> ITEM_USE_TICK = EventFactory.createLoop();

    @FunctionalInterface
    public interface ItemUseTick {
        void tick(ItemUseTickContext ctx);
    }

    /**
     * Mutable state for {@link #ITEM_USE_TICK}.
     */
    public static final class ItemUseTickContext {

        private final LivingEntity entity;
        private final ItemStack item;
        private int duration;

        public ItemUseTickContext(LivingEntity entity, ItemStack item, int duration) {
            this.entity = entity;
            this.item = item;
            this.duration = duration;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public ItemStack getItem() {
            return this.item;
        }

        /**
         * Ticks remaining before the item finishes being used.
         */
        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }

    /**
     * Fires {@link #ITEM_USE_TICK}. Called by the platform bridge, not by mods.
     *
     * @return the remaining use duration after any listener adjusted it.
     */
    public static int fireItemUseTick(LivingEntity entity, ItemStack item, int duration) {
        ItemUseTickContext ctx = new ItemUseTickContext(entity, item, duration);
        ITEM_USE_TICK.invoker().tick(ctx);
        return ctx.getDuration();
    }

    /**
     * Fired once per child when a slime or magma cube splits on death.
     * <p>
     * NeoForge counterpart: {@code MobSplitEvent}. Vanilla site: {@code AbstractCubeMob#remove}.
     * <p>
     * <b>Per child, not per split, and not cancellable.</b> NeoForge restructures {@code remove} to collect the
     * children into a list, suppress their spawning, fire once, then spawn the survivors. Vanilla adds each child
     * inside {@code convertTo}, so reproducing that shape on Fabric would mean reproducing the restructure.
     * The only handler in this stack tags each child individually and never cancels, so this fires per child
     * against the entity as already spawned.
     */
    public static final Event<MobSplit> MOB_SPLIT = EventFactory.createLoop();

    @FunctionalInterface
    public interface MobSplit {
        void split(Mob parent, Mob child);
    }

    /**
     * Fires {@link #MOB_SPLIT}. Called by the platform bridge, not by mods.
     */
    public static void fireMobSplit(Mob parent, Mob child) {
        MOB_SPLIT.invoker().split(parent, child);
    }

    /**
     * Fired whenever an item's enchantment levels are requested <i>for gameplay purposes</i>, so that listeners
     * can report levels the item does not literally carry in its enchantment component.
     * <p>
     * NeoForge counterpart: {@code GetEnchantmentLevelEvent}. Vanilla sites: {@code EnchantmentHelper}'s
     * {@code getItemEnchantmentLevel}, both {@code runIterationOnItem} overloads, and {@code hasTag} — the four
     * places NeoForge patches to route through its gameplay-enchantment path.
     * <p>
     * <b>Not fired for NBT reads.</b> Anything that reads the {@code ENCHANTMENTS} component directly — the
     * anvil, the tooltip, {@code getEnchantmentsForCrafting} — sees the unmodified item on both loaders.
     * <p>
     * <b>Not cancellable, and the whole map is always passed.</b> NeoForge's event carries a nullable
     * <i>target</i> enchantment, so a listener querying one enchantment can skip populating the rest. No listener
     * in this stack reads it — they all rebuild the whole map regardless — so it is not on the context, and both
     * loaders therefore do the same work per query. NeoForge's own event allocates a mutable map per call too, so
     * this costs Fabric no more than NeoForge already pays.
     */
    public static final Event<EnchantmentLevels> ENCHANTMENT_LEVELS = EventFactory.createLoop();

    @FunctionalInterface
    public interface EnchantmentLevels {
        void modify(EnchantmentLevelContext ctx);
    }

    /**
     * Mutable state for {@link #ENCHANTMENT_LEVELS}. The enchantment map <i>is</i> the mutable state — listeners
     * change levels by calling {@link ItemEnchantments.Mutable#set} / {@link ItemEnchantments.Mutable#upgrade}
     * on it, exactly as they did on NeoForge.
     */
    public static final class EnchantmentLevelContext {

        private final ItemInstance stack;
        private final ItemEnchantments.Mutable enchantments;

        public EnchantmentLevelContext(ItemInstance stack, ItemEnchantments.Mutable enchantments) {
            this.stack = stack;
            this.enchantments = enchantments;
        }

        /**
         * The item being queried. This is {@link ItemInstance} rather than {@link ItemStack} because the vanilla
         * query methods take the wider type; listeners that need a real stack should pattern-match for one.
         */
        public ItemInstance getStack() {
            return this.stack;
        }

        public ItemEnchantments.Mutable getEnchantments() {
            return this.enchantments;
        }
    }

    /**
     * Fires {@link #ENCHANTMENT_LEVELS} against a map the caller already holds. Called by the platform bridge,
     * not by mods.
     */
    public static void fireEnchantmentLevels(ItemInstance stack, ItemEnchantments.Mutable enchantments) {
        ENCHANTMENT_LEVELS.invoker().modify(new EnchantmentLevelContext(stack, enchantments));
    }

    /**
     * Fires {@link #ENCHANTMENT_LEVELS} for a single enchantment. Called by the platform bridge, not by mods.
     *
     * @return the level of {@code ench} after listeners have run.
     */
    public static int fireSingleEnchantmentLevel(ItemInstance stack, Holder<Enchantment> ench, int level) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(ench, level);
        fireEnchantmentLevels(stack, enchantments);
        return enchantments.getLevel(ench);
    }

    /**
     * Fires {@link #ENCHANTMENT_LEVELS} for an item's whole enchantment map. Called by the platform bridge, not
     * by mods.
     *
     * @return the map after listeners have run.
     */
    public static ItemEnchantments fireAllEnchantmentLevels(ItemInstance stack, ItemEnchantments enchantments) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        fireEnchantmentLevels(stack, mutable);
        return mutable.toImmutable();
    }

    /**
     * Fired when a {@link LivingEntity} dies, once its death loot has been collected but before any of it reaches
     * the world, so that listeners can add, remove or move the drops.
     * <p>
     * NeoForge counterpart: {@code LivingDropsEvent}. Vanilla site: {@code LivingEntity#dropAllDeathLoot}.
     * <p>
     * <b>Vanilla has no list of drops to intercept</b> — it adds each {@link ItemEntity} to the world as it is
     * produced. Both platforms therefore divert the drops into a collection first: NeoForge through its patched
     * {@code Entity#captureDrops}, Fabric through a mixin that does the same thing at the same two call sites.
     * <p>
     * <b>Not cancellable.</b> Nothing in this stack cancels, and on Fabric there is no platform event to cancel
     * — the drops are Placebo's own list until it spawns them. What a listener does need to know is whether
     * anyone <i>else</i> cancelled, which is what {@link LivingDropsContext#willSpawn()} answers.
     */
    public static final Event<LivingDrops> LIVING_DROPS = EventFactory.createLoop();

    @FunctionalInterface
    public interface LivingDrops {
        void drops(LivingDropsContext ctx);
    }

    /**
     * Mutable state for {@link #LIVING_DROPS}. The drop collection is the mutable part; listeners add to it,
     * remove from it, and reposition the entities in it.
     */
    public static final class LivingDropsContext {

        private final LivingEntity entity;
        private final DamageSource source;
        private final Collection<ItemEntity> drops;
        private final boolean willSpawn;

        public LivingDropsContext(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean willSpawn) {
            this.entity = entity;
            this.source = source;
            this.drops = drops;
            this.willSpawn = willSpawn;
        }

        /**
         * The entity that died.
         */
        public LivingEntity getEntity() {
            return this.entity;
        }

        public DamageSource getSource() {
            return this.source;
        }

        /**
         * The drops, which listeners may modify in place.
         */
        public Collection<ItemEntity> getDrops() {
            return this.drops;
        }

        /**
         * Whether these drops are actually going to reach the world.
         * <p>
         * On NeoForge this is false when another mod has cancelled {@code LivingDropsEvent}, and the event is
         * still fired so that listeners doing cleanup rather than loot generation can run. On Fabric there is
         * nothing to cancel, so it is always true.
         * <p>
         * A listener that <i>generates</i> loot, plays a sound, or converts drops into something else must
         * check this; a listener that only tidies up state should not.
         */
        public boolean willSpawn() {
            return this.willSpawn;
        }
    }

    /**
     * Fires {@link #LIVING_DROPS}. Called by the platform bridge, not by mods.
     */
    public static void fireLivingDrops(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean willSpawn) {
        LIVING_DROPS.invoker().drops(new LivingDropsContext(entity, source, drops, willSpawn));
    }

    /**
     * Fired when a {@link Mob} is about to run its natural despawn check, so that listeners can force the
     * despawn or prevent it.
     * <p>
     * NeoForge counterpart: {@code MobDespawnEvent}. Vanilla sites: {@code Mob#checkDespawn} and
     * {@code WitherBoss#checkDespawn}, which overrides it without calling super.
     * <p>
     * <b>Last writer wins, rather than first.</b> Every listener runs and the result is a mutable field, which
     * is NeoForge's behaviour — a low-priority listener overriding a high-priority one is load-bearing here,
     * since Gateways denies despawns for its own mobs from {@code LOWEST} specifically to beat everyone else.
     * Architectury's interrupting {@code EventResult} would have given first-writer-wins and quietly inverted
     * that.
     */
    public static final Event<MobDespawn> MOB_DESPAWN = EventFactory.createLoop();

    @FunctionalInterface
    public interface MobDespawn {
        void check(MobDespawnContext ctx);
    }

    /**
     * What a {@link #MOB_DESPAWN} listener wants to happen, mirroring NeoForge's {@code MobDespawnEvent.Result}.
     */
    public enum DespawnResult {
        /** Run vanilla's despawn logic unchanged. */
        DEFAULT,
        /** Despawn the mob, whatever vanilla would have decided. */
        ALLOW,
        /** Keep the mob, and reset its no-action timer so it is not reconsidered immediately. */
        DENY
    }

    /**
     * Mutable state for {@link #MOB_DESPAWN}.
     */
    public static final class MobDespawnContext {

        private final Mob entity;
        private final ServerLevelAccessor level;
        private DespawnResult result = DespawnResult.DEFAULT;

        public MobDespawnContext(Mob entity, ServerLevelAccessor level) {
            this.entity = entity;
            this.level = level;
        }

        public Mob getEntity() {
            return this.entity;
        }

        public ServerLevelAccessor getLevel() {
            return this.level;
        }

        public DespawnResult getResult() {
            return this.result;
        }

        public void setResult(DespawnResult result) {
            this.result = result;
        }
    }

    /**
     * Fires {@link #MOB_DESPAWN}. Called by the platform bridge, not by mods.
     * <p>
     * Deliberately has no side effects: NeoForge applies the discard and the timer reset itself once its own
     * event carries the result back, so doing it here too would do it twice. Fabric's mixin applies them.
     *
     * @return what the listeners decided.
     */
    public static DespawnResult fireMobDespawn(Mob mob, ServerLevelAccessor level) {
        MobDespawnContext ctx = new MobDespawnContext(mob, level);
        MOB_DESPAWN.invoker().check(ctx);
        return ctx.getResult();
    }

    /**
     * Fired whenever an entity is asked whether it is immune to a damage source, after vanilla has decided and
     * before the answer is used.
     * <p>
     * NeoForge counterpart: {@code EntityInvulnerabilityCheckEvent}. Vanilla site:
     * {@code Entity#isInvulnerableToBase}, whose return NeoForge wraps.
     * <p>
     * This fires on a very hot path — every damage check on every entity — so listeners should return quickly.
     * That is equally true on NeoForge, which posts its event from the same place.
     */
    public static final Event<InvulnerabilityCheck> ENTITY_INVULNERABILITY_CHECK = EventFactory.createLoop();

    @FunctionalInterface
    public interface InvulnerabilityCheck {
        void check(InvulnerabilityContext ctx);
    }

    /**
     * Mutable state for {@link #ENTITY_INVULNERABILITY_CHECK}.
     */
    public static final class InvulnerabilityContext {

        private final Entity entity;
        private final DamageSource source;
        private boolean invulnerable;

        public InvulnerabilityContext(Entity entity, DamageSource source, boolean invulnerable) {
            this.entity = entity;
            this.source = source;
            this.invulnerable = invulnerable;
        }

        public Entity getEntity() {
            return this.entity;
        }

        public DamageSource getSource() {
            return this.source;
        }

        /**
         * What the answer currently is — vanilla's verdict, plus anything an earlier listener changed.
         */
        public boolean isInvulnerable() {
            return this.invulnerable;
        }

        public void setInvulnerable(boolean invulnerable) {
            this.invulnerable = invulnerable;
        }
    }

    /**
     * Fires {@link #ENTITY_INVULNERABILITY_CHECK}. Called by the platform bridge, not by mods.
     *
     * @return whether the entity is invulnerable to the source, after listeners have run.
     */
    public static boolean fireInvulnerabilityCheck(Entity entity, DamageSource source, boolean invulnerable) {
        InvulnerabilityContext ctx = new InvulnerabilityContext(entity, source, invulnerable);
        ENTITY_INVULNERABILITY_CHECK.invoker().check(ctx);
        return ctx.isInvulnerable();
    }

    /**
     * Fired when a {@link LivingEntity} blocks an attack with an item, after vanilla has worked out how much of
     * the damage the item absorbs and before that figure is used.
     * <p>
     * NeoForge counterpart: {@code LivingShieldBlockEvent}. Vanilla site: {@code LivingEntity#applyItemBlocking},
     * around the single {@code BlocksAttacks#resolveBlockedDamage} call.
     * <p>
     * <b>The block itself cannot be prevented, only resized.</b> NeoForge's event can also turn the block off
     * entirely and choose how much durability the item loses — both of which reach into its own damage pipeline,
     * and neither of which any handler in this stack uses. Sizing to what they do use keeps this a value the
     * two loaders can agree on.
     */
    public static final Event<ShieldBlock> LIVING_SHIELD_BLOCK = EventFactory.createLoop();

    @FunctionalInterface
    public interface ShieldBlock {
        void block(ShieldBlockContext ctx);
    }

    /**
     * Mutable state for {@link #LIVING_SHIELD_BLOCK}.
     */
    public static final class ShieldBlockContext {

        private final LivingEntity entity;
        private final DamageSource source;
        private final float originalBlockedDamage;
        private float blockedDamage;

        public ShieldBlockContext(LivingEntity entity, DamageSource source, float blockedDamage) {
            this.entity = entity;
            this.source = source;
            this.originalBlockedDamage = blockedDamage;
            this.blockedDamage = blockedDamage;
        }

        /**
         * The entity doing the blocking. Its {@code getUseItem} is the blocking item.
         */
        public LivingEntity getEntity() {
            return this.entity;
        }

        public DamageSource getDamageSource() {
            return this.source;
        }

        /**
         * How much vanilla decided the item absorbs, before any listener changed it.
         */
        public float getOriginalBlockedDamage() {
            return this.originalBlockedDamage;
        }

        public float getBlockedDamage() {
            return this.blockedDamage;
        }

        public void setBlockedDamage(float blockedDamage) {
            this.blockedDamage = blockedDamage;
        }
    }

    /**
     * Fires {@link #LIVING_SHIELD_BLOCK}. Called by the platform bridge, not by mods.
     *
     * @return how much damage the blocking item absorbs, after listeners have run.
     */
    public static float fireShieldBlock(LivingEntity entity, DamageSource source, float blockedDamage) {
        ShieldBlockContext ctx = new ShieldBlockContext(entity, source, blockedDamage);
        LIVING_SHIELD_BLOCK.invoker().block(ctx);
        return ctx.getBlockedDamage();
    }

    /**
     * Fired when one item is clicked onto another in a container slot, before vanilla's own stacking behaviour
     * runs, so that listeners can define what combining the two means.
     * <p>
     * NeoForge counterpart: {@code ItemStackedOnOtherEvent}. Vanilla site:
     * {@code AbstractContainerMenu#tryItemClickBehaviourOverride}, at HEAD.
     * <p>
     * <b>Interrupting means "handled".</b> A listener that acts on the pair returns
     * {@link EventResult#interruptTrue()}, which stops both the remaining listeners and vanilla's own handling —
     * the same thing NeoForge's cancellation does, and the reason this is an interrupting event where most of
     * the others here are loops. NeoForge additionally lets a listener choose the boolean the vanilla method
     * returns; it defaults to {@code true} and nothing in this stack sets it.
     */
    public static final Event<ItemStackedOnOther> ITEM_STACKED_ON_OTHER = EventFactory.createEventResult();

    @FunctionalInterface
    public interface ItemStackedOnOther {
        EventResult stackedOn(ItemStackedOnOtherContext ctx);
    }

    /**
     * State for {@link #ITEM_STACKED_ON_OTHER}. Nothing here is settable — a listener acts through the slot and
     * the carried-slot access, then reports that it handled the click.
     */
    public static final class ItemStackedOnOtherContext {

        private final ItemStack carriedItem;
        private final ItemStack stackedOnItem;
        private final Slot slot;
        private final ClickAction clickAction;
        private final Player player;
        private final SlotAccess carriedSlotAccess;

        public ItemStackedOnOtherContext(ItemStack carriedItem, ItemStack stackedOnItem, Slot slot,
            ClickAction clickAction, Player player, SlotAccess carriedSlotAccess) {
            this.carriedItem = carriedItem;
            this.stackedOnItem = stackedOnItem;
            this.slot = slot;
            this.clickAction = clickAction;
            this.player = player;
            this.carriedSlotAccess = carriedSlotAccess;
        }

        /**
         * The stack under the cursor — the one being placed onto the other.
         */
        public ItemStack getCarriedItem() {
            return this.carriedItem;
        }

        /**
         * The stack already in the slot.
         */
        public ItemStack getStackedOnItem() {
            return this.stackedOnItem;
        }

        public Slot getSlot() {
            return this.slot;
        }

        public ClickAction getClickAction() {
            return this.clickAction;
        }

        public Player getPlayer() {
            return this.player;
        }

        /**
         * Write access to the carried stack, for a listener that consumes or replaces it.
         */
        public SlotAccess getCarriedSlotAccess() {
            return this.carriedSlotAccess;
        }
    }

    /**
     * Fires {@link #ITEM_STACKED_ON_OTHER}. Called by the platform bridge, not by mods.
     *
     * @return true if a listener handled the click and vanilla should not.
     */
    public static boolean fireItemStackedOnOther(ItemStack carriedItem, ItemStack stackedOnItem, Slot slot,
        ClickAction clickAction, Player player, SlotAccess carriedSlotAccess) {
        ItemStackedOnOtherContext ctx = new ItemStackedOnOtherContext(carriedItem, stackedOnItem, slot, clickAction, player, carriedSlotAccess);
        return ITEM_STACKED_ON_OTHER.invoker().stackedOn(ctx).isTrue();
    }

    /**
     * Fired when a block is broken, once its drops and experience have been worked out and before either
     * reaches the world.
     * <p>
     * NeoForge counterpart: {@code BlockDropsEvent}. Vanilla site: {@code Block#dropResources}, all three
     * overloads.
     * <p>
     * <b>Vanilla builds no list here either</b>, the same problem {@link #LIVING_DROPS} has: {@code popResource}
     * adds each {@link ItemEntity} as it is produced, and the experience is popped from inside each block's own
     * {@code spawnAfterBreak}. Both platforms divert the two into a collection and a counter first — NeoForge
     * through its patched static capture on {@code Block}, Fabric through a mixin doing the same.
     * <p>
     * <b>The experience is captured, not computed.</b> NeoForge asks {@code BlockState#getExpDrop}, which is one
     * of its own additions to vanilla; there is no such method to ask on Fabric, because each block decides its
     * own amount inside {@code spawnAfterBreak}. Intercepting the award is the only way to see the number, and
     * it has the useful property of being whatever the block actually meant to drop.
     * <p>
     * <b>Not cancellable.</b> NeoForge's is — cancelling suppresses the drops, the experience and
     * {@code spawnAfterBreak} together — and nothing in this stack cancels. The bridge therefore does not
     * receive cancelled events, so a third-party cancel keeps these listeners out, exactly as it did when they
     * were on NeoForge's bus directly.
     */
    public static final Event<BlockDrops> BLOCK_DROPS = EventFactory.createLoop();

    @FunctionalInterface
    public interface BlockDrops {
        void drops(BlockDropsContext ctx);
    }

    /**
     * Mutable state for {@link #BLOCK_DROPS}: the drop list and the experience amount.
     * <p>
     * NeoForge's event also carries the {@link net.minecraft.world.level.block.entity.BlockEntity} that was
     * broken. No listener in this stack reads it, so it is not here.
     */
    public static final class BlockDropsContext {

        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState state;
        private final @Nullable Entity breaker;
        private final ItemStack tool;
        private final List<ItemEntity> drops;
        private int experience;

        public BlockDropsContext(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker,
            ItemStack tool, List<ItemEntity> drops, int experience) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.breaker = breaker;
            this.tool = tool;
            this.drops = drops;
            this.experience = experience;
        }

        public ServerLevel getLevel() {
            return this.level;
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public BlockState getState() {
            return this.state;
        }

        /**
         * Whoever broke the block, or null when nothing did — the two {@code dropResources} overloads that take
         * no breaker are used for indirect breaks, such as a block losing its support.
         */
        public @Nullable Entity getBreaker() {
            return this.breaker;
        }

        /**
         * The tool used, or empty for the breaker-less overloads.
         */
        public ItemStack getTool() {
            return this.tool;
        }

        /**
         * The drops, which listeners may modify in place.
         */
        public List<ItemEntity> getDrops() {
            return this.drops;
        }

        public int getDroppedExperience() {
            return this.experience;
        }

        public void setDroppedExperience(int experience) {
            this.experience = experience;
        }
    }

    /**
     * Fires {@link #BLOCK_DROPS}. Called by the platform bridge, not by mods.
     *
     * @return the experience to drop, after listeners have run.
     */
    public static int fireBlockDrops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker,
        ItemStack tool, List<ItemEntity> drops, int experience) {
        BlockDropsContext ctx = new BlockDropsContext(level, pos, state, breaker, tool, drops, experience);
        BLOCK_DROPS.invoker().drops(ctx);
        return ctx.getDroppedExperience();
    }

    /**
     * Fired when a {@link Mob} is about to be given its spawn-time setup — equipment, difficulty scaling, group
     * data — so that listeners can adjust it or replace the mob entirely.
     * <p>
     * NeoForge counterpart: {@code FinalizeSpawnEvent}. Vanilla site: {@code Mob#finalizeSpawn}, at HEAD.
     * <p>
     * <b>This one fires on NeoForge from a mixin too, not from a bridge, and that is the whole point.</b>
     * NeoForge posts its own event from {@code BaseSpawner} and {@code TrialSpawner} and nowhere else —
     * {@code NaturalSpawner} calls {@code Mob#finalizeSpawn} directly, so on NeoForge the event never sees a
     * natural or chunk-generation spawn. Bridging would have reproduced that gap on Fabric, and hooking the
     * vanilla method on Fabric alone would have given Fabric a working feature NeoForge lacks. Hooking it on
     * both is the only option that leaves the two loaders agreeing.
     * <p>
     * The consequence is deliberate and worth knowing: on NeoForge this fires for spawns that
     * {@code FinalizeSpawnEvent} does not see. NeoForge's own event is untouched and still fires for anyone
     * listening to it.
     * <p>
     * <b>Two independent cancellations</b>, because NeoForge has two and both are used here:
     * {@link FinalizeSpawnContext#setCanceled} skips the spawn setup, and
     * {@link FinalizeSpawnContext#setSpawnCancelled} stops the mob reaching the world at all.
     */
    public static final Event<FinalizeSpawn> FINALIZE_SPAWN = EventFactory.createLoop();

    @FunctionalInterface
    public interface FinalizeSpawn {
        void finalizeSpawn(FinalizeSpawnContext ctx);
    }

    /**
     * Mutable state for {@link #FINALIZE_SPAWN}.
     * <p>
     * NeoForge's event also carries the difficulty, the spawn group data and the spawner that produced the mob.
     * Nothing in this stack reads any of them, so they are not here.
     */
    public static final class FinalizeSpawnContext {

        private final Mob entity;
        private final ServerLevelAccessor level;
        private final EntitySpawnReason spawnType;
        private boolean canceled;

        public FinalizeSpawnContext(Mob entity, ServerLevelAccessor level, EntitySpawnReason spawnType) {
            this.entity = entity;
            this.level = level;
            this.spawnType = spawnType;
        }

        public Mob getEntity() {
            return this.entity;
        }

        public ServerLevelAccessor getLevel() {
            return this.level;
        }

        public double getX() {
            return this.entity.getX();
        }

        public double getY() {
            return this.entity.getY();
        }

        public double getZ() {
            return this.entity.getZ();
        }

        /**
         * Why the mob is being spawned. Invader and elite replacement keys off this — natural and
         * chunk-generation spawns only.
         */
        public EntitySpawnReason getSpawnType() {
            return this.spawnType;
        }

        /**
         * Whether the spawn setup itself is cancelled. The mob still spawns; it simply does not get its
         * equipment, difficulty scaling or group data.
         */
        public boolean isCanceled() {
            return this.canceled;
        }

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        /**
         * Whether the mob is blocked from reaching the world. This is separate from {@link #isCanceled()}: a
         * listener replacing the mob with something else wants both.
         * <p>
         * Backed by the platform — NeoForge has a patched field on {@code Mob} that its own
         * {@code addFreshEntity} path honours, and Placebo supplies the equivalent on Fabric.
         */
        public boolean isSpawnCancelled() {
            return MobSpawnHelper.isSpawnCancelled(this.entity);
        }

        public void setSpawnCancelled(boolean cancelled) {
            MobSpawnHelper.setSpawnCancelled(this.entity, cancelled);
        }
    }

    /**
     * Fires {@link #FINALIZE_SPAWN}. Called by the platform mixin, not by mods.
     *
     * @return true if the spawn setup was cancelled and vanilla's should be skipped.
     */
    public static boolean fireFinalizeSpawn(Mob mob, ServerLevelAccessor level, EntitySpawnReason spawnType) {
        FinalizeSpawnContext ctx = new FinalizeSpawnContext(mob, level, spawnType);
        FINALIZE_SPAWN.invoker().finalizeSpawn(ctx);
        return ctx.isCanceled();
    }

    /**
     * Fired when the item in one of a {@link LivingEntity}'s equipment slots has changed.
     * <p>
     * NeoForge counterpart: {@code LivingEquipmentChangeEvent}. Vanilla site:
     * {@code LivingEntity#collectEquipmentChanges}.
     * <p>
     * <b>Fabric has no equivalent</b>, despite {@code ServerLivingEntityEvents} looking like the place for one —
     * checked against the 26.2 checkout, which has allow/after damage, allow/after death and mob conversion,
     * and nothing about equipment.
     * <p>
     * <b>Fires per changed slot, after the whole sweep rather than during it.</b> NeoForge posts from inside the
     * loop, so its listeners can see an entity halfway through updating. Hooking the returned map instead needs
     * no access to the loop's locals — which matters at 26.2, where the jar carries no local variable table —
     * and nothing that could observe the difference does.
     * <p>
     * The previous stack is not on the context. NeoForge's event carries it; the one handler in this stack reads
     * the slot and the new stack only.
     */
    public static final Event<EquipmentChange> LIVING_EQUIPMENT_CHANGE = EventFactory.createLoop();

    @FunctionalInterface
    public interface EquipmentChange {
        void changed(LivingEntity entity, EquipmentSlot slot, ItemStack to);
    }

    /**
     * Fires {@link #LIVING_EQUIPMENT_CHANGE} once per changed slot. Called by the platform bridge, not by mods.
     */
    public static void fireEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack to) {
        LIVING_EQUIPMENT_CHANGE.invoker().changed(entity, slot, to);
    }

    /**
     * Fired immediately before a projectile applies a non-miss hit result.
     * <p>
     * NeoForge counterpart: {@code ProjectileImpactEvent}. Vanilla implements its seven projectile families
     * differently, so Fabric fires this at their individual {@code hitTargetOrDeflectSelf} call sites rather
     * than trying to turn {@code Projectile} itself into a global hook.
     * <p>
     * Returning {@link EventResult#interruptFalse()} cancels the impact and lets the projectile continue on
     * its vanilla path. The context is deliberately only the projectile and hit result: those are the only
     * members consumed anywhere in this stack.
     */
    public static final Event<ProjectileImpact> PROJECTILE_IMPACT = EventFactory.createEventResult();

    @FunctionalInterface
    public interface ProjectileImpact {
        EventResult impact(Projectile projectile, HitResult hitResult);
    }

    /**
     * Fires {@link #PROJECTILE_IMPACT}. Called by the platform bridge, not by mods.
     *
     * @return true when vanilla should skip this impact.
     */
    public static boolean fireProjectileImpact(Projectile projectile, HitResult hitResult) {
        return PROJECTILE_IMPACT.invoker().impact(projectile, hitResult).isFalse();
    }

    /**
     * Fired after an entity completes its regular tick, including while it is riding another entity.
     * <p>
     * This mirrors NeoForge's {@code EntityTickEvent.Post}. It intentionally has no cancellation: the
     * vanilla tick has already completed by the time listeners run.
     */
    public static final Event<EntityTickPost> ENTITY_TICK_POST = EventFactory.createLoop();

    @FunctionalInterface
    public interface EntityTickPost {
        void tick(Entity entity);
    }

    /**
     * Fires {@link #ENTITY_TICK_POST}. Called by the platform bridge, not by mods.
     */
    public static void fireEntityTickPost(Entity entity) {
        ENTITY_TICK_POST.invoker().tick(entity);
    }

    /**
     * Fired after vanilla's early rejection checks, before blocking, armor/magic reductions, absorption, and
     * health mutation. This mirrors NeoForge's mutable {@code LivingIncomingDamageEvent}; Fabric's analogous
     * callback is boolean-only and cannot carry a modified damage amount.
     */
    public static final Event<IncomingDamage> LIVING_INCOMING_DAMAGE = EventFactory.createEventResult();

    @FunctionalInterface
    public interface IncomingDamage {
        EventResult damage(IncomingDamageContext ctx);
    }

    /** Mutable damage context shared by the incoming-damage bridge. */
    public static final class IncomingDamageContext {

        private final LivingEntity entity;
        private final DamageSource source;
        private final float originalDamage;
        private float damage;

        public IncomingDamageContext(LivingEntity entity, DamageSource source, float damage) {
            this.entity = entity;
            this.source = source;
            this.originalDamage = damage;
            this.damage = damage;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public DamageSource getSource() {
            return this.source;
        }

        public float getOriginalDamage() {
            return this.originalDamage;
        }

        public float getDamage() {
            return this.damage;
        }

        public void setDamage(float damage) {
            this.damage = damage;
        }
    }

    /**
     * Fires {@link #LIVING_INCOMING_DAMAGE}.
     *
     * @return true if vanilla should cancel the damage sequence.
     */
    public static boolean fireIncomingDamage(IncomingDamageContext ctx) {
        return LIVING_INCOMING_DAMAGE.invoker().damage(ctx).isFalse();
    }

    /**
     * Fired after vanilla has applied armor and magic reductions but before it consumes absorption or changes
     * health. This mirrors NeoForge's mutable {@code LivingDamageEvent.Pre}.
     */
    public static final Event<LivingDamagePre> LIVING_DAMAGE_PRE = EventFactory.createLoop();

    @FunctionalInterface
    public interface LivingDamagePre {
        void damage(LivingDamagePreContext ctx);
    }

    /** Mutable context for the post-mitigation, pre-absorption damage phase. */
    public static final class LivingDamagePreContext {

        private final LivingEntity entity;
        private final DamageSource source;
        private final float originalDamage;
        private float damage;

        public LivingDamagePreContext(LivingEntity entity, DamageSource source, float originalDamage, float damage) {
            this.entity = entity;
            this.source = source;
            this.originalDamage = originalDamage;
            this.damage = damage;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public DamageSource getSource() {
            return this.source;
        }

        public float getOriginalDamage() {
            return this.originalDamage;
        }

        public float getDamage() {
            return this.damage;
        }

        public void setDamage(float damage) {
            this.damage = damage;
        }
    }

    /** Fires {@link #LIVING_DAMAGE_PRE}. */
    public static void fireLivingDamagePre(LivingDamagePreContext ctx) {
        LIVING_DAMAGE_PRE.invoker().damage(ctx);
    }

    /**
     * Fired after vanilla has consumed absorption and applied the remaining damage to health. This mirrors the
     * values consumed by NeoForge's {@code LivingDamageEvent.Post}.
     */
    public static final Event<LivingDamagePost> LIVING_DAMAGE_POST = EventFactory.createLoop();

    @FunctionalInterface
    public interface LivingDamagePost {
        void damage(LivingDamagePostContext ctx);
    }

    /** Final values from one post-mitigation damage sequence. */
    public static final class LivingDamagePostContext {

        private final LivingEntity entity;
        private final DamageSource source;
        private final float originalDamage;
        private final float inflictedDamage;
        private final float healthDamage;

        public LivingDamagePostContext(LivingEntity entity, DamageSource source, float originalDamage, float inflictedDamage, float healthDamage) {
            this.entity = entity;
            this.source = source;
            this.originalDamage = originalDamage;
            this.inflictedDamage = inflictedDamage;
            this.healthDamage = healthDamage;
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public DamageSource getSource() {
            return this.source;
        }

        public float getOriginalDamage() {
            return this.originalDamage;
        }

        /** Damage after Pre listeners and before absorption. */
        public float getInflictedDamage() {
            return this.inflictedDamage;
        }

        /** Damage that was ultimately applied to health after absorption. */
        public float getHealthDamage() {
            return this.healthDamage;
        }
    }

    /** Fires {@link #LIVING_DAMAGE_POST}. */
    public static void fireLivingDamagePost(LivingDamagePostContext ctx) {
        LIVING_DAMAGE_POST.invoker().damage(ctx);
    }

}
