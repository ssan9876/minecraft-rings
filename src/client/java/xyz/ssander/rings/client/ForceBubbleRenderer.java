package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.ForceBubble;
import xyz.ssander.rings.RingColor;

/** Draws a force bubble: a sphere of the ring's force field, sized to whatever it carries. */
public class ForceBubbleRenderer extends EntityRenderer<ForceBubble, ForceBubbleRenderer.State> {
	public static class State extends EntityRenderState {
		RingColor color = RingColor.GREEN;
		float height = 1.0F;
		float radius = 1.0F;
	}

	public ForceBubbleRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected boolean affectedByCulling(ForceBubble bubble) {
		// It's drawn round whatever it carries, well outside its own small box
		return false;
	}

	@Override
	public void extractRenderState(ForceBubble bubble, State state, float partialTick) {
		super.extractRenderState(bubble, state, partialTick);
		state.color = bubble.color();
		Entity passenger = bubble.getFirstPassenger();
		state.height = passenger != null ? passenger.getBbHeight() : 1.0F;
		state.radius = passenger != null ? Math.max(passenger.getBbHeight(), passenger.getBbWidth()) / 2.0F + 0.45F : 0.8F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 centre = new Vec3(0.0, state.height / 2.0, 0.0);
		Vec3 cameraFromHere = camera.pos.subtract(state.x, state.y, state.z);
		EnergySphere.submit(collector, poseStack, centre, state.radius, cameraFromHere, state.color, state.ageInTicks, 1.0F);
		super.submit(state, poseStack, collector, camera);
	}
}
