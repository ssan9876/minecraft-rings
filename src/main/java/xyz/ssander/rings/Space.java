package xyz.ssander.rings;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Space travel. Fly up past {@link #LEAVE_EARTH_Y} on a ring and you're in space, over the Earth, with planets hanging
 * in the void around you. Fly into a planet to land on it, back up past {@link #LEAVE_PLANET_Y} to return to space,
 * and down to the Earth below to come home where you left. Space, the Moon and Mars have no air: only an active ring
 * keeps its wearer breathing. Off the Earth, gravity is the planet's own.
 */
public final class Space {
	public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, Rings.id("space"));
	public static final ResourceKey<DamageType> VACUUM = ResourceKey.create(Registries.DAMAGE_TYPE, Rings.id("vacuum"));

	/** Flying higher than this over the Earth takes a ring wearer to space. */
	public static final double LEAVE_EARTH_Y = 450.0;
	/** Flying higher than this over a planet takes you back to space. */
	public static final double LEAVE_PLANET_Y = 400.0;
	/** In space, the Earth's atmosphere starts below this. */
	public static final double EARTH_Y = 0.0;
	/** Where you come out: in space, over the Earth; over a planet; and back on the Earth. */
	private static final Vec3 SPACE_ARRIVAL = new Vec3(0.5, 140.0, 0.5);
	private static final double PLANET_ARRIVAL_Y = 330.0;
	private static final double EARTH_ARRIVAL_Y = 430.0;
	/** Slow falling for someone coming back to the Earth with no flight, long enough to reach the ground. */
	private static final int SAFE_RETURN_TICKS = 90 * 20;

	private static final Identifier GRAVITY_ID = Rings.id("planet_gravity");

	/** The planets: where each hangs in space, how big it is, and what it's like. */
	public enum Planet {
		MOON("moon", new Vec3(0.0, 230.0, -650.0), 80.0, 1.0 / 6.0, false),
		MARS("mars", new Vec3(760.0, 200.0, 300.0), 110.0, 0.38, false),
		OA("oa", new Vec3(-720.0, 260.0, 420.0), 95.0, 1.0, true);

		private final String name;
		private final Vec3 centre;
		private final double radius;
		private final double gravity;
		private final boolean air;
		private final ResourceKey<Level> dimension;

		Planet(String name, Vec3 centre, double radius, double gravity, boolean air) {
			this.name = name;
			this.centre = centre;
			this.radius = radius;
			this.gravity = gravity;
			this.air = air;
			this.dimension = ResourceKey.create(Registries.DIMENSION, Rings.id(name));
		}

		public String id() {
			return this.name;
		}

		/** Its centre in space. */
		public Vec3 centre() {
			return this.centre;
		}

		public double radius() {
			return this.radius;
		}

		public ResourceKey<Level> dimension() {
			return this.dimension;
		}

		public static @Nullable Planet of(ResourceKey<Level> dimension) {
			for (Planet planet : values()) {
				if (planet.dimension == dimension) {
					return planet;
				}
			}
			return null;
		}
	}

	private Space() {
	}

	/** A trip somewhere else: where to, where to come out, and what to tell the traveller. */
	public record Trip(ResourceKey<Level> to, Vec3 position, String message) {
		boolean toEarth() {
			return this.to == Level.OVERWORLD;
		}
	}

	/**
	 * Where something flying at this spot should go, if anywhere: up out of the Earth's sky to space, from space
	 * down to the Earth (to {@code home}) or into a planet, and up out of a planet's sky back to space.
	 */
	public static @Nullable Trip trip(ResourceKey<Level> dimension, Vec3 pos, BlockPos home, RandomSource random) {
		Planet planet = Planet.of(dimension);
		if (dimension == Level.OVERWORLD && pos.y > LEAVE_EARTH_Y) {
			return new Trip(SPACE, SPACE_ARRIVAL.add(random.nextDouble() * 6 - 3, 0.0, random.nextDouble() * 6 - 3), "message.rings.space_enter");
		}
		if (dimension == SPACE) {
			if (pos.y < EARTH_Y) {
				return new Trip(Level.OVERWORLD, new Vec3(home.getX() + 0.5, EARTH_ARRIVAL_Y, home.getZ() + 0.5), "message.rings.space_earth");
			}
			for (Planet target : Planet.values()) {
				if (pos.distanceTo(target.centre) < target.radius) {
					return new Trip(target.dimension, new Vec3(random.nextDouble() * 32 - 16, PLANET_ARRIVAL_Y, random.nextDouble() * 32 - 16),
							"message.rings.planet_" + target.name);
				}
			}
		}
		if (planet != null && pos.y > LEAVE_PLANET_Y) {
			return new Trip(SPACE, planet.centre.add(0.0, planet.radius + 30.0, 0.0), "message.rings.space_enter");
		}
		return null;
	}

	/** Where on the Earth the player comes back down. */
	static BlockPos home(ServerPlayer player) {
		return player.getAttachedOrElse(RingAttachments.EARTH_RETURN, player.level().getServer().overworld().getRespawnData().pos());
	}

	/** Moves players between the Earth, space and the planets, and keeps gravity and air right. Runs every server tick. */
	static void tick(ServerPlayer player) {
		// Carried in a bubble or riding a jet, they go wherever it goes, and it keeps them breathing
		if (player.getVehicle() instanceof ForceBubble || player.getVehicle() instanceof ConstructJet) {
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		Planet planet = Planet.of(dimension);
		boolean ringFlying = player.getAttachedOrElse(RingAttachments.FLYING, false);

		// Only the ring's power gets anyone off the Earth
		Trip trip = trip(dimension, player.position(), home(player), player.getRandom());
		if (trip != null && (dimension != Level.OVERWORLD || ringFlying)) {
			if (dimension == Level.OVERWORLD) {
				player.setAttached(RingAttachments.EARTH_RETURN, player.blockPosition());
			}
			go(player, trip);
			if (trip.toEarth() && !player.getAbilities().flying) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SAFE_RETURN_TICKS, 0, false, false, true));
			}
			return;
		}

		gravity(player, dimension == SPACE ? 0.25 : planet != null ? planet.gravity : 1.0);
		if ((dimension == SPACE || planet != null && !planet.air) && !player.isCreative() && !player.isSpectator()) {
			breathe(player);
		}
	}

	/** Out of air unless the ring is on: vanilla adds air back each tick, so this takes a little more. */
	private static void breathe(ServerPlayer player) {
		if (RingAttachments.isActive(player)) {
			return;
		}
		int air = player.getAirSupply() - 5;
		if (air <= -20) {
			ServerLevel level = player.level();
			player.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(VACUUM)), 2.0F);
			air = 0;
		}
		player.setAirSupply(air);
	}

	/** Gravity, and fall damage with it, as a share of the Earth's. */
	private static void gravity(ServerPlayer player, double share) {
		set(player.getAttribute(Attributes.GRAVITY), share);
		set(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), share);
	}

	private static void set(@Nullable AttributeInstance attribute, double share) {
		if (attribute == null) {
			return;
		}
		AttributeModifier current = attribute.getModifier(GRAVITY_ID);
		if (share == 1.0) {
			if (current != null) {
				attribute.removeModifier(GRAVITY_ID);
			}
		} else if (current == null || current.amount() != share - 1.0) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, share - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void go(ServerPlayer player, Trip trip) {
		ServerLevel level = player.level().getServer().getLevel(trip.to());
		if (level == null) {
			return;
		}
		Vec3 position = trip.position();
		boolean flying = player.getAbilities().flying;
		List<ForceBubble> bubbles = ForceBubble.carriedBy(player);
		player.teleport(new TeleportTransition(level, position, player.getDeltaMovement(), player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		if (flying && player.getAbilities().mayfly) {
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
		// Whatever the wearer carries in a bubble comes too
		for (ForceBubble bubble : bubbles) {
			bubble.teleport(new TeleportTransition(level, position.add(1.5, 0.0, 0.0), Vec3.ZERO, bubble.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
		}
		player.sendOverlayMessage(Component.translatable(trip.message()));
		level.playSound(null, position.x, position.y, position.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** Takes a vehicle and everyone aboard on the trip. */
	static void go(Entity vehicle, Trip trip) {
		if (!(vehicle.level() instanceof ServerLevel from) || from.getServer().getLevel(trip.to()) == null) {
			return;
		}
		ServerLevel level = from.getServer().getLevel(trip.to());
		for (Entity passenger : vehicle.getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				if (from.dimension() == Level.OVERWORLD) {
					player.setAttached(RingAttachments.EARTH_RETURN, player.blockPosition());
				}
				player.sendOverlayMessage(Component.translatable(trip.message()));
			}
		}
		vehicle.teleport(new TeleportTransition(level, trip.position(), vehicle.getDeltaMovement(), vehicle.getYRot(), vehicle.getXRot(), TeleportTransition.DO_NOTHING));
		level.playSound(null, trip.position().x, trip.position().y, trip.position().z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
	}
}
