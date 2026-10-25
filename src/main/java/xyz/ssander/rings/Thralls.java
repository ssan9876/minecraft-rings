package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.mixin.MobAccessor;

/**
 * Creatures raised by a ring to fight for its wearer: copies of creatures an orange ring has claimed, and the Black
 * Lanterns a black ring raises from what it kills. Each is a real creature of its kind, outlined in the ring's
 * colour, that fights whatever hurts its master or what its master attacks (and hostile creatures near by), follows
 * them about, and fades away after a while, or when it dies, leaving nothing behind. Raised without a master (by
 * Nekron), they hunt players instead.
 */
public final class Thralls {
	/** How many a wearer can have at once, of each colour. */
	public static final int MAX_PER_OWNER = 4;
	/** A Black Lantern rises this many ticks after its kill. */
	private static final int RISE_DELAY = 30;
	private static final int BLACK_LANTERN_LIFETIME = 2 * 60 * 20;
	/** How far they look for something to fight, and how far they let their master get before following. */
	private static final double HUNT_RANGE = 16.0;
	private static final double FOLLOW_DISTANCE = 8.0;
	private static final double TELEPORT_DISTANCE = 28.0;

	/** Who raised a thrall, in what colour, and when it fades (game time). */
	public record Thrall(Optional<UUID> owner, RingColor color, long expires) {
		static final Codec<Thrall> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Thrall::owner),
				StringRepresentable.fromEnum(RingColor::values).fieldOf("color").forGetter(Thrall::color),
				Codec.LONG.fieldOf("expires").forGetter(Thrall::expires)
		).apply(instance, Thrall::new));
	}

	public static final AttachmentType<Thrall> THRALL = AttachmentRegistry.create(Rings.id("thrall"),
			builder -> builder.persistent(Thrall.CODEC));
	/** A thrall's colour, synced to every client for its outline. */
	public static final AttachmentType<RingColor> THRALL_COLOR = AttachmentRegistry.create(Rings.id("thrall_color"),
			builder -> builder.persistent(StringRepresentable.fromEnum(RingColor::values)).syncWith(RingColor.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Creatures that can't be claimed or raised: bosses, and the mod's own. */
	private static final Set<EntityType<?>> EXCLUDED = Set.of(EntityTypes.ENDER_DRAGON, EntityTypes.WITHER, EntityTypes.WARDEN, EntityTypes.ELDER_GUARDIAN);

	private record Rising(ServerLevel level, Vec3 pos, EntityType<?> type, UUID owner, long at) {
	}

	private static final Set<Mob> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
	private static final List<Rising> RISING = new ArrayList<>();

	private Thralls() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(THRALL)) {
				enlist(mob);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob && mob.hasAttached(THRALL)) {
				fade(mob);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Thralls::afterDeath);
	}

	public static @Nullable Thrall of(Entity entity) {
		return entity.getAttached(THRALL);
	}

	/** Whether creatures of this kind can be claimed or raised: hostile ones that fight with goals, not bosses. */
	public static boolean canRaise(Mob mob) {
		return mob instanceof Enemy && !EXCLUDED.contains(mob.getType()) && !mob.hasAttached(THRALL)
				&& !((MobAccessor) mob).rings$targetSelector().getAvailableGoals().isEmpty()
				&& mob.getType().getDescriptionId().startsWith("entity.minecraft.");
	}

	/** Raises a creature of the type at the position, fighting for the owner (or for nobody, if null). */
	public static @Nullable Mob raise(ServerLevel level, EntityType<?> type, Vec3 pos, @Nullable UUID owner, RingColor color, int lifetime) {
		if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob)) {
			return null;
		}
		mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
		mob.setPersistenceRequired();
		// Raised by the ring, they don't burn up in daylight
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		mob.setAttached(THRALL, new Thrall(Optional.ofNullable(owner), color, level.getGameTime() + lifetime));
		mob.setAttached(THRALL_COLOR, color);
		level.addFreshEntityWithPassengers(mob);
		level.sendParticles(color.dust(0.3F, 1.4F), pos.x, pos.y + 1.0, pos.z, 40, 0.4, 0.8, 0.4, 0.0);
		level.playSound(null, pos.x, pos.y, pos.z, color == RingColor.BLACK ? SoundEvents.ZOMBIE_VILLAGER_CURE : SoundEvents.BEACON_POWER_SELECT,
				SoundSource.HOSTILE, 0.8F, color == RingColor.BLACK ? 0.5F : 1.2F);
		return mob;
	}

	/** How many thralls of the colour the owner has. */
	public static int count(ServerLevel level, UUID owner, RingColor color) {
		int count = 0;
		for (Mob mob : ACTIVE) {
			Thrall thrall = of(mob);
			if (!mob.isRemoved() && mob.level() == level && thrall != null && thrall.color() == color && thrall.owner().filter(owner::equals).isPresent()) {
				count++;
			}
		}
		return count;
	}

	/** Swaps a thrall's own idea of who to fight for the ring's: its master's enemies. */
	private static void enlist(Mob mob) {
		MobAccessor ai = (MobAccessor) mob;
		ai.rings$targetSelector().removeAllGoals(goal -> true);
		ai.rings$targetSelector().addGoal(1, new HuntGoal(mob));
		ai.rings$goalSelector().addGoal(1, new FollowMasterGoal(mob));
		ACTIVE.add(mob);
	}

	/** Fades the thrall away, leaving nothing. */
	public static void fade(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			Thrall thrall = of(mob);
			RingColor color = thrall != null ? thrall.color() : RingColor.GREEN;
			level.sendParticles(color.dust(0.4F, 1.2F), mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
			if (color == RingColor.BLACK) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
			}
		}
		mob.discard();
	}

	public static @Nullable Player master(Mob mob) {
		Thrall thrall = of(mob);
		return thrall == null ? null : thrall.owner().map(uuid -> mob.level().getPlayerByUUID(uuid)).orElse(null);
	}

	/** Fades thralls whose time is up or whose master has gone, and raises Black Lanterns. Runs every server tick. */
	static void tick(MinecraftServer server) {
		for (Iterator<Mob> it = ACTIVE.iterator(); it.hasNext(); ) {
			Mob mob = it.next();
			Thrall thrall = of(mob);
			if (mob.isRemoved() || thrall == null) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) mob.level();
			boolean masterGone = thrall.owner().isPresent() && master(mob) == null;
			if (level.getGameTime() > thrall.expires() || masterGone) {
				it.remove();
				fade(mob);
				continue;
			}
			// Daylight would set undead alight: the ring's energy keeps the flames off
			if (mob.isOnFire() && !mob.isInLava()) {
				mob.clearFire();
			}
			if (mob.tickCount % 10 == 0) {
				level.sendParticles(thrall.color() == RingColor.BLACK ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK,
						mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2, 0.25, 0.4, 0.25, 0.0);
			}
		}

		for (Iterator<Rising> it = RISING.iterator(); it.hasNext(); ) {
			Rising rising = it.next();
			if (rising.level().getGameTime() < rising.at()) {
				continue;
			}
			it.remove();
			if (count(rising.level(), rising.owner(), RingColor.BLACK) < MAX_PER_OWNER && rising.level().getPlayerByUUID(rising.owner()) != null) {
				raise(rising.level(), rising.type(), rising.pos(), rising.owner(), RingColor.BLACK, BLACK_LANTERN_LIFETIME);
			}
		}
	}

	/** What a ring wearer kills: an orange ring claims it, and a black ring raises it as a Black Lantern. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(entity instanceof Mob mob) || mob.hasAttached(THRALL)
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		RingColor color = RingAttachments.activeColor(killer);
		if (color == RingColor.ORANGE && canRaise(mob)) {
			claim(RingAttachments.getRing(killer), mob.getType());
			killer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.rings.claimed", mob.getType().getDescription()));
		} else if (color == RingColor.BLACK && !EXCLUDED.contains(mob.getType())) {
			// Whatever falls to a black ring rises again: hostile creatures as themselves, anything else as a zombie
			EntityType<?> type = canRaise(mob) ? mob.getType() : EntityTypes.ZOMBIE;
			RISING.add(new Rising(level, mob.position(), type, killer.getUUID(), level.getGameTime() + RISE_DELAY));
			level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
		}
	}

	/** Adds a kind of creature to what an orange ring has claimed: the most recent first, each kind once. */
	private static void claim(ItemStack ring, EntityType<?> type) {
		Identifier id = EntityType.getKey(type);
		List<Identifier> claims = new ArrayList<>(ring.getOrDefault(RingComponents.CLAIMS, List.of()));
		claims.remove(id);
		claims.addFirst(id);
		while (claims.size() > RingComponents.MAX_CLAIMS) {
			claims.removeLast();
		}
		ring.set(RingComponents.CLAIMS, List.copyOf(claims));
	}

	/** Whether the thrall should leave this one alone: its master and their other thralls. */
	private static boolean isFriend(Mob mob, LivingEntity other) {
		Thrall thrall = of(mob);
		if (thrall == null) {
			return false;
		}
		if (thrall.owner().isEmpty()) {
			// Nekron's dead only fight the living
			return !(other instanceof Player) && (of(other) == null || of(other).owner().isEmpty());
		}
		UUID owner = thrall.owner().get();
		if (other.getUUID().equals(owner)) {
			return true;
		}
		Thrall theirs = of(other);
		return theirs != null && theirs.owner().filter(owner::equals).isPresent();
	}

	/** Chooses who a thrall fights: its master's attacker or target, or else the nearest enemy. */
	private static @Nullable LivingEntity chooseTarget(Mob mob) {
		Thrall thrall = of(mob);
		if (thrall == null) {
			return null;
		}
		if (thrall.owner().isEmpty()) {
			return mob.level().getNearestPlayer(mob, HUNT_RANGE * 1.5) instanceof Player player && !player.isCreative() && !player.isSpectator() ? player : null;
		}
		Player master = master(mob);
		if (master == null) {
			return null;
		}
		for (LivingEntity wanted : new LivingEntity[] {master.getLastHurtByMob(), master.getLastHurtMob()}) {
			if (wanted != null && wanted.isAlive() && wanted != mob && !isFriend(mob, wanted) && wanted.distanceTo(mob) < HUNT_RANGE * 1.5) {
				return wanted;
			}
		}
		LivingEntity nearest = null;
		for (LivingEntity other : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(HUNT_RANGE),
				other -> other instanceof Enemy && other.isAlive() && other != mob && !isFriend(mob, other))) {
			if (nearest == null || other.distanceTo(mob) < nearest.distanceTo(mob)) {
				nearest = other;
			}
		}
		return nearest;
	}

	/** Picks the thrall's target for it (in place of all its own target goals). */
	private static final class HuntGoal extends Goal {
		private final Mob mob;
		private int cooldown;

		HuntGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity current = this.mob.getTarget();
			if (current != null && (!current.isAlive() || isFriend(this.mob, current))) {
				this.mob.setTarget(null);
			}
			if (--this.cooldown > 0) {
				return;
			}
			this.cooldown = 10;
			LivingEntity wanted = chooseTarget(this.mob);
			if (wanted != null && wanted != this.mob.getTarget()) {
				this.mob.setTarget(wanted);
			}
		}
	}

	/** Keeps the thrall near its master while there's nothing to fight, teleporting to them if left far behind. */
	private static final class FollowMasterGoal extends Goal {
		private final Mob mob;

		FollowMasterGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Player master = master(this.mob);
			return master != null && this.mob.getTarget() == null && this.mob.distanceTo(master) > FOLLOW_DISTANCE;
		}

		@Override
		public boolean canContinueToUse() {
			Player master = master(this.mob);
			return master != null && this.mob.getTarget() == null && this.mob.distanceTo(master) > FOLLOW_DISTANCE * 0.5;
		}

		@Override
		public void tick() {
			Player master = master(this.mob);
			if (master == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(master, 10.0F, this.mob.getMaxHeadXRot());
			if (this.mob.distanceTo(master) > TELEPORT_DISTANCE && master.onGround()) {
				this.mob.snapTo(master.getX() + master.getRandom().nextInt(5) - 2, master.getY(), master.getZ() + master.getRandom().nextInt(5) - 2,
						this.mob.getYRot(), this.mob.getXRot());
				this.mob.getNavigation().stop();
			} else if (this.mob.tickCount % 10 == 0) {
				this.mob.getNavigation().moveTo(master, 1.2);
			}
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}
	}
}
