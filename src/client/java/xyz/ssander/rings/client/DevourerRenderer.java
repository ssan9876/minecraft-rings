package xyz.ssander.rings.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import xyz.ssander.rings.Devourer;

/**
 * Draws the Devourer: a hunched shadow, its body cracked with violet light, long arms ending in pale claws, a head
 * that's nearly all maw (jaws working, full of stars) under two burning eyes, and in place of legs a mass of tendrils
 * trailing and swaying beneath it. Solid dark boxes, with violet light added over the cracks, eyes and maw.
 */
public class DevourerRenderer extends EntityRenderer<Devourer, DevourerRenderer.State> {
	private static final int SHADOW = 0x140C1E;
	private static final int SHADOW_LIGHT = 0x2A1A3C;
	private static final int CRACK = 0xB050FF;
	private static final int EYE = 0xFFB0FF;
	private static final int CLAW = 0xD8D0E8;
	private static final int TENDRILS = 7;
	private static final int SEGMENTS = 5;

	public static class State extends EntityRenderState {
		float yRot;
		float bodyPitch;
		boolean hurt;
	}

	public DevourerRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Devourer devourer, State state, float partialTick) {
		super.extractRenderState(devourer, state, partialTick);
		state.yRot = devourer.getYRot(partialTick);
		state.hurt = devourer.hurtTime > 0;
		// Leaning into its lunge
		double speed = devourer.getDeltaMovement().horizontalDistance();
		state.bodyPitch = (float) Math.min(35.0, speed * 60.0);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = state.ageInTicks;
		float jaw = 0.25F + 0.25F * (1.0F + Mth.sin(t * 0.18F));
		List<float[]> dark = new ArrayList<>();
		List<float[]> light = new ArrayList<>();
		List<float[]> claws = new ArrayList<>();

		// Body: a hunched chest over a narrower waist, shoulders up round the head
		dark.add(new float[] {-0.9F, 2.2F, -0.55F, 0.9F, 3.5F, 0.6F});
		dark.add(new float[] {-0.6F, 1.5F, -0.4F, 0.6F, 2.3F, 0.45F});
		dark.add(new float[] {-1.25F, 3.2F, -0.5F, -0.75F, 3.75F, 0.5F});
		dark.add(new float[] {0.75F, 3.2F, -0.5F, 1.25F, 3.75F, 0.5F});
		// Cracks of light down the chest
		light.add(new float[] {-0.08F, 2.3F, 0.58F, 0.08F, 3.4F, 0.64F});
		light.add(new float[] {-0.5F, 2.8F, 0.58F, -0.2F, 2.9F, 0.64F});
		light.add(new float[] {0.25F, 2.55F, 0.58F, 0.6F, 2.65F, 0.64F});

		// Head: a skull above a gaping maw whose lower jaw works
		dark.add(new float[] {-0.55F, 3.75F, -0.45F, 0.55F, 4.45F, 0.55F});
		dark.add(new float[] {-0.5F, 3.7F - jaw, -0.3F, 0.5F, 3.85F - jaw, 0.6F});
		light.add(new float[] {-0.42F, 3.82F - jaw, 0.1F, 0.42F, 3.78F, 0.52F});
		light.add(new float[] {-0.35F, 4.12F, 0.55F, -0.12F, 4.25F, 0.6F});
		light.add(new float[] {0.12F, 4.12F, 0.55F, 0.35F, 4.25F, 0.6F});

		// Arms hanging long and swinging, ending in claws
		for (int side = -1; side <= 1; side += 2) {
			float swing = 0.25F * Mth.sin(t * 0.12F + side);
			float x = side * 1.15F;
			dark.add(new float[] {x - 0.22F, 2.0F, -0.22F + swing, x + 0.22F, 3.4F, 0.22F + swing});
			dark.add(new float[] {x - 0.18F, 0.9F, -0.2F + swing * 2.0F, x + 0.18F, 2.05F, 0.2F + swing * 2.0F});
			for (int finger = -1; finger <= 1; finger++) {
				float fx = x + finger * 0.12F;
				claws.add(new float[] {fx - 0.04F, 0.45F, 0.05F + swing * 2.2F, fx + 0.04F, 0.92F, 0.14F + swing * 2.2F});
			}
		}

		// Tendrils, swaying beneath it in place of legs
		for (int i = 0; i < TENDRILS; i++) {
			float angle = Mth.TWO_PI * i / TENDRILS;
			float ox = Mth.cos(angle) * 0.35F;
			float oz = Mth.sin(angle) * 0.3F;
			float y = 1.55F;
			for (int s = 0; s < SEGMENTS; s++) {
				float sway = 0.12F * (s + 1) * Mth.sin(t * 0.1F + i * 1.3F + s * 0.7F);
				float size = 0.16F * (1.0F - s / (float) (SEGMENTS + 1));
				float x = ox * (1.0F + s * 0.35F) + sway;
				float z = oz * (1.0F + s * 0.35F) - 0.05F * s;
				dark.add(new float[] {x - size, y - 0.32F, z - size, x + size, y, z + size});
				y -= 0.3F;
			}
		}

		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		poseStack.rotateDegrees(Axis.XP, state.bodyPitch);
		int body = state.hurt ? 0x6A2A5A : SHADOW;
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (float[] box : dark) {
				LightBoxes.box(matrix, consumer, box, 0.0F, body, 1.0F);
			}
			for (float[] box : claws) {
				LightBoxes.box(matrix, consumer, box, 0.0F, CLAW, 1.0F);
			}
			// A paler fringe of shadow round the shoulders
			LightBoxes.box(matrix, consumer, new float[] {-1.3F, 3.62F, -0.55F, 1.3F, 3.7F, 0.55F}, 0.0F, SHADOW_LIGHT, 1.0F);
		});
		float pulse = 0.7F + 0.3F * Mth.sin(t * 0.3F);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (float[] box : light) {
				LightBoxes.box(matrix, consumer, box, 0.0F, CRACK, 0.85F * pulse);
				LightBoxes.box(matrix, consumer, box, 0.06F, CRACK, 0.3F);
			}
			// The eyes burn brightest
			LightBoxes.box(matrix, consumer, new float[] {-0.35F, 4.12F, 0.55F, -0.12F, 4.25F, 0.62F}, 0.0F, EYE, 1.0F);
			LightBoxes.box(matrix, consumer, new float[] {0.12F, 4.12F, 0.55F, 0.35F, 4.25F, 0.62F}, 0.0F, EYE, 1.0F);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
