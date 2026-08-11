package dev.shadowsoffire.placebo.menu;

import java.util.function.Predicate;

import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import dev.shadowsoffire.placebo.menu.QuickMoveHandler.QuickMoveMenu;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Base menu implementation with quick-move and data-listener utilities. */
public abstract class PlaceboContainerMenu extends AbstractContainerMenu implements QuickMoveMenu {

    protected final Level level;
    protected final QuickMoveHandler mover = new QuickMoveHandler();
    protected int playerInvStart = -1, hotbarStart = -1;

    protected PlaceboContainerMenu(MenuType<?> type, int id, Inventory playerInventory) {
        super(type, id);
        this.level = playerInventory.player.level();
    }

    protected void addPlayerSlots(Inventory playerInventory, int x, int y) {
        this.playerInvStart = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        this.hotbarStart = this.slots.size();
        for (int row = 0; row < 9; row++) {
            this.addSlot(new Slot(playerInventory, row, x + row * 18, y + 58));
        }
    }

    protected void registerInvShuffleRules() {
        if (this.hotbarStart == -1 || this.playerInvStart == -1) {
            throw new UnsupportedOperationException("Attempted to register inv shuffle rules with no player inv slots.");
        }
        this.mover.registerRule((stack, slot) -> slot >= this.hotbarStart, this.playerInvStart, this.hotbarStart);
        this.mover.registerRule((stack, slot) -> slot >= this.playerInvStart, this.hotbarStart, this.slots.size());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return this.mover.quickMoveStack(this, player, index);
    }

    @Override
    public boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }

    /** Also notifies local data listeners when the client receives a data packet. */
    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        this.updateDataSlotListeners(id, data);
    }

    public void addDataListener(IDataUpdateListener listener) {
        this.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}

            @Override
            public void dataChanged(AbstractContainerMenu menu, int slot, int value) {
                listener.dataUpdated(slot, value);
            }
        });
    }

    protected class UpdatingSlot extends FilteredSlot {
        public UpdatingSlot(InternalItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
            super(handler, index, x, y, filter);
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack);
            PlaceboContainerMenu.this.slotsChanged(null);
        }
    }
}
