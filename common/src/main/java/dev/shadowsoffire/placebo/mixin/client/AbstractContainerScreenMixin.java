package dev.shadowsoffire.placebo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import dev.shadowsoffire.placebo.util.DrawsOnLeft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Makes every container screen a {@link DrawsOnLeft}, so screens can draw text down the left margin without
 * each of them implementing it.
 * <p>
 * In {@code common} because both loaders need it: {@code DrawsOnLeft.draw} casts an arbitrary screen to the
 * interface, and without the mixin applied that is a {@code ClassCastException}. It was NeoForge-only until
 * the interface itself moved, which meant Apotheosis's Fabric jar shipped a screen implementing a class the
 * jar did not contain -- see {@code tools/fabric_linkage.py}, which is the check that found it.
 */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public class AbstractContainerScreenMixin implements DrawsOnLeft {

}
