package dev.shadowsoffire.placebo.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.dynreg.FabricDynReg;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;

/** Supplies Fabric dynamic registries with the authoritative lookup used by the current data reload. */
@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void placebo$captureReloadLookup(LayeredRegistryAccess<RegistryLayer> layers,
        HolderLookup.Provider lookup, FeatureFlagSet flags, Commands.CommandSelection selection,
        List<Registry.PendingTags<?>> postponedTags, PermissionSet permissions,
        List<DataComponentInitializers.PendingComponents<?>> newComponents, CallbackInfo ci) {
        FabricDynReg.setReloadLookup(lookup);
    }
}
