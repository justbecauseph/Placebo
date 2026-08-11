package dev.shadowsoffire.placebo.cap;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.placebo.menu.FilteredSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A loader-neutral, fixed-size item container used by menus and block entities.
 * Platform transfer APIs should wrap this container at their registration boundary.
 *
 * <p>Menu slots intentionally access the vanilla {@link SimpleContainer} methods directly,
 * allowing their placement rules to differ from automation's {@link #canPlaceItem} rules.
 *
 * @see FilteredSlot
 */
public class InternalItemHandler extends SimpleContainer {

    private static final Codec<List<ItemStack>> STACKS_CODEC = ItemStack.OPTIONAL_CODEC.listOf();
    private static final Codec<List<ItemStack>> SERIALIZED_CODEC = STACKS_CODEC.fieldOf("stacks").codec();

    public InternalItemHandler(int size) {
        super(size);
    }

    /** Preserves the former ItemStacksResourceHandler disk shape under {@code key}. */
    public void serialize(ValueOutput output, String key) {
        output.store(key, SERIALIZED_CODEC, this.copyContents());
    }

    /** Reads the former ItemStacksResourceHandler disk shape from {@code key}. */
    public void deserialize(ValueInput input, String key) {
        input.read(key, SERIALIZED_CODEC).ifPresent(this::loadContents);
    }

    public List<ItemStack> copyContents() {
        List<ItemStack> contents = new ArrayList<>(this.getContainerSize());
        for (int i = 0; i < this.getContainerSize(); i++) {
            contents.add(this.getItem(i).copy());
        }
        return contents;
    }

    public void loadContents(List<ItemStack> contents) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.getItems().set(i, i < contents.size() ? contents.get(i).copy() : ItemStack.EMPTY);
        }
    }

    public int size() {
        return this.getContainerSize();
    }

    /** Maximum stack size exposed to menu slots for a particular index. */
    public int getSlotLimit(int index, ItemStack stack) {
        return this.getMaxStackSize(stack);
    }

}
