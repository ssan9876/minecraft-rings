package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ships falling out of the sky. Very rarely, while someone is out under the open sky on the Earth, a burning ship
 * streaks down and crashes a way off from them, and everyone near is told which way it came down; at the crash is its
 * dying pilot with their ring (see CrashSite). Only one fallen ship waits at a time: no other falls until its pilot's
 * ring has been taken. Crash sites also come with the world, as rarely; this is how worlds explored before them get
 * any.
 */
public final class Crashes {
	/** Where the pilot of the last ship to fall waits (kept on the Overworld), until their ring is taken. */
	private static final AttachmentType<BlockPos> WAITING = AttachmentRegistry.create(Rings.id("crash_waiting"),
			builder -> builder.persistent(BlockPos.CODEC));

	/**
	 * How often, on average, a ship falls while someone is out under the sky: in minutes, 0 for never (kept on the
	 * Overworld; /rings crashes sets it). A chance is taken each minute.
	 */
	private static final AttachmentType<Integer> EVERY = AttachmentRegistry.create(Rings.id("crash_every"),
			builder -> builder.persistent(Codec.INT));
	public static final int DEFAULT_EVERY = 150;
	private static final int CHECK_TICKS = 60 * 20;
	/** How far from the player it comes down. */
	private static final double MIN_DISTANCE = 96.0;
	private static final double MAX_DISTANCE = 192.0;
	/** How long it takes to fall, from how far up and how far back along its path. */
	private static final int FALL_TICKS = 90;
	private static final double FALL_HEIGHT = 160.0;
	private static final double FALL_RUN = 220.0;
	/** Everyone this close hears it come down and is told where. */
	private static final double HEARD = 800.0;

	private static final class Fall {
		final ServerLevel level;
		final Vec3 from;
		final Vec3 to;
		final RingColor color;
		int age;

		Fall(ServerLevel level, Vec3 from, Vec3 to, RingColor color) {
			this.level = level;
			this.from = from;
			this.to = to;
			this.color = color;
		}

		Vec3 at(double t) {
			// Steepening as it falls
			Vec3 flat = this.from.lerp(this.to, t);
			return new Vec3(flat.x, Mth.lerp(t * t, this.from.y, this.to.y), flat.z);
		}
	}

	private static final List<Fall> FALLING = new ArrayList<>();

	private Crashes() {
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Fall> it = FALLING.iterator(); it.hasNext(); ) {
			Fall fall = it.next();
			if (fall.level.getServer() != server) {
				it.remove();
				continue;
			}
			fall.age++;
			if (fall.age < FALL_TICKS) {
				streak(fall);
			} else {
				it.remove();
				land(fall);
			}
		}

		ServerLevel overworld = server.overworld();
		if (overworld.getGameTime() % CHECK_TICKS != 0 || overworld.hasAttached(WAITING) || !FALLING.isEmpty()) {
			return;
		}
		RandomSource random = overworld.getRandom();
		int every = every(server);
		if (every <= 0 || random.nextInt(every) != 0) {
			return;
		}
		List<ServerPlayer> outside = overworld.players().stream()
				.filter(player -> !player.isSpectator() && !player.isCreative() && overworld.canSeeSky(player.blockPosition()))
				.toList();
		if (!outside.isEmpty()) {
			fall(overworld, outside.get(random.nextInt(outside.size())), null, MIN_DISTANCE, MAX_DISTANCE);
		}
	}

	/** How often ships fall, on average, in minutes (0 for never). */
	public static int every(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(EVERY, DEFAULT_EVERY);
	}

	public static void setEvery(MinecraftServer server, int minutes) {
		server.overworld().setAttached(EVERY, minutes);
	}

	/**
	 * Sends a ship down somewhere between the distances from the player, of the colour (or null for whatever falls
	 * there). Returns where it'll come down.
	 */
	public static BlockPos fall(ServerLevel level, ServerPlayer near, @Nullable RingColor color, double minDistance, double maxDistance) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = Mth.lerp(random.nextDouble(), minDistance, maxDistance);
		int x = Mth.floor(near.getX() + Math.cos(angle) * distance);
		int z = Mth.floor(near.getZ() + Math.sin(angle) * distance);
		// Load (or make) the ground it comes down on
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				level.getChunk((x >> 4) + cx, (z >> 4) + cz);
			}
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		double heading = random.nextDouble() * Math.PI * 2.0;
		Vec3 to = new Vec3(x + 0.5, y + 1.0, z + 0.5);
		if (color == null) {
			color = CrashSite.pickColor(random, level.getBiome(BlockPos.containing(to)));
		}
		Vec3 from = to.add(-Math.cos(heading) * FALL_RUN, FALL_HEIGHT, -Math.sin(heading) * FALL_RUN);
		FALLING.add(new Fall(level, from, to, color));
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(to) < HEARD) {
				player.sendSystemMessage(Component.translatable("message.rings.crash_seen").withColor(0xFFB060));
			}
		}
		return BlockPos.containing(to);
	}

	/** The burning ship in the sky, seen from far off. */
	private static void streak(Fall fall) {
		ServerLevel level = fall.level;
		double t = (double) fall.age / FALL_TICKS;
		Vec3 head = fall.at(t);
		Vec3 back = fall.at(Math.max(0.0, t - 0.02));
		for (int i = 0; i < 4; i++) {
			Vec3 p = back.lerp(head, i / 4.0);
			level.sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y, p.z, 6, 0.6, 0.6, 0.6, 0.02);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, p.x, p.y, p.z, 4, 0.8, 0.8, 0.8, 0.02);
			level.sendParticles(fall.color.dust(0.3F, 2.0F), true, true, p.x, p.y, p.z, 4, 0.5, 0.5, 0.5, 0.0);
		}
		level.sendParticles(ParticleTypes.LAVA, true, true, head.x, head.y, head.z, 2, 0.4, 0.4, 0.4, 0.0);
		if (fall.age % 10 == 0) {
			level.playSound(null, head.x, head.y, head.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 12.0F, 0.5F);
		}
	}

	/** It comes down: a blast, the crash site, and word to everyone near of which way it fell. */
	private static void land(Fall fall) {
		ServerLevel level = fall.level;
		Vec3 to = fall.to;
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, to.x, to.y, to.z, 3, 2.0, 1.0, 2.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, to.x, to.y + 2.0, to.z, 40, 3.0, 2.0, 3.0, 0.02);
		level.sendParticles(fall.color.dust(0.4F, 2.0F), true, true, to.x, to.y + 1.0, to.z, 120, 4.0, 2.0, 4.0, 0.0);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 16.0F, 0.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 16.0F, 0.7F);

		BlockPos pilot = CrashSite.build(level, BlockPos.containing(to), level.getRandom(), fall.color, Heightmap.Types.WORLD_SURFACE, Block.UPDATE_ALL, false);
		if (pilot == null) {
			// Into water: lost
			return;
		}
		level.getServer().overworld().setAttached(WAITING, pilot);
		for (ServerPlayer player : level.players()) {
			double distance = player.position().distanceTo(to);
			if (distance < HEARD) {
				player.sendSystemMessage(Component.translatable("message.rings.crash_landed",
						Component.translatable(direction(player.position(), to)), Math.round(distance)).withColor(0xFFB060));
			}
		}
	}

	/** Which way one place is from another, as a lang key: north, north-east and so on. */
	static String direction(Vec3 from, Vec3 to) {
		double angle = Math.toDegrees(Math.atan2(to.x - from.x, -(to.z - from.z)));
		String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
		int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
		return "direction.rings." + names[index];
	}

	/** A pilot's ring was taken: if theirs was the ship waiting, another can fall. */
	static void claimed(ServerLevel level, BlockPos pos) {
		ServerLevel overworld = level.getServer().overworld();
		@Nullable BlockPos waiting = overworld.getAttached(WAITING);
		if (level == overworld && waiting != null && waiting.distSqr(pos) < 32 * 32) {
			overworld.removeAttached(WAITING);
		}
	}

	/** Forgets the waiting ship, so another can fall (for operators). */
	static void forget(MinecraftServer server) {
		server.overworld().removeAttached(WAITING);
	}
}
