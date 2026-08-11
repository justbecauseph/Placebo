package dev.shadowsoffire.placebo.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.patreon.PatreonUtils.WingType;
import dev.shadowsoffire.placebo.patreon.WingsManager;
import dev.shadowsoffire.placebo.patreon.wings.Wing;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/** Fabric counterpart to NeoForge's render-state modifier plus {@code WingLayer}. */
public class FabricWingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final RenderStateDataKey<WingRenderData> WING_DATA = RenderStateDataKey.create(() -> Placebo.MODID + ":wings/render_data");

    public FabricWingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> playerRenderer) {
        super(playerRenderer);
    }

    /** Called from the Fabric-only AvatarRenderer mixin while its source entity is still available. */
    public static <T extends Avatar & ClientAvatarEntity> void extractRenderState(T avatar, AvatarRenderState state) {
        if (!(avatar instanceof AbstractClientPlayer player) || WingsManager.DISABLED.contains(player.getUUID())) {
            return;
        }

        WingType type = WingsManager.getType(player.getUUID());
        if (type != null) {
            ((FabricRenderState) state).setData(WING_DATA, new WingRenderData(type, type.textureGetter.apply(player)));
        }
    }

    @Override
    public void submit(PoseStack stack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        WingRenderData data = ((FabricRenderState) state).getData(WING_DATA);
        if (data == null) {
            return;
        }

        stack.pushPose();
        stack.translate(0, data.type().yOffset, 0);
        data.type().model.get().submit(stack, collector, lightCoords, state, data.texture());
        stack.popPose();
    }

    public record WingRenderData(WingType type, Identifier texture) {}
}
