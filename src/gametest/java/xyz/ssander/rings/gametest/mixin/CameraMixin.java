package xyz.ssander.rings.gametest.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.gametest.TestCamera;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float yRot;

	@Shadow
	private float xRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	// Turns the detached camera just before it's pulled back from the player, so it ends up off to the side
	@Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0))
	private void rings$swingCamera(float partialTicks, CallbackInfo ci) {
		if (TestCamera.yawOffset != 0.0F) {
			this.setRotation(this.yRot + TestCamera.yawOffset, this.xRot);
		}
	}
}
