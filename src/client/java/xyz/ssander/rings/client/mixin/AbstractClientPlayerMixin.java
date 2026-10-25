package xyz.ssander.rings.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.client.SuperFlight;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	// The view widens with ring-flying speed, scaled by the FOV effects setting like vanilla's
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void rings$speedFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if ((Object) this == minecraft.player && minecraft.player.getAttachedOrElse(RingAttachments.FLYING, false)) {
			float speed = SuperFlight.speedFactor(minecraft.player);
			cir.setReturnValue(cir.getReturnValue() * (1.0F + 0.25F * speed * effectScale));
		}
	}
}
