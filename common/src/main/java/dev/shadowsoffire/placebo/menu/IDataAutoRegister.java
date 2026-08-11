package dev.shadowsoffire.placebo.menu;

import java.util.function.Consumer;

import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Implement on a {@link BlockEntity} to register menu data slots automatically. */
public interface IDataAutoRegister {
    void registerSlots(Consumer<DataSlot> consumer);
}
