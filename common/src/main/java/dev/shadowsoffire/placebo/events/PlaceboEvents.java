package dev.shadowsoffire.placebo.events;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ServerLevelAccessor;

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

}
