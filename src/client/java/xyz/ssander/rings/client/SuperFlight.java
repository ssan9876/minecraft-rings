package xyz.ssander.rings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingAttachments;

/**
 * Super flight for the local player: while flying on the ring, hold sneak and forward to fly where you look, building
 * up to several times normal flying speed. Let go and the momentum carries you on and eases back into normal
 * flight. Sneak alone still flies down, as in vanilla. Movement is the client's to decide, so this runs on the
 * client, in place of the player's flying movement; everyone else sees the result through the usual movement updates.
 */
public final class SuperFlight {
	/** Top speed, in blocks per tick (40 blocks a second, about twice elytra-with-rockets). */
	public static final double MAX_SPEED = 2.0;
	/** Speed gained each tick while building up: about a second from flying speed to top speed. */
	private static final double ACCELERATION = 0.08;
	/** How quickly the flight turns toward where you look, 0 to 1 per tick. Below 1 so turns sweep rather than snap. */
	private static final double STEERING = 0.3;

	private static boolean active;
	private static double speed;
	private static Vec3 velocity = Vec3.ZERO;

	private SuperFlight() {
	}

	/** Whether the local player is in super flight. */
	public static boolean isActive() {
		return active;
	}

	/**
	 * Moves the player for this tick if they're in super flight, in place of vanilla's flying movement. Returns
	 * whether it did.
	 */
	public static boolean travel(LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean wanted = player.getAbilities().flying
				&& player.getAttachedOrElse(RingAttachments.FLYING, false)
				&& !player.isPassenger()
				&& minecraft.options.keyShift.isDown()
				&& minecraft.options.keyUp.isDown();
		if (!wanted) {
			if (active) {
				// Hand the momentum to vanilla flight, which slows it down with its usual drag
				player.setDeltaMovement(velocity);
				active = false;
			}
			return false;
		}

		if (!active) {
			active = true;
			velocity = player.getDeltaMovement();
			speed = velocity.length();
		}
		speed = Math.min(speed + ACCELERATION, MAX_SPEED);
		velocity = velocity.lerp(player.getViewVector(1.0F).scale(speed), STEERING);
		player.setDeltaMovement(velocity);
		player.move(MoverType.SELF, velocity);

		// Running into something stops you as much as it stops the movement
		if (player.horizontalCollision || player.verticalCollision) {
			velocity = player.getDeltaMovement();
			speed = Math.min(speed, velocity.length() + 0.2);
		}
		return true;
	}

	/** How close to top speed the local player is, 0 to 1, for the camera's speed effects. */
	public static float speedFactor(LocalPlayer player) {
		double moved = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo).length();
		return Mth.clamp((float) ((moved - 0.6) / (MAX_SPEED - 0.6)), 0.0F, 1.0F);
	}
}
