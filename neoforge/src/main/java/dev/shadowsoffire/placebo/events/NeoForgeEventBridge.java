package dev.shadowsoffire.placebo.events;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
     * NeoForge fires this from its patched enchanting-menu cost calculation. Passing it through retains the
     * original NeoForge event for third-party listeners while exposing the same mutable cost to common code.
     */
    @SubscribeEvent
    public void enchantmentLevelSet(EnchantmentLevelSetEvent e) {
        e.setEnchantLevel(PlaceboEvents.fireEnchantmentLevelSet(e.getLevel(), e.getPos(), e.getEnchantRow(), e.getPower(), e.getItem(), e.getEnchantLevel()));
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

    @SubscribeEvent
    public void shieldBlock(LivingShieldBlockEvent e) {
        e.setBlockedDamage(PlaceboEvents.fireShieldBlock(e.getEntity(), e.getDamageSource(), e.getBlockedDamage()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void blockDrops(BlockDropsEvent e) {
        e.setDroppedExperience(PlaceboEvents.fireBlockDrops(e.getLevel(), e.getPos(), e.getState(), e.getBreaker(),
            e.getTool(), e.getDrops(), e.getDroppedExperience()));
    }

    @SubscribeEvent
    public void itemStackedOnOther(ItemStackedOnOtherEvent e) {
        boolean handled = PlaceboEvents.fireItemStackedOnOther(e.getCarriedItem(), e.getStackedOnItem(), e.getSlot(),
            e.getClickAction(), e.getPlayer(), e.getCarriedSlotAccess());
        if (handled) {
            e.setCanceled(true);
        }
    }

    /**
     * The bridge occupies {@code HIGH}, the earliest priority used by a migrated consumer. Listeners on the
     * common event retain their relative priorities; if one cancels, the NeoForge event is cancelled before
     * lower-priority native listeners run, just as it was when that listener subscribed directly.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void projectileImpact(ProjectileImpactEvent e) {
        if (PlaceboEvents.fireProjectileImpact(e.getProjectile(), e.getRayTraceResult())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void entityTeleport(EntityTeleportEvent e) {
        PlaceboEvents.EntityTeleportContext ctx = new PlaceboEvents.EntityTeleportContext(e.getEntity(), e.getTargetLevel(),
            e.getTargetX(), e.getTargetY(), e.getTargetZ());
        if (!PlaceboEvents.fireEntityTeleport(ctx)) {
            e.setCanceled(true);
        }
        else {
            e.setTargetX(ctx.getTargetX());
            e.setTargetY(ctx.getTargetY());
            e.setTargetZ(ctx.getTargetZ());
        }
    }

    /**
     * Bridges at the earliest priority any stack consumer used. Their original relative order now lives on
     * {@link PlaceboEvents#LIVING_INCOMING_DAMAGE}, while external ordering necessarily collapses to this slot.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void incomingDamage(LivingIncomingDamageEvent e) {
        PlaceboEvents.IncomingDamageContext ctx = new PlaceboEvents.IncomingDamageContext(e.getEntity(), e.getSource(), e.getAmount());
        if (PlaceboEvents.fireIncomingDamage(ctx)) {
            e.setCanceled(true);
        }
        else {
            e.setAmount(ctx.getDamage());
        }
    }

    /** See {@link PlaceboEvents#LIVING_DAMAGE_PRE}. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void livingDamagePre(LivingDamageEvent.Pre e) {
        PlaceboEvents.LivingDamagePreContext ctx = new PlaceboEvents.LivingDamagePreContext(e.getEntity(), e.getSource(), e.getOriginalDamage(), e.getNewDamage());
        PlaceboEvents.fireLivingDamagePre(ctx);
        e.setNewDamage(ctx.getDamage());
    }

    /** See {@link PlaceboEvents#LIVING_DAMAGE_POST}. */
    @SubscribeEvent
    public void livingDamagePost(LivingDamageEvent.Post e) {
        PlaceboEvents.fireLivingDamagePost(new PlaceboEvents.LivingDamagePostContext(e.getEntity(), e.getSource(), e.getOriginalDamage(), e.getInflictedDamage(), e.getHealthDamage()));
    }

}
