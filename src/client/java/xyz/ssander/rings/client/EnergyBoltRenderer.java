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

import xyz.ssander.rings.EnergyBolt;
import xyz.ssander.rings.RingColor;

/** Draws an energy bolt: a glowing streak of its colour, white-hot in the middle, pointing the way it flies. */
public class EnergyBoltRenderer extends EntityRenderer<EnergyBolt, EnergyBoltRenderer.State> {
	public static class State extends EntityRenderState {
		RingColor color = RingColor.RED;
		float yRot;
		float xRot;
	}

	private static final float[] CORE = {-0.06F, -0.06F, -0.45F, 0.06F, 0.06F, 0.45F};

	public EnergyBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EnergyBolt bolt, State state, float partialTick) {
		super.extractRenderState(bolt, state, partialTick);
		state.color = bolt.kind().color();
		state.yRot = bolt.getYRot(partialTick);
		state.xRot = bolt.getXRot(partialTick);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.2F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, state.yRot);
		poseStack.rotateDegrees(Axis.XP, -state.xRot);
		boolean dark = state.color == RingColor.BLACK;
		int energy = state.color.energy();
		int core = dark ? 0x3A2A48 : ARGB.srgbLerp(0.7F, energy, 0xFFFFFF);
		float pulse = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 1.3F);
		collector.submitCustomGeometry(poseStack, dark ? RenderTypes.debugQuads() : RenderTypes.lightning(), (pose, consumer) -> {
			LightBoxes.box(pose.pose(), consumer, CORE, 0.0F, core, dark ? 0.9F : 0.9F);
			LightBoxes.box(pose.pose(), consumer, CORE, 0.08F * pulse, energy, dark ? 0.5F : 0.45F);
			LightBoxes.box(pose.pose(), consumer, CORE, 0.2F * pulse, energy, dark ? 0.25F : 0.15F);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
