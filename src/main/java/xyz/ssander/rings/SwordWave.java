package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The construct sword's slash: right-click and a crescent of the ring's light flies off the blade where the wearer
 * looks, cutting through everything in its path (its maker and their creatures aside) until it hits a wall or runs
 * out of reach.
 */
public final class SwordWave {
	/** Blocks a wave flies each tick, and for how many ticks. */
	private static final double SPEED = 1.6;
	private static final int LIFETIME = 12;
	/** How far either side of its middle a wave cuts, in blocks. */
	private static final double HALF_WIDTH = 1.6;
	private static final float DAMAGE = 7.0F;
	/** Ring charge each slash costs, and ticks before the sword can slash again. */
	public static final int COST = 8;
	public static final int COOLDOWN = 16;

	private static final class Wave {
		final ServerLevel level;
		final UUID owner;
		final RingColor color;
		final Vec3 direction;
		final Vec3 side;
		final Set<UUID> hit = new HashSet<>();
		Vec3 pos;
		int age;

		Wave(ServerLevel level, UUID owner, RingColor color, Vec3 pos, Vec3 direction) {
			this.level = level;
			this.owner = owner;
			this.color = color;
			this.pos = pos;
			this.direction = direction;
			Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
			this.side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();

	private SwordWave() {
	}

	/** Sends a wave off the wearer's sword. */
	public static void slash(ServerPlayer player, RingColor color) {
		Vec3 look = player.getViewVector(1.0F);
		WAVES.add(new Wave(player.level(), player.getUUID(), color, player.getEyePosition().subtract(0.0, 0.3, 0.0).add(look.scale(0.8)), look));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
	}

	/** Moves every wave on and cuts what it passes through. Runs every server tick. */
	public static void tick(MinecraftServer server) {
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			if (!fly(server, wave)) {
				it.remove();
			}
		}
	}

	/** One tick of a wave's flight; false once it's spent. */
	private static boolean fly(MinecraftServer server, Wave wave) {
		ServerLevel level = wave.level;
		if (server.getLevel(level.dimension()) != level || wave.age++ >= LIFETIME) {
			return false;
		}
		Vec3 from = wave.pos;
		Vec3 to = from.add(wave.direction.scale(SPEED));
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		boolean blocked = wall.getType() != HitResult.Type.MISS;
		if (blocked) {
			to = wall.getLocation();
		}

		ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ConstructFist.DAMAGE_TYPE), owner, owner);
		AABB swept = new AABB(from, to).inflate(HALF_WIDTH, 0.8, HALF_WIDTH);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, swept, e -> e.isAlive() && !e.isSpectator())) {
			if (target.getUUID().equals(wave.owner) || isOwnersCreature(target, wave.owner) || !wave.hit.add(target.getUUID())) {
				continue;
			}
			target.hurtServer(level, source, DAMAGE);
			target.push(wave.direction.x * 0.6, 0.25, wave.direction.z * 0.6);
			target.needsSync = true;
			level.sendParticles(wave.color.dust(0.7F, 1.2F), target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(), 10, 0.25, 0.3, 0.25, 0.0);
		}

		crescent(level, wave, to);
		wave.pos = to;
		if (blocked) {
			level.sendParticles(wave.color.dust(0.5F, 1.4F), to.x, to.y, to.z, 18, 0.4, 0.4, 0.4, 0.0);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
		return !blocked;
	}

	private static boolean isOwnersCreature(LivingEntity entity, UUID owner) {
		Thralls.Thrall thrall = Thralls.of(entity);
		return thrall != null && thrall.owner().filter(owner::equals).isPresent();
	}

	/** The wave itself: a crescent of light, bowed forward, with a white-hot leading edge. */
	private static void crescent(ServerLevel level, Wave wave, Vec3 at) {
		for (int i = -6; i <= 6; i++) {
			double across = i / 6.0;
			double bow = (1.0 - across * across) * 0.6;
			Vec3 point = at.add(wave.side.scale(across * HALF_WIDTH)).add(wave.direction.scale(bow)).add(0.0, across * across * -0.15, 0.0);
			float light = Math.abs(i) < 4 ? 0.75F : 0.2F;
			level.sendParticles(wave.color.dust(light, 1.0F), point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			Vec3 trail = point.subtract(wave.direction.scale(0.5));
			level.sendParticles(wave.color.dust(0.0F, 0.8F), trail.x, trail.y, trail.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}
}
