package xyz.ssander.rings.client;

/** Ring flight pose amounts, added to the player render state (see AvatarRenderStateMixin). */
public interface RingRenderState {
	float rings$dive();

	float rings$hover();

	void rings$setFlight(float dive, float hover);
}
