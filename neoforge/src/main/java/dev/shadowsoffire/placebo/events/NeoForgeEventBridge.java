package dev.shadowsoffire.placebo.events;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/**
 * Bridges NeoForge's events onto {@link PlaceboEvents}.
 * <p>
 * Deliberately a bridge rather than a mixin: NeoForge already fires these, so reusing them costs nothing,
 * changes no behaviour, and leaves the NeoForge event intact for anyone else listening. Only Fabric needs
 * the mixin.
 */
public class NeoForgeEventBridge {

    @SubscribeEvent
    public void livingHeal(LivingHealEvent e) {
        float result = PlaceboEvents.fireLivingHeal(e.getEntity(), e.getAmount());
        if (result <= 0F) {
            e.setCanceled(true);
        }
        else {
            e.setAmount(result);
        }
    }

    @SubscribeEvent
    public void experienceDrop(LivingExperienceDropEvent e) {
        int result = PlaceboEvents.fireLivingExperienceDrop(e.getEntity(), e.getAttackingPlayer(), e.getDroppedExperience());
        if (result <= 0) {
            e.setCanceled(true);
        }
        else {
            e.setDroppedExperience(result);
        }
    }

    @SubscribeEvent
    public void itemUseTick(LivingEntityUseItemEvent.Tick e) {
        e.setDuration(PlaceboEvents.fireItemUseTick(e.getEntity(), e.getItem(), e.getDuration()));
    }

    /**
     * NeoForge fires this once for the whole split; {@link PlaceboEvents#MOB_SPLIT} is per child. See that field
     * for why the common event is shaped that way.
     */
    @SubscribeEvent
    public void mobSplit(MobSplitEvent e) {
        for (Mob child : e.getChildren()) {
            PlaceboEvents.fireMobSplit(e.getParent(), child);
        }
    }

    /**
     * NeoForge's event already hands over a mutable map and reads it back afterwards, so the bridge only has to
     * pass it through.
     * <p>
     * Subscribed at {@link EventPriority#HIGH} because the whole common chain now occupies a single slot on
     * NeoForge's bus, and the earliest consumer that used to subscribe directly (Apotheosis's affix and gem
     * boosting) was at {@code HIGH}. Consumers keep their order relative to <i>each other</i> through
     * Architectury's own {@link dev.architectury.event.EventPriority} when registering on
     * {@link PlaceboEvents#ENCHANTMENT_LEVELS}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void enchantmentLevels(GetEnchantmentLevelEvent e) {
        PlaceboEvents.fireEnchantmentLevels(e.getStack(), e.getEnchantments());
    }

    /**
     * Subscribed with {@code receiveCanceled} so that cleanup listeners still run when another mod suppresses
     * the drops, which is what NeoForge's own {@code receiveCanceled = true} handlers were doing before they
     * moved here. Listeners that generate loot check {@link PlaceboEvents.LivingDropsContext#willSpawn()}
     * instead of relying on not being called.
     */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public void livingDrops(LivingDropsEvent e) {
        PlaceboEvents.fireLivingDrops(e.getEntity(), e.getSource(), e.getDrops(), !e.isCanceled());
    }

    /**
     * The result is carried back rather than acted on: NeoForge's own {@code checkMobDespawn} applies the
     * discard and the timer reset after the event returns, so doing it here would do it twice.
     */
    @SubscribeEvent
    public void mobDespawn(MobDespawnEvent e) {
        switch (PlaceboEvents.fireMobDespawn(e.getEntity(), e.getLevel())) {
            case ALLOW -> e.setResult(MobDespawnEvent.Result.ALLOW);
            case DENY -> e.setResult(MobDespawnEvent.Result.DENY);
            case DEFAULT -> {}
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void invulnerabilityCheck(EntityInvulnerabilityCheckEvent e) {
        e.setInvulnerable(PlaceboEvents.fireInvulnerabilityCheck(e.getEntity(), e.getSource(), e.isInvulnerable()));
    }

    @SubscribeEvent
    public void shieldBlock(LivingShieldBlockEvent e) {
        e.setBlockedDamage(PlaceboEvents.fireShieldBlock(e.getEntity(), e.getDamageSource(), e.getBlockedDamage()));
    }

    /**
     * The common event's interrupt maps onto cancellation; NeoForge's separate "cancellation result" is left at
     * its default of true, which is what cancelling meant for every handler that moved here.
     */
    @SubscribeEvent
    public void itemStackedOnOther(ItemStackedOnOtherEvent e) {
        boolean handled = PlaceboEvents.fireItemStackedOnOther(e.getCarriedItem(), e.getStackedOnItem(), e.getSlot(),
            e.getClickAction(), e.getPlayer(), e.getCarriedSlotAccess());
        if (handled) {
            e.setCanceled(true);
        }
    }

}
