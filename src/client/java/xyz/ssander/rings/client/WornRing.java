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
import org.joml.Matrix4f;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * The ring on its wearer's right hand, on or off: a band round the fingers and the ring's face on the front of the
 * fist, in the ring's colour. Switched on, the face glows. Drawn on players by {@link Layer} and on your own hand in
 * first person (see AvatarRendererMixin).
 */
public final class WornRing {
	/** Plain white, tinted with the ring's colours. */
	private static final Identifier TEXTURE = Rings.id("textures/entity/ring_band.png");
	private static final float PX = 1.0F / 16.0F;

	private WornRing() {
	}

	/**
	 * Draws the ring. The pose is the right arm's (after {@code rightArm.translateAndRotate}), whose box runs from
	 * the shoulder down to the fist: 4 pixels wide, or 3 for slim arms.
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, RingColor color, boolean slim, boolean active) {
		float left = slim ? -2.0F : -3.0F;
		float middle = slim ? -0.5F : -1.0F;
		int band = metal(color);
		int face = color.energy();
		// The band sits just proud of the fingers all round; the face a little further out on the front
		float[] bandBox = {left - 0.15F, 8.7F, -2.15F, 1.15F, 9.5F, 2.15F};
		float[] faceBox = {middle - 0.8F, 8.3F, -2.65F, middle + 0.8F, 9.9F, -2.15F};
		collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, consumer) -> {
			box(pose, consumer, bandBox, 0.0F, band, light);
			box(pose, consumer, faceBox, 0.0F, face, active ? LightCoordsUtil.FULL_BRIGHT : light);
		});
		if (active && color != RingColor.BLACK) {
			collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
				Matrix4f matrix = pose.pose();
				glow(matrix, consumer, faceBox, 0.35F, face, 0.35F);
				glow(matrix, consumer, faceBox, 0.9F, face, 0.12F);
			});
		}
	}

	/** The band's colour: the ring's own, darkened to look like metal. Black and white rings keep theirs. */
	private static int metal(RingColor color) {
		return switch (color) {
			case WHITE -> 0xD8DCE4;
			case BLACK -> 0x26262E;
			default -> ARGB.scaleRGB(color.energy(), 0.6F);
		};
	}

	private static void box(PoseStack.Pose pose, VertexConsumer consumer, float[] b, float grow, int rgb, int light) {
		float x0 = (b[0] - grow) * PX, y0 = (b[1] - grow) * PX, z0 = (b[2] - grow) * PX;
		float x1 = (b[3] + grow) * PX, y1 = (b[4] + grow) * PX, z1 = (b[5] + grow) * PX;
		float r = ARGB.red(rgb) / 255.0F, g = ARGB.green(rgb) / 255.0F, bl = ARGB.blue(rgb) / 255.0F;
		// Each face: its outward normal, then its corners wound to face outward (as in LightBoxes)
		float[][] faces = {
				{0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
				{0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
				{1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
				{-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
				{0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
				{0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
		};
		float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
		for (float[] f : faces) {
			for (int corner = 0; corner < 4; corner++) {
				int i = 3 + corner * 3;
				consumer.addVertex(pose, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, 1.0F)
						.setUv(uvs[corner][0], uvs[corner][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, f[0], f[1], f[2]);
			}
		}
	}

	/** An additive glow round a box, grown by {@code grow} pixels. */
	private static void glow(Matrix4f m, VertexConsumer c, float[] b, float grow, int rgb, float alpha) {
		float x0 = (b[0] - grow) * PX, y0 = (b[1] - grow) * PX, z0 = (b[2] - grow) * PX;
		float x1 = (b[3] + grow) * PX, y1 = (b[4] + grow) * PX, z1 = (b[5] + grow) * PX;
		LightBoxes.box(m, c, new float[] {x0, y0, z0, x1, y1, z1}, 0.0F, rgb, alpha);
	}

	/** Draws the worn ring on players in the world. */
	public static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final boolean slim;

		public Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, boolean slim) {
			super(renderer);
			this.slim = slim;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			RingRenderState ring = (RingRenderState) state;
			if (ring.rings$wornRing() == null || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().rightArm.translateAndRotate(poseStack);
			// A costume swaps in a wide-armed suit whatever the player's own arms are
			WornRing.submit(poseStack, collector, light, ring.rings$wornRing(), this.slim && ring.rings$ownSkin() == null, ring.rings$ringActive());
			poseStack.popPose();
		}
	}
}
