package xyz.ssander.rings.client;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;

/**
 * How ring-flying players look: eases each one between the hover pose and the superhero pose (tipped forward,
 * fist out) by how fast they're going, and leaves a trail of the ring's energy. Coming down to land from flight
 * plays the superhero landing. Runs every client tick.
 */
public final class RingFlight {
	/**
	 * Faster than this horizontally (blocks per tick), a flying player tips forward into the superhero pose.
	 * Flying straight up or down keeps them upright.
	 */
	private static final double DIVE_SPEED = 0.3;
	/** Ticks to ease fully into or out of a pose. */
	private static final float EASE_TICKS = 6.0F;
	/** Landing from flight while coming down faster than this (blocks per tick) plays the superhero landing. */
	private static final double LANDING_SPEED = 0.2;
	/** How long the server can take to say the flight ended after touching down, in ticks. */
	private static final int LANDING_WINDOW = 10;

	private static final Map<Player, Pose> POSES = new WeakHashMap<>();

	private static final class Pose {
		float dive, diveO, hover, hoverO;
		boolean flying;
		/** When they last came down faster than the landing speed. */
		int lastDescent = Integer.MIN_VALUE / 2;
	}

	private RingFlight() {
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}

		for (Player player : level.players()) {
			boolean flying = player.getAttachedOrElse(RingAttachments.FLYING, false);
			Pose pose = POSES.get(player);
			if (pose == null) {
				if (!flying) {
					continue;
				}
				pose = new Pose();
				POSES.put(player, pose);
			}

			Vec3 motion = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
			boolean diving = flying && motion.horizontalDistance() > DIVE_SPEED;
			pose.diveO = pose.dive;
			pose.hoverO = pose.hover;
			pose.dive = approach(pose.dive, diving ? 1.0F : 0.0F);
			pose.hover = approach(pose.hover, flying && !diving ? 1.0F : 0.0F);

			// Flight ends by itself on touching down. The server's word on it can come a tick or two after.
			if (-motion.y > LANDING_SPEED) {
				pose.lastDescent = player.tickCount;
			}
			if (pose.flying && !flying && player.onGround() && player.tickCount - pose.lastDescent <= LANDING_WINDOW) {
				RingAnimations.land(player);
			}
			pose.flying = flying;

			if (flying) {
				trail(level, player, motion, diving);
			} else if (pose.dive == 0.0F && pose.hover == 0.0F) {
				POSES.remove(player);
			}
		}
	}

	/** How far into the superhero pose the player is, 0 to 1. */
	public static float dive(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.diveO, pose.dive));
	}

	/** How far into the hover pose the player is, 0 to 1. */
	public static float hover(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.hoverO, pose.hover));
	}

	private static float approach(float value, float target) {
		float step = 1.0F / EASE_TICKS;
		return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static void trail(ClientLevel level, Player player, Vec3 motion, boolean diving) {
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		RingColor color = costume != null ? costume.color() : RingColor.GREEN;
		RandomSource random = player.getRandom();
		int count = diving ? 4 : 1;
		for (int i = 0; i < count; i++) {
			// Spread along the path just flown, so fast flight leaves a streak
			double back = random.nextDouble() * 1.5;
			double x = player.getX() - motion.x * back + (random.nextDouble() - 0.5) * 0.4;
			double y = player.getY() - motion.y * back + (diving ? 0.3 + random.nextDouble() * 0.6 : random.nextDouble() * 0.2);
			double z = player.getZ() - motion.z * back + (random.nextDouble() - 0.5) * 0.4;
			level.addParticle(random.nextInt(3) == 0 ? color.dust(0.6F, 0.7F) : color.dust(0.0F, 1.0F), x, y, z, 0.0, diving ? 0.0 : -0.05, 0.0);
		}
	}
}
