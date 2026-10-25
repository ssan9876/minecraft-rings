package xyz.ssander.rings.gametest;

/** Lets a test film the player from the side in third person (see CameraMixin). */
public final class TestCamera {
	/** Degrees the third-person camera swings around the player. 90 is side-on. */
	public static volatile float yawOffset = 0.0F;

	private TestCamera() {
	}
}
