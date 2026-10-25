package xyz.ssander.rings.client;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;

/**
 * How ring-flying players look, for everyone watching. Each one eases between poses by how they're moving:
 * <ul>
 * <li>hovering: upright, limbs hanging loose, whichever way they move;</li>
 * <li>cruising, while sprinting or in super flight: tipped along the flight, fist out ahead, banking into turns;</li>
 * <li>sonic, near super flight's top speed: streamlined, with a sonic boom on breaking through and a thick trail;</li>
 * <li>rising fast: upright, fist to the sky.</li>
 * </ul>
 * Everyone leaves a trail of the ring's energy. Coming down to land plays the superhero landing, with a shockwave if
 * it was fast. Runs every client tick.
 */
public final class RingFlight {
	/**
	 * Flying players stay upright whichever way they move, until they sprint (moving at least this fast
	 * horizontally, in blocks per tick) or go faster than super flight's {@link #SUPER_SPEED}: then they tip into the
	 * cruising pose. In super flight that includes diving down head first.
	 */
	private static final double SPRINT_SPEED = 0.1;
	private static final double SUPER_SPEED = 1.0;
	/** Rising faster than this (blocks per tick) raises the fist to the sky. Normal flying rises at about 0.4. */
	private static final double RISE_SPEED = 0.55;
	/** Sonic from this speed (80% of super flight's top speed), and ready for another sonic boom below the second. */
	private static final double SONIC_SPEED = SuperFlight.MAX_SPEED * 0.8;
	private static final double SONIC_REARM_SPEED = 1.0;
	/** Ticks to ease fully into or out of a pose. */
	private static final float EASE_TICKS = 6.0F;
	/** Landing from flight while coming down faster than this (blocks per tick) plays the superhero landing. */
	private static final double LANDING_SPEED = 0.2;
	/** Landing within a moment of going this fast (blocks per tick) shakes the ground. */
	private static final double IMPACT_SPEED = 1.0;
	/** How long the server can take to say the flight ended after touching down, in ticks. */
	private static final int LANDING_WINDOW = 10;

	private static final Map<Player, Pose> POSES = new WeakHashMap<>();
	private static WindSound wind;

	private static final class Pose {
		float dive, diveO, hover, hoverO, sonic, sonicO, rise, riseO;
		boolean flying;
		boolean boomReady = true;
		/** When they last came down faster than the landing speed, and last went fast enough to shake the ground. */
		int lastDescent = Integer.MIN_VALUE / 2;
		int lastFast = Integer.MIN_VALUE / 2;
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
			double speed = motion.length();
			// Rocketing mostly straight up keeps them upright, fist to the sky, however fast
			boolean rising = flying && motion.y > RISE_SPEED && motion.horizontalDistance() < motion.y * 0.5;
			boolean diving = flying && !rising && (player.isSprinting() && motion.horizontalDistance() > SPRINT_SPEED || speed > SUPER_SPEED);
			boolean sonic = diving && speed > SONIC_SPEED;
			pose.diveO = pose.dive;
			pose.hoverO = pose.hover;
			pose.sonicO = pose.sonic;
			pose.riseO = pose.rise;
			pose.dive = approach(pose.dive, diving ? 1.0F : 0.0F);
			pose.hover = approach(pose.hover, flying && !diving && !rising ? 1.0F : 0.0F);
			pose.sonic = approach(pose.sonic, sonic ? 1.0F : 0.0F);
			pose.rise = approach(pose.rise, rising ? 1.0F : 0.0F);

			if (sonic && pose.boomReady) {
				sonicBoom(level, player, motion);
				pose.boomReady = false;
			} else if (speed < SONIC_REARM_SPEED) {
				pose.boomReady = true;
			}

			// Flight ends by itself on touching down. The server's word on it can come a tick or two after.
			if (-motion.y > LANDING_SPEED) {
				pose.lastDescent = player.tickCount;
			}
			if (speed > IMPACT_SPEED) {
				pose.lastFast = player.tickCount;
			}
			if (pose.flying && !flying && player.onGround() && player.tickCount - pose.lastDescent <= LANDING_WINDOW) {
				RingAnimations.land(player);
				// Touching down ends the flying poses at once, so the landing isn't played lying flat
				pose.dive = pose.diveO = pose.sonic = pose.sonicO = 0.0F;
				if (player.tickCount - pose.lastFast <= LANDING_WINDOW) {
					impact(level, player);
				}
			}
			pose.flying = flying;

			if (flying) {
				trail(level, player, motion, diving, sonic);
			} else if (pose.dive == 0.0F && pose.hover == 0.0F && pose.sonic == 0.0F && pose.rise == 0.0F) {
				POSES.remove(player);
			}
		}

		// The rush of wind at speed, for the local player
		if (minecraft.player != null && POSES.containsKey(minecraft.player) && (wind == null || wind.isStopped())) {
			wind = new WindSound(minecraft.player);
			minecraft.getSoundManager().play(wind);
		}
	}

	/** How far into the cruising pose the player is, 0 to 1. */
	public static float dive(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.diveO, pose.dive));
	}

	/** How far into the hover pose the player is, 0 to 1. */
	public static float hover(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.hoverO, pose.hover));
	}

	/** How far into the sonic pose the player is, 0 to 1. It goes on top of the cruising pose. */
	public static float sonic(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.sonicO, pose.sonic));
	}

	/** How far into the rising pose (fist to the sky) the player is, 0 to 1. */
	public static float rise(Player player, float partialTicks) {
		Pose pose = POSES.get(player);
		return pose == null ? 0.0F : smooth(Mth.lerp(partialTicks, pose.riseO, pose.rise));
	}

	private static float approach(float value, float target) {
		float step = 1.0F / EASE_TICKS;
		return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static RingColor color(Player player) {
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		return costume != null ? costume.color() : RingColor.GREEN;
	}

	private static void trail(ClientLevel level, Player player, Vec3 motion, boolean diving, boolean sonic) {
		RingColor color = color(player);
		RandomSource random = player.getRandom();
		int count = sonic ? 10 : diving ? 4 : 1;
		for (int i = 0; i < count; i++) {
			// Spread along the path just flown, so fast flight leaves a streak
			double back = random.nextDouble() * 1.5;
			double spread = sonic ? 0.25 : 0.4;
			double x = player.getX() - motion.x * back + (random.nextDouble() - 0.5) * spread;
			double y = player.getY() - motion.y * back + (diving ? 0.3 + random.nextDouble() * 0.6 : random.nextDouble() * 0.2);
			double z = player.getZ() - motion.z * back + (random.nextDouble() - 0.5) * spread;
			// At sonic speed the trail is a white-hot streak with the energy around it
			float light = sonic && i % 3 == 0 ? 0.9F : random.nextInt(3) == 0 ? 0.6F : 0.0F;
			level.addParticle(color.dust(light, sonic ? 1.3F : 1.0F), x, y, z, 0.0, diving ? 0.0 : -0.05, 0.0);
		}
	}

	/** Breaking through: a boom, a shock ring around the flight path, and a burst of the ring's energy. */
	private static void sonicBoom(ClientLevel level, Player player, Vec3 motion) {
		Vec3 centre = player.position().add(0.0, 0.9, 0.0);
		level.playLocalSound(centre.x, centre.y, centre.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.5F, false);
		level.addParticle(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 0.0, 0.0, 0.0);

		// A ring square to the direction of flight
		Vec3 forward = motion.normalize();
		Vec3 side = forward.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(forward);
		RingColor color = color(player);
		for (int i = 0; i < 32; i++) {
			float angle = Mth.TWO_PI * i / 32;
			Vec3 around = side.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
			Vec3 outer = centre.add(around.scale(1.6));
			Vec3 inner = centre.add(around.scale(1.0));
			level.addParticle(color.dust(0.85F, 1.1F), outer.x, outer.y, outer.z, 0.0, 0.0, 0.0);
			level.addParticle(color.dust(0.0F, 1.4F), inner.x, inner.y, inner.z, 0.0, 0.0, 0.0);
		}
	}

	/** Hitting the ground fast: a heavy thud and a ring of the ground thrown up around the landing. */
	private static void impact(ClientLevel level, Player player) {
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		RandomSource random = player.getRandom();
		level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F, false);
		RingColor color = color(player);
		for (int i = 0; i < 48; i++) {
			float angle = Mth.TWO_PI * i / 48;
			double radius = 0.6 + random.nextDouble() * 2.2;
			double x = player.getX() + Mth.cos(angle) * radius;
			double z = player.getZ() + Mth.sin(angle) * radius;
			if (!ground.isAir()) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, player.getY() + 0.1, z,
						Mth.cos(angle) * 0.3, 0.25 + random.nextDouble() * 0.3, Mth.sin(angle) * 0.3);
			}
			if (i % 2 == 0) {
				level.addParticle(color.dust(0.3F, 1.2F), x, player.getY() + 0.15, z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** The rush of wind, louder the faster the local player flies. */
	private static final class WindSound extends AbstractTickableSoundInstance {
		private final Player player;

		WindSound(Player player) {
			super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, player.getRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0F;
			this.relative = true;
		}

		@Override
		public boolean canStartSilent() {
			// It starts silent and swells with speed
			return true;
		}

		@Override
		public void tick() {
			if (this.player.isRemoved() || !POSES.containsKey(this.player)) {
				this.stop();
				return;
			}
			double speed = new Vec3(this.player.getX() - this.player.xo, this.player.getY() - this.player.yo, this.player.getZ() - this.player.zo).length();
			float strength = Mth.clamp((float) ((speed - 0.5) / (SuperFlight.MAX_SPEED - 0.5)), 0.0F, 1.0F);
			this.volume = strength * 0.9F;
			this.pitch = 0.8F + strength * 0.5F;
		}
	}
}
