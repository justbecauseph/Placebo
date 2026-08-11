package dev.shadowsoffire.placebo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.client.FabricWingLayer;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/**
 * Fabric exposes arbitrary render-state data but not NeoForge's AvatarRenderStateModifier event. Capture the
 * Patreon wing data at the equivalent point, once AvatarRenderer has finished extracting its vanilla state.
 */
@Mixin(value = AvatarRenderer.class, remap = false)
public abstract class AvatarRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void placebo$extractWingState(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        FabricWingLayer.extractRenderState(entity, state);
    }
}
