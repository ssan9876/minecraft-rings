package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import xyz.ssander.rings.ConstructFist;
import xyz.ssander.rings.RingColor;

/**
 * Draws the giant fist (knuckles, back of the hand, thumb and wrist) or the giant hammer (head and haft): glowing
 * boxes of the ring's light in its colour.
 */
public class ConstructFistRenderer extends EntityRenderer<ConstructFist, ConstructFistRenderer.State> {
	public static class State extends EntityRenderState {
		RingColor color = RingColor.GREEN;
		boolean hammer;
		float yRot;
		float xRot;
	}

	/** The fist's boxes as {x0, y0, z0, x1, y1, z1} in blocks, pointing along +Z. */
	private static final float[][] BOXES = {
			// Four knuckles
			{-0.52F, -0.30F, 0.15F, -0.28F, 0.32F, 0.62F},
			{-0.25F, -0.30F, 0.15F, -0.01F, 0.36F, 0.66F},
			{0.02F, -0.30F, 0.15F, 0.26F, 0.36F, 0.66F},
			{0.29F, -0.30F, 0.15F, 0.53F, 0.32F, 0.60F},
			// Back of the hand
			{-0.52F, -0.38F, -0.42F, 0.53F, 0.40F, 0.15F},
			// Thumb, folded across
			{-0.40F, -0.48F, 0.05F, 0.30F, -0.28F, 0.42F},
			// Wrist
			{-0.32F, -0.30F, -1.15F, 0.32F, 0.30F, -0.42F},
	};

	/** The hammer's boxes, head down, as it comes down onto its target. */
	private static final float[][] HAMMER_BOXES = {
			// Head
			{-1.0F, 0.0F, -0.6F, 1.0F, 1.1F, 0.6F},
			// Bands round the head
			{-1.06F, 0.12F, -0.66F, -0.8F, 0.98F, 0.66F},
			{0.8F, 0.12F, -0.66F, 1.06F, 0.98F, 0.66F},
			// Haft
			{-0.14F, 1.1F, -0.14F, 0.14F, 3.0F, 0.14F},
	};

	public ConstructFistRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ConstructFist fist, State state, float partialTick) {
		super.extractRenderState(fist, state, partialTick);
		state.color = fist.color();
		state.hammer = fist.isHammer();
		state.yRot = fist.getYRot(partialTick);
		state.xRot = fist.getXRot(partialTick);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		if (state.hammer) {
			// Upright, turned to face the way its maker looked
			poseStack.rotateDegrees(Axis.YP, -state.yRot);
		} else {
			poseStack.translate(0.0F, 0.5F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, -state.yRot);
			poseStack.rotateDegrees(Axis.XP, state.xRot);
			poseStack.scale(1.3F, 1.3F, 1.3F);
		}
		float[][] boxes = state.hammer ? HAMMER_BOXES : BOXES;

		boolean dark = state.color == RingColor.BLACK;
		int energy = state.color.energy();
		int core = dark ? 0x3A3A48 : ARGB.srgbLerp(0.2F, energy, 0xFFFFFF);
		float pulse = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.8F);
		collector.submitCustomGeometry(poseStack, dark ? RenderTypes.debugQuads() : RenderTypes.lightning(), (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (float[] box : boxes) {
				box(matrix, consumer, box, 0.0F, core, dark ? 0.85F : 0.32F * pulse);
				box(matrix, consumer, box, 0.07F, energy, dark ? 0.4F : 0.14F * pulse);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** A box grown by {@code grow} on every side, each face wound to face outward. */
	private static void box(Matrix4f m, VertexConsumer c, float[] b, float grow, int rgb, float alpha) {
		float x0 = b[0] - grow, y0 = b[1] - grow, z0 = b[2] - grow;
		float x1 = b[3] + grow, y1 = b[4] + grow, z1 = b[5] + grow;
		float r = ARGB.red(rgb) / 255.0F, g = ARGB.green(rgb) / 255.0F, bl = ARGB.blue(rgb) / 255.0F;
		float[][] faces = {
				{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, // +Z
				{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, // -Z
				{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, // +X
				{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, // -X
				{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, // +Y
				{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, // -Y
		};
		for (float[] f : faces) {
			for (int i = 0; i < 12; i += 3) {
				c.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, alpha);
			}
		}
	}
}
