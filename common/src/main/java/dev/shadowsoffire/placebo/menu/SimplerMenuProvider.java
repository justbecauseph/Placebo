package dev.shadowsoffire.placebo.menu;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.Level;

/**
 * Boilerplate for creating {@link MenuProvider}s when using {@code BlockEntityMenu}.
 * <p>
 * An {@link ExtendedMenuProvider} rather than a plain one: the position has to reach the client so the menu can
 * be rebuilt there. On NeoForge that used to come from {@code player.openMenu(provider, pos)}, which writes the
 * position for you; Architectury asks for it explicitly, and being explicit is what makes this work on Fabric
 * as well.
 */
public class SimplerMenuProvider<M extends AbstractContainerMenu> implements ExtendedMenuProvider {

    private final Component title;
    private final MenuConstructor menuConstructor;
    private final BlockPos pos;

    public SimplerMenuProvider(Level level, BlockPos pos, PosFactory<M> factory) {
        this.menuConstructor = (id, inv, player) -> factory.create(id, inv, pos);
        this.title = Component.translatable(level.getBlockState(pos).getBlock().getDescriptionId());
        this.pos = pos;
    }

    @Override
    public Component getDisplayName() {
        return this.title;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return this.menuConstructor.createMenu(containerId, inventory, player);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

}
