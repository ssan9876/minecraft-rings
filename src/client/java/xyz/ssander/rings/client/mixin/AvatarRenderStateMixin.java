package xyz.ssander.rings.client.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import xyz.ssander.rings.client.RingRenderState;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements RingRenderState {
	@Unique
	private float rings$dive;
	@Unique
	private float rings$hover;
	@Unique
	private float rings$sonic;
	@Unique
	private float rings$rise;
	@Unique
	private boolean rings$firing;
	@Unique
	private @Nullable PlayerSkin rings$ownSkin;

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

	@Override
	public float rings$sonic() {
		return this.rings$sonic;
	}

	@Override
	public float rings$rise() {
		return this.rings$rise;
	}

	@Override
	public void rings$setFastFlight(float sonic, float rise) {
		this.rings$sonic = sonic;
		this.rings$rise = rise;
	}

	@Override
	public boolean rings$firing() {
		return this.rings$firing;
	}

	@Override
	public void rings$setFiring(boolean firing) {
		this.rings$firing = firing;
	}

	@Override
	public @Nullable PlayerSkin rings$ownSkin() {
		return this.rings$ownSkin;
	}

	@Override
	public void rings$setOwnSkin(@Nullable PlayerSkin skin) {
		this.rings$ownSkin = skin;
	}
}
