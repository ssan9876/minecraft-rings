package xyz.ssander.rings.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.client.SpaceSky;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	// Space and the planets have their own sun (SpaceSky) and no Moon hanging in every sky; the stars stay
	@Inject(method = {"renderSun", "renderMoon"}, at = @At("HEAD"), cancellable = true)
	private void rings$ownSky(CallbackInfo ci) {
		if (SpaceSky.ownsSky(Minecraft.getInstance().level)) {
			ci.cancel();
		}
	}
}
