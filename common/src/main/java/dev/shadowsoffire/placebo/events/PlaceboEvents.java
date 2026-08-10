package dev.shadowsoffire.placebo.events;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

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

}
