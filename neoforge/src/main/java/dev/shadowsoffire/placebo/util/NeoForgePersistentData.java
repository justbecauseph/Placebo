package dev.shadowsoffire.placebo.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * {@link PersistentData.Impl} for NeoForge -- the vanilla-patched accessor itself, so nothing about how this
 * data is stored or saved changes.
 */
public class NeoForgePersistentData implements PersistentData.Impl {

    @Override
    public CompoundTag of(Entity entity) {
        return entity.getPersistentData();
    }

}
