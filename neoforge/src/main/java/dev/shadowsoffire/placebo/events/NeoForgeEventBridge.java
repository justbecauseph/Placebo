package dev.shadowsoffire.placebo.events;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
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

}
