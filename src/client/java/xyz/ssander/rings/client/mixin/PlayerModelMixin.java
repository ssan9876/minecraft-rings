package xyz.ssander.rings.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.client.HeadOnlyModel;
import xyz.ssander.rings.client.RingRenderState;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin implements HeadOnlyModel {
	@Unique
	private boolean rings$headOnly;

	@Override
	public void rings$setHeadOnly(boolean headOnly) {
		this.rings$headOnly = headOnly;
	}

	// While a costume is on, the costume model leaves out the head and OwnHeadLayer's head-only model draws it
	// with the player's own skin. The model is shared by every player, so visibility is set every time.
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void rings$costumeHead(AvatarRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		if (this.rings$headOnly) {
			model.head.visible = true;
			model.body.visible = false;
			model.rightArm.visible = false;
			model.leftArm.visible = false;
			model.rightLeg.visible = false;
			model.leftLeg.visible = false;
		} else {
			model.head.visible = ((RingRenderState) state).rings$ownSkin() == null;
		}
	}

	// Runs after vanilla has posed the limbs, and blends toward the ring's flying poses
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void rings$flightPose(AvatarRenderState state, CallbackInfo ci) {
		RingRenderState ring = (RingRenderState) state;
		float dive = ring.rings$dive();
		float hover = ring.rings$hover();
		float sonic = ring.rings$sonic();
		float rise = ring.rings$rise();
		if (dive <= 0.0F && hover <= 0.0F && rise <= 0.0F) {
			return;
		}

		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float sway = Mth.sin(state.ageInTicks * 0.08F);

		// Hovering: legs hang loose and drift, arms a little out from the sides
		if (hover > 0.0F) {
			rings$blend(model.rightLeg, hover, 0.12F + sway * 0.06F, 0.0F, 0.06F);
			rings$blend(model.leftLeg, hover, 0.02F - sway * 0.06F, 0.0F, -0.06F);
			rings$blend(model.rightArm, hover, -0.1F + sway * 0.03F, 0.0F, 0.25F);
			rings$blend(model.leftArm, hover, -0.1F - sway * 0.03F, 0.0F, -0.25F);
		}

		// Flying fast (the body is tipped forward by the renderer): right fist out ahead, left arm along the side
		if (dive > 0.0F) {
			rings$blend(model.rightArm, dive, (float) -Math.PI, 0.0F, 0.15F);
			rings$blend(model.leftArm, dive, 0.15F, 0.0F, -0.1F);
			rings$blend(model.rightLeg, dive, 0.08F + sway * 0.02F, 0.0F, 0.03F);
			rings$blend(model.leftLeg, dive, -0.02F - sway * 0.02F, 0.0F, -0.03F);
		}

		// Sonic: streamlined, the trailing arm swept back along the body and the legs together, shuddering in the rush
		if (sonic > 0.0F) {
			float shudder = Mth.sin(state.ageInTicks * 2.3F) * 0.025F;
			rings$blend(model.rightArm, sonic, (float) -Math.PI + shudder, 0.0F, 0.05F);
			rings$blend(model.leftArm, sonic, 0.45F - shudder, 0.0F, -0.04F);
			rings$blend(model.rightLeg, sonic, 0.02F + shudder, 0.0F, 0.01F);
			rings$blend(model.leftLeg, sonic, 0.02F - shudder, 0.0F, -0.01F);
		}

		// Rising fast: fist to the sky, the other arm at the side, legs together trailing below
		if (rise > 0.0F) {
			rings$blend(model.rightArm, rise, (float) -Math.PI, 0.0F, 0.08F);
			rings$blend(model.leftArm, rise, 0.08F, 0.0F, -0.1F);
			rings$blend(model.rightLeg, rise, 0.06F, 0.0F, 0.02F);
			rings$blend(model.leftLeg, rise, 0.0F, 0.0F, -0.02F);
		}
	}

	// Firing the beam: the ring arm points along the line of sight, like aiming a bow, unless the dive pose already
	// has the fist out ahead
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void rings$beamAim(AvatarRenderState state, CallbackInfo ci) {
		RingRenderState ring = (RingRenderState) state;
		if (!ring.rings$firing() || ring.rings$dive() > 0.5F) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		model.rightArm.xRot = model.head.xRot - Mth.HALF_PI;
		model.rightArm.yRot = model.head.yRot;
		model.rightArm.zRot = 0.0F;
	}

	@Unique
	private static void rings$blend(ModelPart part, float amount, float xRot, float yRot, float zRot) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
