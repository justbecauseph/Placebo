package dev.shadowsoffire.placebo.menu;

import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu implementation that retrieves the target block entity from the provided position.
 *
 * @param <T> The type of the target block entity.
 * @see MenuUtil#posType(PosFactory)
 */
public abstract class BlockEntityMenu<T extends BlockEntity> extends PlaceboContainerMenu {

    protected final BlockPos pos;
    protected final T tile;

    @SuppressWarnings("unchecked")
    protected BlockEntityMenu(MenuType<?> type, int id, Inventory pInv, BlockPos pos) {
        super(type, id, pInv);
        this.pos = pos;
        this.tile = (T) this.level.getBlockEntity(pos);
        if (this.tile instanceof IDataAutoRegister autoRegister) {
            autoRegister.registerSlots(this::addDataSlot);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tile != null && this.tile.getType().isValid(this.level.getBlockState(this.pos));
    }
}
