package dev.shadowsoffire.placebo;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.placebo.util.SpecialTooltipItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Client-side state that both loaders keep, and the small amount of logic that reads it.
 * <p>
 * <b>Everything here touches {@code net.minecraft.client} and still lives in {@code :common}.</b> That is the
 * pattern Gateways established and Patchouli proved at scale -- {@code :common} compiles against the merged
 * jar, so the question is never packaging but whether every path in is side-guarded. The two callers outside
 * client code, {@code GradientColor} and {@code MixRegistry}, both check the environment first.
 * <p>
 * The <i>wiring</i> is not here: NeoForge subscribes its bus from {@code NeoForgeClientEvents}, Fabric
 * registers from {@code PlaceboFabricClient}, and both do nothing but feed the fields below. Splitting it this
 * way is what let {@code GradientColor} stop being platform-side, since all it ever wanted was a tick count.
 */
public class PlaceboClient {

    public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(Placebo.loc("keys"));

    /** Client ticks since load. Advanced by the platform's tick hook. */
    public static long ticks = 0;

    private static int scrollIdx = 0;
    private static ItemStack currentTooltipItem = ItemStack.EMPTY;
    private static long tooltipTick = 0;

    public static float getColorTicks() {
        return (ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 0.5F;
    }

    /**
     * The client's brewing registry, or null before one exists -- which is the case during world creation in
     * single-player, and on a client that has not joined a level.
     */
    @Nullable
    public static PotionBrewing getBrewingRegistry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        ClientLevel level = mc.level;
        return level == null ? null : level.potionBrewing();
    }

    public static int getTooltipScrollIndex() {
        return scrollIdx;
    }

    public static int getTooltipScrollIndex(int size) {
        return Math.floorMod(scrollIdx, size);
    }

    /** Called by the platform's tick hook. */
    public static void tick() {
        ticks++;
    }

    /** Called by the platform's tooltip hook, so the scroll handler knows what is under the cursor. */
    public static void setTooltipItem(ItemStack stack) {
        currentTooltipItem = stack;
        tooltipTick = ticks;
    }

    /**
     * Scrolls the tooltip if one that supports it is showing and shift is held.
     * <p>
     * NeoForge feeds this from two separate events -- a screen one and a raw input one -- and Fabric from its
     * own pair, which is why the decision lives here and the events do not.
     *
     * @return true if the scroll was consumed and the platform should cancel it.
     */
    public static boolean scroll(double deltaY) {
        if (currentTooltipItem.getItem() instanceof SpecialTooltipItem && tooltipTick == ticks && Minecraft.getInstance().hasShiftDown()) {
            scrollIdx += deltaY < 0 ? 1 : -1;
            return true;
        }
        return false;
    }

}
