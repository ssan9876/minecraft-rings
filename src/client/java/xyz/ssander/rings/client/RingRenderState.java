package xyz.ssander.rings.client;

import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Ring data added to the player render state (see AvatarRenderStateMixin). */
public interface RingRenderState {
	float rings$dive();

	float rings$hover();

	void rings$setFlight(float dive, float hover);

	/** How far into the sonic and rising flight poses the player is (see RingFlight). */
	float rings$sonic();

	float rings$rise();

	void rings$setFastFlight(float sonic, float rise);

	/** Whether the player is firing their ring's beam, holding the ring arm out along it. */
	boolean rings$firing();

	void rings$setFiring(boolean firing);

	/** The player's own skin while they wear a costume (their head is drawn with it), otherwise null. */
	@Nullable PlayerSkin rings$ownSkin();

	void rings$setOwnSkin(@Nullable PlayerSkin skin);
}
