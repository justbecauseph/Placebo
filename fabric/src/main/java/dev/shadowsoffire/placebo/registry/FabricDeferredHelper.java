package dev.shadowsoffire.placebo.registry;

/**
 * Fabric's {@link DeferredHelper}.
 * <p>
 * There is nothing to add: the base class registers through Architectury's {@code DeferredRegister} and is
 * already loader-neutral. This subclass exists only because the constructor is {@code protected}.
 * <p>
 * It used to say that 16 methods lived on {@code NeoForgeDeferredHelper} with no Fabric equivalent, and that
 * calling one here was a deliberate compile error. <b>None of them do any more.</b> Attachments, custom
 * ingredients, data maps, extended menus, block entities, custom registries and global loot modifiers all
 * reached {@code DeferredHelper}, one at a time, and each turned out to be either a type that was
 * platform-bound only by where it lived or a subsystem worth implementing outright. What remains on the
 * NeoForge subclass is bus plumbing that no mod calls.
 */
public class FabricDeferredHelper extends DeferredHelper {

    public FabricDeferredHelper(String modid) {
        super(modid);
    }

}
