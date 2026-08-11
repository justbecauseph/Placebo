package dev.shadowsoffire.placebo.menu;

import java.util.function.Predicate;

import com.google.common.base.Predicates;

import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A vanilla slot with a player-placement filter independent of automation rules. */
public class FilteredSlot extends Slot {

    protected final Predicate<ItemStack> filter;

    public FilteredSlot(InternalItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
        super(handler, index, x, y);
        this.filter = filter;
    }

    public FilteredSlot(InternalItemHandler handler, int index, int x, int y) {
        this(handler, index, x, y, Predicates.alwaysTrue());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.filter.test(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return ((InternalItemHandler) this.container).getSlotLimit(this.getContainerSlot(), stack);
    }
}
