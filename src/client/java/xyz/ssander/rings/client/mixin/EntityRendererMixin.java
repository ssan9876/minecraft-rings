package xyz.ssander.rings.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Thralls;
import xyz.ssander.rings.client.RingScan;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	// Creatures raised by a ring are outlined in its colour, and the ring's scan outlines creatures round about,
	// through walls
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void rings$outlines(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		RingColor thrall = entity.getAttached(Thralls.THRALL_COLOR);
		if (thrall != null) {
			// Black would vanish against the dark, so Black Lanterns glow a deathly violet
			state.outlineColor = ARGB.opaque(thrall == RingColor.BLACK ? 0x6A3C8C : thrall.energy());
		}
		int scan = RingScan.outline(entity);
		if (scan != 0) {
			state.outlineColor = ARGB.opaque(scan);
		}
	}
}
