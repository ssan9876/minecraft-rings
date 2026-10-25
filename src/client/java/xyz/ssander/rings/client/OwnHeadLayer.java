package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws a costumed player's head with their own skin. The costume model hides its head, and this layer submits a
 * second player model that shows only the head and hat, posed from the same render state.
 */
public class OwnHeadLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final PlayerModel headModel;

	public OwnHeadLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, PlayerModel headModel) {
		super(renderer);
		this.headModel = headModel;
		((HeadOnlyModel) headModel).rings$setHeadOnly(true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		PlayerSkin ownSkin = ((RingRenderState) state).rings$ownSkin();
		if (ownSkin == null || state.isInvisible) {
			return;
		}

		submitNodeCollector.submitModel(this.headModel, state, poseStack, RenderTypes.entityTranslucent(ownSkin.body().texturePath()),
				lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null, state.outlineColor);
	}
}
