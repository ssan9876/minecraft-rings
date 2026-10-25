package xyz.ssander.rings.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.client.RingFlight;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float yRot;

	@Shadow
	private float xRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	// Flying at sonic speed shakes the view
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void rings$sonicShake(float partialTicks, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) {
			return;
		}
		float shake = RingFlight.sonic(minecraft.player, partialTicks);
		if (shake > 0.0F) {
			float time = minecraft.player.tickCount + partialTicks;
			float yaw = (Mth.sin(time * 2.9F) + Mth.sin(time * 4.7F) * 0.5F) * 0.35F * shake;
			float pitch = (Mth.cos(time * 3.3F) + Mth.sin(time * 5.9F) * 0.5F) * 0.35F * shake;
			this.setRotation(this.yRot + yaw, this.xRot + pitch);
		}
	}
}
