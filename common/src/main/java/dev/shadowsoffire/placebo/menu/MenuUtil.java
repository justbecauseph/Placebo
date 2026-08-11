package dev.shadowsoffire.placebo.menu;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.menu.MenuRegistry.ExtendedMenuTypeFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;

/**
 * Menu construction and opening, loader-neutrally.
 * <p>
 * This was platform-side on the reasoning that NeoForge passes a raw buffer through {@code IContainerFactory}
 * while Fabric passes a typed payload through {@code ExtendedScreenHandlerType} — two genuinely different
 * designs. That is true of the <i>raw</i> Fabric API and irrelevant here, because Architectury already bridges
 * it and is already a dependency on both loaders: {@link MenuRegistry#ofExtended} takes exactly
 * {@code IContainerFactory}'s shape, and {@link dev.architectury.registry.menu.ExtendedMenuProvider} is the
 * opening half.
 * <p>
 * The one real difference is the buffer type — Architectury's factory takes {@link FriendlyByteBuf} where
 * NeoForge's takes {@code RegistryFriendlyByteBuf}. Every menu in this stack reads only positions and ints, so
 * the narrower type costs nothing. A menu that needed to read an {@code ItemStack} would have to say so.
 */
public class MenuUtil {

    /**
     * Creates a {@link MenuType} with the target menu supplier and the vanilla feature flags.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> type(MenuSupplier<T> factory) {
        return new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS);
    }

    /**
     * Creates a {@link MenuType} for a menu that reads extra data from the opening buffer.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> bufType(ExtendedMenuTypeFactory<T> factory) {
        return MenuRegistry.ofExtended(factory);
    }

    /**
     * The common case of {@link #bufType}: a menu whose only extra data is a {@link BlockPos}.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> posType(PosFactory<T> factory) {
        return MenuRegistry.ofExtended(factory);
    }

    /**
     * Opens a position-carrying menu for the player, returning the result the block's {@code useWithoutItem}
     * should hand back.
     */
    public static <M extends AbstractContainerMenu> InteractionResult openGui(Player player, BlockPos pos, PosFactory<M> factory) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuRegistry.openExtendedMenu((ServerPlayer) player, new SimplerMenuProvider<>(player.level(), pos, factory));
        return InteractionResult.CONSUME;
    }

    /**
     * A menu factory whose extra data is a single {@link BlockPos}.
     * <p>
     * It no longer extends a platform interface — it is its own type with a default that reads the position off
     * the buffer, which is all {@code IContainerFactory} was ever providing here.
     */
    @FunctionalInterface
    public static interface PosFactory<T extends AbstractContainerMenu> extends ExtendedMenuTypeFactory<T> {

        T create(int id, Inventory inv, BlockPos pos);

        @Override
        default T create(int id, Inventory inv, FriendlyByteBuf buf) {
            return this.create(id, inv, buf.readBlockPos());
        }
    }

}
