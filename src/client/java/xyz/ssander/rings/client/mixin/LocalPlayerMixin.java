package xyz.ssander.rings.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.client.SuperFlight;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	// Sneak is super flight's boost key, and vanilla only stops sneak crouching you in hover flight, not while gliding
	@Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
	private void rings$noCrouchGliding(CallbackInfoReturnable<Boolean> cir) {
		if (SuperFlight.isGliding()) {
			cir.setReturnValue(false);
		}
	}
}
