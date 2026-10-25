package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import xyz.ssander.rings.ConstructJet;
import xyz.ssander.rings.RingColor;

/** Draws the construct jet: a fuselage, swept wings, a tail and a bright canopy, of glowing boxes of the ring's light. */
public class ConstructJetRenderer extends EntityRenderer<ConstructJet, ConstructJetRenderer.State> {
	public static class State extends EntityRenderState {
		RingColor color = RingColor.GREEN;
		float yRot;
		float xRot;
	}

	/** The jet's boxes, nose toward +Z, in blocks. */
	private static final float[][] BODY = {
			// Fuselage and nose
			{-0.45F, 0.0F, -3.0F, 0.45F, 0.7F, 2.2F},
			{-0.3F, 0.1F, 2.2F, 0.3F, 0.55F, 3.0F},
			// Wings, and the tail's wings and fin
			{-2.6F, 0.22F, -1.4F, 2.6F, 0.36F, 0.3F},
			{-1.2F, 0.3F, -3.0F, 1.2F, 0.4F, -2.4F},
			{-0.06F, 0.7F, -3.0F, 0.06F, 1.6F, -2.3F},
	};
	private static final float[][] BRIGHT = {
			// Canopy over the seats
			{-0.32F, 0.7F, -2.4F, 0.32F, 1.0F, 1.6F},
			// Engines' glow
			{-0.35F, 0.12F, -3.25F, 0.35F, 0.58F, -3.0F},
	};

	public ConstructJetRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ConstructJet jet, State state, float partialTick) {
		super.extractRenderState(jet, state, partialTick);
		state.color = jet.color();
		state.yRot = jet.getYRot(partialTick);
		state.xRot = jet.getXRot(partialTick);
	}

	@Override
	protected boolean affectedByCulling(ConstructJet jet) {
		// Much bigger than its box
		return false;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		// Pitched along the flight, about its middle
		poseStack.translate(0.0F, 0.35F, 0.0F);
		poseStack.rotateDegrees(Axis.XP, state.xRot);
		poseStack.translate(0.0F, -0.35F, 0.0F);
		boolean dark = state.color == RingColor.BLACK;
		int energy = state.color.energy();
		int core = dark ? 0x3A3A48 : ARGB.srgbLerp(0.2F, energy, 0xFFFFFF);
		int bright = dark ? 0x6A6A80 : ARGB.srgbLerp(0.7F, energy, 0xFFFFFF);
		float pulse = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.5F);
		collector.submitCustomGeometry(poseStack, dark ? RenderTypes.debugQuads() : RenderTypes.lightning(), (pose, consumer) -> {
			for (float[] box : BODY) {
				LightBoxes.box(pose.pose(), consumer, box, 0.0F, core, dark ? 0.85F : 0.28F);
				LightBoxes.box(pose.pose(), consumer, box, 0.06F, energy, dark ? 0.4F : 0.12F);
			}
			for (float[] box : BRIGHT) {
				LightBoxes.box(pose.pose(), consumer, box, 0.0F, bright, dark ? 0.6F : 0.35F * pulse);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
