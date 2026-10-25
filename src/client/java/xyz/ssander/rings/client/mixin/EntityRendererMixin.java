package xyz.ssander.rings.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.client.RingScan;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	// The ring's scan outlines creatures round about, through walls
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void rings$scanOutline(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int scan = RingScan.outline(entity);
		if (scan != 0) {
			state.outlineColor = ARGB.opaque(scan);
		}
	}
}
