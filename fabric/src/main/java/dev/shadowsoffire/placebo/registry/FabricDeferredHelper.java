package dev.shadowsoffire.placebo.registry;

/**
 * Fabric's {@link DeferredHelper}.
 * <p>
 * There is nothing to add: the base class registers through Architectury's {@code DeferredRegister} and is
 * already loader-neutral. This subclass exists only because the constructor is {@code protected}, and it is
 * the counterpart to {@code NeoForgeDeferredHelper} -- which does have platform-only methods.
 * <p>
 * The 16 methods that live on {@code NeoForgeDeferredHelper} (attachments, custom ingredients, global loot
 * modifiers, data maps, {@code IContainerFactory} menus, {@code RegistryBuilder} registries) have no Fabric
 * equivalent yet. Calling one on Fabric is a compile error against this type, which is the intent -- each is
 * a Phase 2b design decision rather than a rename.
 */
public class FabricDeferredHelper extends DeferredHelper {

    public FabricDeferredHelper(String modid) {
        super(modid);
    }

}
