package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingStyles;
import xyz.ssander.rings.Rings;

/**
 * The wearer's chosen emblem (see RingStyles), as a badge over the suit's own on the chest: a disc of the ring's
 * colour, darkened, with the emblem shining on it. Only on a suit, and only for an emblem other than the lantern every
 * suit already has.
 */
public class EmblemLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier BADGE = Rings.id("textures/entity/emblem/badge.png");
	private static final float PX = 1.0F / 16.0F;
	/** The badge on the chest, in the body's pixels (its front face is at z -2, y running down from the neck). */
	private static final float X0 = -3.0F, X1 = 3.0F, Y0 = 0.6F, Y1 = 6.6F;

	public EmblemLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		RingRenderState ring = (RingRenderState) state;
		RingStyles.Emblem emblem = ring.rings$emblem();
		RingColor color = ring.rings$wornRing();
		if (emblem == null || color == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		int disc = color == RingColor.BLACK ? 0x9C9CB4 : ARGB.scaleRGB(color.energy(), 0.45F);
		int mark = color == RingColor.WHITE || color == RingColor.YELLOW ? 0x202020 : 0xFFFFFF;
		Identifier symbol = Rings.id("textures/entity/emblem/" + emblem.getSerializedName() + ".png");
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(BADGE), (pose, consumer) ->
				quad(pose, consumer, -2.06F, disc, light));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(symbol), (pose, consumer) ->
				quad(pose, consumer, -2.1F, mark, ring.rings$ringActive() ? LightCoordsUtil.FULL_BRIGHT : light));
		poseStack.popPose();
	}

	/** A square facing out of the chest at depth z, tinted. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, float z, int rgb, int light) {
		float r = ARGB.red(rgb) / 255.0F, g = ARGB.green(rgb) / 255.0F, b = ARGB.blue(rgb) / 255.0F;
		float[][] corners = {{X1, Y0, 0, 0}, {X0, Y0, 1, 0}, {X0, Y1, 1, 1}, {X1, Y1, 0, 1}};
		for (float[] c : corners) {
			consumer.addVertex(pose, c[0] * PX, c[1] * PX, z * PX).setColor(r, g, b, 1.0F).setUv(c[2], c[3])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
		}
	}
}
