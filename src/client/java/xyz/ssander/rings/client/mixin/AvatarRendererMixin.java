package xyz.ssander.rings.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.ForceBubble;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.client.Costumes;
import xyz.ssander.rings.client.RingFlight;
import xyz.ssander.rings.client.RingRenderState;
import xyz.ssander.rings.client.SlimArms;
import xyz.ssander.rings.client.WornRing;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin implements SlimArms {
	@Unique
	private boolean rings$slim;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void rings$rememberArms(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
		this.rings$slim = slimSteve;
	}

	@Override
	public boolean rings$slim() {
		return this.rings$slim;
	}

	// Your own ring on your hand in first person
	@Inject(method = "renderRightHand", at = @At("TAIL"))
	private void rings$ringInFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, boolean hasSleeve,
			CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		RingColor color = player == null ? null : player.getAttached(RingAttachments.WORN_RING);
		if (color == null) {
			return;
		}
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		// A suit has wide arms, whatever the player's own are
		boolean suit = costume != null && costume.skin() != null;
		poseStack.pushPose();
		((AvatarRenderer<?>) (Object) this).getModel().rightArm.translateAndRotate(poseStack);
		WornRing.submit(poseStack, collector, lightCoords, color, this.rings$slim && !suit, costume != null);
		poseStack.popPose();
	}
	// Swaps in the costume, outlines the wearer in the ring's colour, and sets up the flying pose.
	// First-person hands read this state too, so they show the suit's sleeves and gloves.
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void rings$applyRing(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		RingRenderState ring = (RingRenderState) state;
		ring.rings$setOwnSkin(null);
		if (!(entity instanceof Player player)) {
			ring.rings$setFlight(0.0F, 0.0F);
			ring.rings$setFastFlight(0.0F, 0.0F);
			ring.rings$setFiring(false);
			ring.rings$setWornRing(null, false);
			return;
		}
		ring.rings$setFiring(RingBeam.isFiring(player));

		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		if (costume != null && costume.skin() != null) {
			PlayerSkin.Patch patch = costume.skin();
			// The suit replaces everything below the neck. The head keeps the player's own skin (OwnHeadLayer).
			ring.rings$setOwnSkin(state.skin);
			state.skin = state.skin.with(patch);
			// Show the whole suit whatever the player's skin settings are
			state.showJacket = true;
			state.showLeftSleeve = true;
			state.showRightSleeve = true;
			state.showLeftPants = true;
			state.showRightPants = true;
			state.showCape |= patch.cape().isPresent();
		}
		if (costume != null) {
			state.outlineColor = ARGB.opaque(costume.color().energy());
		}

		// Cruising is real elytra flight, so vanilla tips the body along it; this adds the arms and legs
		ring.rings$setFlight(RingFlight.dive(player, partialTicks), RingFlight.hover(player, partialTicks));
		ring.rings$setFastFlight(RingFlight.sonic(player, partialTicks), RingFlight.rise(player, partialTicks));
		ring.rings$setWornRing(player.getAttached(RingAttachments.WORN_RING), costume != null);
		// Carried in a force bubble, they float upright rather than sitting
		if (player.getVehicle() instanceof ForceBubble) {
			state.isPassenger = false;
		}
	}

	// Hovering bobs gently up and down
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"))
	private void rings$hoverBob(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		float hover = ((RingRenderState) state).rings$hover();
		if (hover > 0.0F) {
			poseStack.translate(0.0F, hover * Mth.sin(state.ageInTicks * 0.1F) * 0.06F, 0.0F);
		}
	}
}
