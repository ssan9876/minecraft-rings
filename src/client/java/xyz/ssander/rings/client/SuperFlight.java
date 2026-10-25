package xyz.ssander.rings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingAttachments;

/**
 * The ring's elytra mode, for the local player. While flying on the ring, sprint forward to switch from hovering into
 * real elytra flight (vanilla's gliding: its pose, its low hitbox and camera) and fly where you look at cruising
 * speed. Hold sneak as well for super flight, building up to many times that. Let go of forward and the momentum
 * carries you back into hovering; touch down and you land on your feet. Movement is the client's to decide, so this
 * runs on the client, in place of the player's flying movement; everyone else sees the result through the usual
 * movement updates.
 */
public final class SuperFlight {
	/** Super flight's top speed, in blocks per tick (80 blocks a second, more than twice elytra-with-rockets). */
	public static final double MAX_SPEED = 4.0;
	/** Elytra mode's cruising speed without sneak, in blocks per tick. */
	public static final double CRUISE_SPEED = 1.5;
	/** Speed gained each tick while building up: about a second and a half from cruising to top speed. */
	private static final double ACCELERATION = 0.14;
	/** How quickly the flight turns toward where you look, 0 to 1 per tick. Below 1 so turns sweep rather than snap. */
	private static final double STEERING = 0.3;

	/** Whether the local player is gliding on the ring, and whether in super flight. */
	private static boolean gliding;
	private static boolean boosting;
	private static double speed;
	private static Vec3 velocity = Vec3.ZERO;

	private SuperFlight() {
	}

	/** Whether the local player is in super flight. */
	public static boolean isActive() {
		return gliding && boosting;
	}

	/** Whether the local player is in the ring's elytra mode. */
	public static boolean isGliding() {
		return gliding;
	}

	/**
	 * Moves the player for this tick if they're in the ring's elytra mode, in place of vanilla's movement, switching
	 * into and out of it as the keys say. Returns whether it moved them.
	 */
	public static boolean travel(LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean forward = minecraft.options.keyUp.isDown();
		boolean boost = minecraft.options.keyShift.isDown();
		boolean sprint = minecraft.options.keySprint.isDown() || player.isSprinting();

		if (gliding && (!player.isFallFlying() || player.onGround() || player.isPassenger())) {
			// Landed, or the ring switched off: the glide is over, and the player stays on their feet
			end(player, false);
			return false;
		}
		if (!gliding) {
			boolean start = forward && (sprint || boost)
					&& player.getAbilities().flying
					&& player.getAttachedOrElse(RingAttachments.FLYING, false)
					&& !player.onGround()
					&& !player.isPassenger();
			if (!start) {
				return false;
			}
			// Hovering off, gliding on: vanilla only glides outside creative flight
			velocity = player.getDeltaMovement();
			speed = velocity.length();
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			if (!player.tryToStartFallFlying()) {
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				return false;
			}
			player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			gliding = true;
		} else if (!forward) {
			// Let go: back to hovering, with the momentum carrying on
			end(player, true);
			return false;
		}

		boosting = boost;
		double target = boost ? MAX_SPEED : CRUISE_SPEED;
		speed = speed < target ? Math.min(speed + ACCELERATION, target) : Math.max(speed - ACCELERATION, target);
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

	/** Leaves elytra mode, either back into hovering or (having landed) onto the ground. */
	private static void end(LocalPlayer player, boolean hover) {
		gliding = false;
		boosting = false;
		player.stopFallFlying();
		if (hover && player.getAbilities().mayfly) {
			// Hand the momentum to vanilla flight, which slows it down with its usual drag
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.setDeltaMovement(velocity);
		}
	}

	/** How close to top speed the local player is, 0 to 1, for the camera's speed effects. */
	public static float speedFactor(LocalPlayer player) {
		double moved = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo).length();
		return Mth.clamp((float) ((moved - 0.6) / (MAX_SPEED - 0.6)), 0.0F, 1.0F);
	}
}
