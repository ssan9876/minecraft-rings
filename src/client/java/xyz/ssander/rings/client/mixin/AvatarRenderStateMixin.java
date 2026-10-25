package xyz.ssander.rings.client.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import xyz.ssander.rings.client.RingRenderState;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements RingRenderState {
	@Unique
	private float rings$dive;
	@Unique
	private float rings$hover;

	@Override
	public float rings$dive() {
		return this.rings$dive;
	}

	@Override
	public float rings$hover() {
		return this.rings$hover;
	}

	@Override
	public void rings$setFlight(float dive, float hover) {
		this.rings$dive = dive;
		this.rings$hover = hover;
	}
}
