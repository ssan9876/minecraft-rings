package xyz.ssander.rings.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import xyz.ssander.rings.Parallax;
import xyz.ssander.rings.RingColor;

/**
 * Draws Parallax: a great horned head of yellow light with burning eyes and jaws, and a long serpent body trailing
 * behind along the way it came, each coil smaller than the last.
 */
public class ParallaxRenderer extends EntityRenderer<Parallax, ParallaxRenderer.State> {
	/** Body coils, how many ticks of the path apart, and the biggest (nearest the head) coil's half-size. */
	private static final int COILS = 12;
	private static final int TICKS_APART = 2;
	private static final float BIGGEST_COIL = 1.5F;

	public static class State extends EntityRenderState {
		float yRot;
		float xRot;
		Vec3[] coils = new Vec3[0];
		boolean hurt;
	}

	/** The head's boxes, facing +Z, in blocks, centred on the body's middle. */
	private static final float[][] HEAD = {
			{-1.0F, -0.8F, -0.6F, 1.0F, 0.9F, 1.2F},
			// Horns
			{-1.3F, 0.6F, -0.6F, -0.9F, 2.0F, -0.2F},
			{0.9F, 0.6F, -0.6F, 1.3F, 2.0F, -0.2F},
			// Jaws
			{-0.8F, -1.3F, 0.6F, -0.2F, -0.6F, 2.0F},
			{0.2F, -1.3F, 0.6F, 0.8F, -0.6F, 2.0F},
	};
	private static final float[][] EYES = {
			{-0.75F, 0.15F, 1.15F, -0.3F, 0.5F, 1.3F},
			{0.3F, 0.15F, 1.15F, 0.75F, 0.5F, 1.3F},
	};

	/** The path each Parallax has flown, newest first, a position per tick. */
	private static final Map<Parallax, Deque<Vec3>> PATHS = new WeakHashMap<>();
	private static final Map<Parallax, Integer> LAST_TICK = new WeakHashMap<>();

	public ParallaxRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected boolean affectedByCulling(Parallax parallax) {
		return false;
	}

	@Override
	public void extractRenderState(Parallax parallax, State state, float partialTick) {
		super.extractRenderState(parallax, state, partialTick);
		state.yRot = parallax.getYRot(partialTick);
		state.xRot = parallax.getXRot(partialTick);
		state.hurt = parallax.hurtTime > 0;

		Deque<Vec3> path = PATHS.computeIfAbsent(parallax, p -> new ArrayDeque<>());
		if (LAST_TICK.getOrDefault(parallax, -1) != parallax.tickCount) {
			LAST_TICK.put(parallax, parallax.tickCount);
			path.addFirst(parallax.position());
			while (path.size() > COILS * TICKS_APART + 2) {
				path.removeLast();
			}
		}
		Vec3 here = parallax.getPosition(partialTick);
		state.coils = new Vec3[COILS];
		Iterator<Vec3> it = path.iterator();
		Vec3 last = here;
		for (int i = 0; i < COILS; i++) {
			for (int step = 0; step < TICKS_APART && it.hasNext(); step++) {
				last = it.next();
			}
			// Coils bunch up behind the head when it hangs still, rather than all sitting in one place
			Vec3 trailing = last.subtract(here);
			Vec3 back = new Vec3(Mth.sin(state.yRot * Mth.DEG_TO_RAD), 0.0, -Mth.cos(state.yRot * Mth.DEG_TO_RAD)).scale(1.8 * (i + 1));
			state.coils[i] = trailing.lengthSqr() < back.lengthSqr() ? back : trailing;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int energy = RingColor.YELLOW.energy();
		int core = ARGB.srgbLerp(state.hurt ? 0.8F : 0.3F, energy, 0xFFFFFF);
		int dark = 0xB88A08;
		float pulse = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.4F);
		poseStack.pushPose();
		poseStack.translate(0.0F, 1.5F, 0.0F);
		// The body, coil by coil from the tail up to the head: a solid golden core (so it shows against a bright
		// sky, where added light wouldn't) inside a glow of yellow light
		float[][] coils = new float[COILS][];
		for (int i = 0; i < COILS; i++) {
			Vec3 at = state.coils[i];
			float size = BIGGEST_COIL * (1.0F - 0.06F * i) * (0.92F + 0.08F * Mth.sin(state.ageInTicks * 0.3F + i));
			coils[i] = new float[] {(float) at.x - size, (float) at.y - size, (float) at.z - size, (float) at.x + size, (float) at.y + size, (float) at.z + size};
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
			for (float[] box : coils) {
				LightBoxes.box(pose.pose(), consumer, box, -(box[3] - box[0]) * 0.15F, state.hurt ? 0xFFF0B0 : dark, 0.9F);
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (float[] box : coils) {
				LightBoxes.box(matrix, consumer, box, 0.0F, core, 0.3F * pulse);
				LightBoxes.box(matrix, consumer, box, 0.3F, energy, 0.14F);
			}
		});
		// The head, turned the way it faces
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		poseStack.rotateDegrees(Axis.XP, state.xRot);
		poseStack.scale(1.6F, 1.6F, 1.6F);
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
			for (float[] box : HEAD) {
				LightBoxes.box(pose.pose(), consumer, box, -0.1F, state.hurt ? 0xFFF0B0 : dark, 0.9F);
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (float[] box : HEAD) {
				LightBoxes.box(matrix, consumer, box, 0.0F, core, 0.3F * pulse);
				LightBoxes.box(matrix, consumer, box, 0.12F, energy, 0.16F);
			}
			for (float[] eye : EYES) {
				LightBoxes.box(matrix, consumer, eye, 0.0F, 0xFFFFFF, 0.9F);
				LightBoxes.box(matrix, consumer, eye, 0.1F, 0xFF8020, 0.5F);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
