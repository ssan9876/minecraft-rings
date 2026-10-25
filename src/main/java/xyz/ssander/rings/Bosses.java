package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The enemies out among the stars: Manhunters roaming space and the dead worlds, and the two great foes who wait to
 * be woken. Parallax breaks out of the Central Power Battery on Oa when a yellow ring touches it; Nekron rises on the
 * Moon when a black ring is charged full at a black lantern there.
 */
public final class Bosses {
	/** Each player in space or on the Moon or Mars may draw Manhunters, up to this many near them. */
	private static final int MAX_MANHUNTERS = 3;
	private static final int SPAWN_TICKS = 100;
	private static final float SPAWN_CHANCE = 0.35F;
	/** A great foe won't wake while another of its kind is this near. */
	private static final double BOSS_RANGE = 256.0;

	private Bosses() {
	}

	/** Sends Manhunters after players out among the stars. Runs every server tick. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			boolean hunted = level.dimension() == Space.SPACE || level.dimension() == Space.Planet.MOON.dimension()
					|| level.dimension() == Space.Planet.MARS.dimension();
			if (!hunted || player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL
					|| player.getRandom().nextFloat() > SPAWN_CHANCE
					|| level.getEntitiesOfClass(Manhunter.class, player.getBoundingBox().inflate(64.0)).size() >= MAX_MANHUNTERS) {
				continue;
			}
			float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
			double distance = 28.0 + player.getRandom().nextDouble() * 12.0;
			double x = player.getX() + Mth.cos(angle) * distance;
			double z = player.getZ() + Mth.sin(angle) * distance;
			double y = level.dimension() == Space.SPACE
					? player.getY() + player.getRandom().nextInt(17) - 8
					: level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 4 + player.getRandom().nextInt(7);
			spawn(level, Rings.MANHUNTER, new Vec3(x, y, z));
		}
	}

	private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos) {
		T mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(mob);
		return mob;
	}

	private static boolean awake(ServerLevel level, Class<? extends Mob> kind, BlockPos near) {
		return !level.getEntitiesOfClass(kind, new AABB(near).inflate(BOSS_RANGE)).isEmpty();
	}

	/** A yellow ring touched the Central Power Battery: Parallax breaks free. */
	static void freeParallax(ServerLevel level, BlockPos battery, ServerPlayer freer) {
		if (awake(level, Parallax.class, battery)) {
			return;
		}
		Vec3 above = Vec3.atCenterOf(battery).add(0.0, 14.0, 0.0);
		spawn(level, Rings.PARALLAX, above);
		level.sendParticles(RingColor.YELLOW.dust(0.3F, 2.0F), above.x, above.y, above.z, 200, 3.0, 3.0, 3.0, 0.0);
		level.playSound(null, above.x, above.y, above.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.6F);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.rings.parallax_free", freer.getDisplayName()), false);
	}

	/** A black ring was charged full at a black lantern: on the Moon, that wakes Nekron. */
	static void maybeWakeNekron(ServerLevel level, BlockPos lantern, ServerPlayer waker) {
		if (level.dimension() != Space.Planet.MOON.dimension() || awake(level, Nekron.class, lantern)) {
			return;
		}
		float angle = waker.getYRot() * Mth.DEG_TO_RAD;
		double x = waker.getX() - Mth.sin(angle) * 12.0;
		double z = waker.getZ() + Mth.cos(angle) * 12.0;
		double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		Nekron nekron = spawn(level, Rings.NEKRON, new Vec3(x, y, z));
		if (nekron != null) {
			nekron.setTarget(waker);
		}
		level.sendParticles(ParticleTypes.SOUL, x, y + 2.0, z, 120, 1.5, 2.5, 1.5, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 80, 1.5, 1.5, 1.5, 0.02);
		level.playSound(null, x, y, z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.5F);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.rings.nekron_rises", waker.getDisplayName()), false);
	}
}
