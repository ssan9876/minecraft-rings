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

import xyz.ssander.rings.ConstructBeast;
import xyz.ssander.rings.RingColor;

/**
 * Draws a construct creature: a wolf or a golem built of glowing boxes of the ring's light, legs striding as it
 * walks and (the golem's) arms swinging as it strikes.
 */
public class ConstructBeastRenderer extends EntityRenderer<ConstructBeast, ConstructBeastRenderer.State> {
	public static class State extends EntityRenderState {
		RingColor color = RingColor.GREEN;
		boolean golem;
		float bodyYaw;
		float headYaw;
		float headPitch;
		float walkPosition;
		float walkSpeed;
		float attack;
		boolean hurt;
	}

	/** A box, and the point it swings about, all in blocks with the creature facing +Z. */
	private record Part(float[] box, float pivotY, float pivotZ, Swing swing) {
	}

	private enum Swing {
		NONE, LEG_A, LEG_B, ARM_A, ARM_B, HEAD, TAIL
	}

	private static final Part[] WOLF = {
			new Part(new float[] {-0.22F, 0.42F, -0.45F, 0.22F, 0.8F, 0.25F}, 0.0F, 0.0F, Swing.NONE),
			new Part(new float[] {-0.28F, 0.4F, 0.1F, 0.28F, 0.9F, 0.42F}, 0.0F, 0.0F, Swing.NONE),
			new Part(new float[] {-0.19F, 0.6F, 0.42F, 0.19F, 0.94F, 0.74F}, 0.75F, 0.45F, Swing.HEAD),
			new Part(new float[] {-0.09F, 0.6F, 0.74F, 0.09F, 0.74F, 0.92F}, 0.75F, 0.45F, Swing.HEAD),
			new Part(new float[] {-0.17F, 0.94F, 0.52F, -0.07F, 1.06F, 0.6F}, 0.75F, 0.45F, Swing.HEAD),
			new Part(new float[] {0.07F, 0.94F, 0.52F, 0.17F, 1.06F, 0.6F}, 0.75F, 0.45F, Swing.HEAD),
			new Part(new float[] {-0.2F, 0.0F, 0.14F, -0.08F, 0.45F, 0.26F}, 0.45F, 0.2F, Swing.LEG_A),
			new Part(new float[] {0.08F, 0.0F, 0.14F, 0.2F, 0.45F, 0.26F}, 0.45F, 0.2F, Swing.LEG_B),
			new Part(new float[] {-0.2F, 0.0F, -0.4F, -0.08F, 0.45F, -0.28F}, 0.45F, -0.34F, Swing.LEG_B),
			new Part(new float[] {0.08F, 0.0F, -0.4F, 0.2F, 0.45F, -0.28F}, 0.45F, -0.34F, Swing.LEG_A),
			new Part(new float[] {-0.05F, 0.66F, -0.85F, 0.05F, 0.76F, -0.45F}, 0.72F, -0.45F, Swing.TAIL),
	};

	private static final Part[] GOLEM = {
			new Part(new float[] {-0.45F, 0.0F, -0.2F, -0.07F, 0.95F, 0.2F}, 0.95F, 0.0F, Swing.LEG_A),
			new Part(new float[] {0.07F, 0.0F, -0.2F, 0.45F, 0.95F, 0.2F}, 0.95F, 0.0F, Swing.LEG_B),
			new Part(new float[] {-0.62F, 0.95F, -0.34F, 0.62F, 1.95F, 0.34F}, 0.0F, 0.0F, Swing.NONE),
			new Part(new float[] {-0.25F, 1.95F, -0.22F, 0.25F, 2.4F, 0.3F}, 1.95F, 0.0F, Swing.HEAD),
			new Part(new float[] {-0.95F, 0.35F, -0.2F, -0.62F, 1.9F, 0.2F}, 1.8F, 0.0F, Swing.ARM_A),
			new Part(new float[] {0.62F, 0.35F, -0.2F, 0.95F, 1.9F, 0.2F}, 1.8F, 0.0F, Swing.ARM_B),
	};

	public ConstructBeastRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ConstructBeast beast, State state, float partialTick) {
		super.extractRenderState(beast, state, partialTick);
		state.color = beast.color();
		state.golem = beast.isGolem();
		state.bodyYaw = Mth.rotLerp(partialTick, beast.yBodyRotO, beast.yBodyRot);
		state.headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, beast.yHeadRotO, beast.yHeadRot) - state.bodyYaw);
		state.headPitch = beast.getXRot(partialTick);
		state.walkPosition = beast.walkAnimation.position(partialTick);
		state.walkSpeed = Math.min(1.0F, beast.walkAnimation.speed(partialTick));
		state.attack = beast.getSwingAnimation(partialTick);
		state.hurt = beast.hurtTime > 0;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.bodyYaw);
		boolean dark = state.color == RingColor.BLACK;
		int energy = state.color.energy();
		int core = dark ? 0x3A3A48 : ARGB.srgbLerp(state.hurt ? 0.7F : 0.2F, energy, 0xFFFFFF);
		float pulse = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.3F);
		Part[] parts = state.golem ? GOLEM : WOLF;
		collector.submitCustomGeometry(poseStack, dark ? RenderTypes.debugQuads() : RenderTypes.lightning(), (pose, consumer) -> {
			for (Part part : parts) {
				Matrix4f matrix = new Matrix4f(pose.pose()).translate(0.0F, part.pivotY(), part.pivotZ());
				switch (part.swing()) {
					case HEAD -> matrix.rotateY(state.headYaw * Mth.DEG_TO_RAD).rotateX(-state.headPitch * Mth.DEG_TO_RAD);
					case TAIL -> matrix.rotateX(-0.6F + Mth.sin(state.ageInTicks * 0.4F) * 0.15F);
					default -> matrix.rotateX(swing(part.swing(), state));
				}
				matrix.translate(0.0F, -part.pivotY(), -part.pivotZ());
				part(matrix, consumer, part.box(), core, energy, dark, pulse);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** How far a leg or arm swings, in radians: striding opposite pairs, and the golem's arms raised to strike. */
	private static float swing(Swing swing, State state) {
		float stride = Mth.cos(state.walkPosition * 0.6662F) * 1.2F * state.walkSpeed;
		return switch (swing) {
			case LEG_A -> stride;
			case LEG_B -> -stride;
			case ARM_A -> -stride * 0.6F - Mth.sin(state.attack * Mth.PI) * 1.8F;
			case ARM_B -> stride * 0.6F - Mth.sin(state.attack * Mth.PI) * 1.8F;
			default -> 0.0F;
		};
	}

	private static void part(Matrix4f matrix, VertexConsumer consumer, float[] box, int core, int energy, boolean dark, float pulse) {
		LightBoxes.box(matrix, consumer, box, 0.0F, core, dark ? 0.85F : 0.3F * pulse);
		LightBoxes.box(matrix, consumer, box, 0.05F, energy, dark ? 0.4F : 0.14F * pulse);
	}
}
