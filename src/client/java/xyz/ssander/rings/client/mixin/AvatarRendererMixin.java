package xyz.ssander.rings.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.client.Costumes;
import xyz.ssander.rings.client.RingFlight;
import xyz.ssander.rings.client.RingRenderState;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	// Swaps in the costume, outlines the wearer in the ring's colour, and sets up the flying pose.
	// First-person hands read this state too, so they show the suit's sleeves and gloves.
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void rings$applyRing(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		RingRenderState ring = (RingRenderState) state;
		ring.rings$setOwnSkin(null);
		if (!(entity instanceof Player player)) {
			ring.rings$setFlight(0.0F, 0.0F);
			ring.rings$setFiring(false);
			return;
		}
		ring.rings$setFiring(RingBeam.isFiring(player));

		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		if (costume != null) {
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
			state.outlineColor = ARGB.opaque(costume.color().energy());
		}

		float dive = RingFlight.dive(player, partialTicks);
		ring.rings$setFlight(dive, RingFlight.hover(player, partialTicks));
		if (dive > 0.05F && !state.isFallFlying) {
			// Reuse the elytra pose: tips the body along the look direction and banks into turns.
			// fallFlyingScale() is (t / 10)^2, so this eases it in with the dive.
			state.isFallFlying = true;
			state.fallFlyingTimeInTicks = 10.0F * Mth.sqrt(dive);
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
