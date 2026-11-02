package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import xyz.ssander.rings.CrashPilot;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * A crashed ring-bearer: sitting slumped back against their ship, legs out, in their colour's uniform (each colour's
 * pilot is a different people, see {@code textures/entity/crash_pilot}). Their ring is in their hand until they hand it
 * on.
 */
public class CrashPilotRenderer extends HumanoidMobRenderer<CrashPilot, CrashPilotRenderer.State, HumanoidModel<CrashPilotRenderer.State>> {
	/** How far back they lean against the hull. */
	private static final float SLUMP = 18.0F;

	public static class State extends HumanoidRenderState {
		public RingColor color = RingColor.GREEN;
		public boolean ringTaken;
	}

	public CrashPilotRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CrashPilot entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.color = entity.color();
		state.ringTaken = entity.ringTaken();
		// Sitting on the ground, as a rider sits
		state.isPassenger = true;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return Rings.id("textures/entity/crash_pilot/" + state.color.id() + ".png");
	}

	@Override
	protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		// Down onto the ground (a rider's legs hang below the seat), and leaning back
		poseStack.translate(0.0F, -0.55F, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -SLUMP);
	}
}
