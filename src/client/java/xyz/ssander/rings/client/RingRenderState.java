package xyz.ssander.rings.client;

import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Ring data added to the player render state (see AvatarRenderStateMixin). */
public interface RingRenderState {
	float rings$dive();

	float rings$hover();

	void rings$setFlight(float dive, float hover);

	/** The player's own skin while they wear a costume (their head is drawn with it), otherwise null. */
	@Nullable PlayerSkin rings$ownSkin();

	void rings$setOwnSkin(@Nullable PlayerSkin skin);
}
