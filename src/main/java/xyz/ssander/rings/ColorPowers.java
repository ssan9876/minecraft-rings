package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * What each colour of ring does that the others don't: one power on the ring power key, and a gift that's always on
 * while the ring is.
 * <ul>
 * <li>Green (will): a burst of will that throws back everything round about; constructs cost less.</li>
 * <li>Yellow (fear): a wave of terror that sends creatures fleeing; hostile creatures that come close panic.</li>
 * <li>Red (rage): spits burning plasma; rage makes the wearer stronger but less careful (half the ring's armor).</li>
 * <li>Blue (hope): heals everyone near by; nearby green rings don't run down.</li>
 * <li>Orange (greed): calls up copies of the creatures the wearer has defeated (each kind is claimed by the ring).</li>
 * <li>Pink (love): traps a creature in crystal and charms it; sneak to go straight to a friend anywhere.</li>
 * <li>White (life): brings back a player who just died near by, or heals the wearer; the wearer slowly heals.</li>
 * <li>Black (death): drains the life of everything round about; what the wearer kills rises to fight for them.</li>
 * </ul>
 */
public final class ColorPowers {
	private static final int WILL_COST = 40;
	private static final double WILL_RADIUS = 6.0;
	private static final float WILL_DAMAGE = 4.0F;
	private static final int FEAR_COST = 40;
	private static final double FEAR_RADIUS = 14.0;
	private static final int FEAR_TICKS = 12 * 20;
	private static final double FEAR_AURA = 5.0;
	private static final int PLASMA_COST = 20;
	private static final int HOPE_COST = 60;
	private static final double HOPE_RADIUS = 8.0;
	private static final double HOPE_BOOST_RADIUS = 16.0;
	/** Charge a blue ring adds each second to each green ring near by: a little more than one uses switched on. */
	private static final int HOPE_BOOST = 3;
	private static final int CLAIM_COST = 30;
	private static final int CLAIM_COPIES = 3;
	private static final int CLAIM_LIFETIME = 60 * 20;
	private static final int CRYSTAL_COST = 35;
	private static final double CRYSTAL_RANGE = 24.0;
	private static final int CRYSTAL_TICKS = 10 * 20;
	private static final int CHARM_TICKS = 30 * 20;
	/** Going to a friend costs a sixth of the ring; bringing back the dead a sixth too, and healing a little less. */
	public static final int TELEPORT_COST = RingItem.MAX_CHARGE / 6;
	public static final int REVIVE_COST = RingItem.MAX_CHARGE / 6;
	private static final int HEAL_COST = 80;
	private static final double REVIVE_RANGE = 12.0;
	private static final int REVIVE_TICKS = 60 * 20;
	private static final int DRAIN_COST = 30;
	private static final double DRAIN_RADIUS = 8.0;
	private static final float DRAIN_DAMAGE = 3.0F;
	private static final float DRAIN_MAX_HEAL = 10.0F;
	/** Ticks between uses of the ring power. */
	private static final int COOLDOWN = 10;

	private record Death(ResourceKey<Level> dimension, Vec3 pos, long time) {
	}

	private static final Map<ServerPlayer, Long> LAST_USED = new WeakHashMap<>();
	/** Where and when players died, for a white ring to bring them back. */
	private static final Map<UUID, Death> DEATHS = new HashMap<>();

	private ColorPowers() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				DEATHS.put(player.getUUID(), new Death(player.level().dimension(), player.position(), player.level().getGameTime()));
			}
		});
	}

	/** What a construct costs a ring of the colour: green, the ring of will, makes them for less. */
	public static int constructCost(RingConstructs.Construct construct, RingColor color) {
		return color == RingColor.GREEN ? construct.cost() * 3 / 4 : construct.cost();
	}

	/** The ring's armor, which a red ring's rage halves. */
	public static double armor(RingColor color) {
		return color == RingColor.RED ? 4.0 : 8.0;
	}

	/** The wearer pressed the ring power key (sneaking or not). */
	public static void use(ServerPlayer player) {
		RingColor color = RingAttachments.activeColor(player);
		if (color == null) {
			player.sendOverlayMessage(Component.translatable("message.rings.power_inactive"));
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - LAST_USED.getOrDefault(player, Long.MIN_VALUE / 2) < COOLDOWN) {
			return;
		}
		ItemStack ring = RingAttachments.getRing(player);
		int cost = switch (color) {
			case GREEN -> WILL_COST;
			case YELLOW -> FEAR_COST;
			case RED -> PLASMA_COST;
			case BLUE -> HOPE_COST;
			case ORANGE -> CLAIM_COST;
			case PINK -> CRYSTAL_COST;
			case WHITE -> HEAL_COST;
			case BLACK -> DRAIN_COST;
		};
		if (RingItem.charge(ring) < cost) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", ring.getHoverName()));
			return;
		}

		int spent = switch (color) {
			case GREEN -> will(level, player);
			case YELLOW -> fear(level, player);
			case RED -> plasma(level, player);
			case BLUE -> hope(level, player);
			case ORANGE -> claims(level, player, ring);
			case PINK -> crystal(level, player);
			case WHITE -> life(level, player, ring);
			case BLACK -> death(level, player);
		};
		if (spent > 0) {
			LAST_USED.put(player, now);
			RingItem.addCharge(ring, -spent);
			RingPowers.playAnimation(player, RingAnimationPayload.Kind.CONSTRUCT, color);
		}
	}

	/** A burst of will: everything round about is struck and thrown back. */
	private static int will(ServerLevel level, ServerPlayer player) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ConstructFist.DAMAGE_TYPE), player);
		for (LivingEntity target : around(level, player, WILL_RADIUS)) {
			target.hurtServer(level, source, WILL_DAMAGE);
			Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? player.getViewVector(1.0F) : away.normalize();
			target.push(away.scale(1.6).add(0.0, 0.5, 0.0));
			target.needsSync = true;
		}
		ring(level, player, RingColor.GREEN, WILL_RADIUS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
		return WILL_COST;
	}

	/** A wave of terror: creatures round about flee, weakened and slowed. */
	private static int fear(ServerLevel level, ServerPlayer player) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FEAR_RADIUS), mob -> mob.isAlive() && Thralls.of(mob) == null)) {
			MobMoods.scare(mob, player.position(), FEAR_TICKS);
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEAR_TICKS, 0));
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FEAR_TICKS / 2, 0));
		}
		ring(level, player, RingColor.YELLOW, FEAR_RADIUS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 1.2F);
		return FEAR_COST;
	}

	/** A bolt of burning plasma where the wearer looks. */
	private static int plasma(ServerLevel level, ServerPlayer player) {
		Vec3 target = player.getEyePosition().add(player.getViewVector(1.0F).scale(RingBeam.RANGE));
		level.addFreshEntity(EnergyBolt.fire(player, EnergyBolt.Kind.PLASMA, target));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		return PLASMA_COST;
	}

	/** Hope: the wearer and every player near by are healed. */
	private static int hope(ServerLevel level, ServerPlayer player) {
		for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(HOPE_RADIUS), Player::isAlive)) {
			other.heal(8.0F);
			other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
			level.sendParticles(RingColor.BLUE.dust(0.6F, 1.0F), other.getX(), other.getY() + 1.0, other.getZ(), 20, 0.3, 0.6, 0.3, 0.0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, other.getX(), other.getY() + 1.0, other.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
		}
		ring(level, player, RingColor.BLUE, HOPE_RADIUS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.8F);
		return HOPE_COST;
	}

	/** Greed: copies of what the ring has claimed, fighting for the wearer. */
	private static int claims(ServerLevel level, ServerPlayer player, ItemStack ring) {
		List<Identifier> claims = ring.getOrDefault(RingComponents.CLAIMS, List.of());
		if (claims.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.rings.no_claims"));
			return 0;
		}
		int room = Math.min(CLAIM_COPIES, Thralls.MAX_PER_OWNER - Thralls.count(level, player.getUUID(), RingColor.ORANGE));
		int affordable = RingItem.charge(ring) / CLAIM_COST;
		int made = 0;
		List<Component> names = new ArrayList<>();
		for (int i = 0; i < Math.min(room, affordable); i++) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(claims.get(i % claims.size()));
			// Spread out in front of the wearer
			float angle = player.getYRot() * Mth.DEG_TO_RAD + (i - 1) * 0.7F;
			Vec3 pos = player.position().add(-Mth.sin(angle) * 2.5, 0.0, Mth.cos(angle) * 2.5);
			if (Thralls.raise(level, type, pos, player.getUUID(), RingColor.ORANGE, CLAIM_LIFETIME) != null) {
				made++;
				names.add(type.getDescription());
			}
		}
		if (made == 0) {
			player.sendOverlayMessage(Component.translatable("message.rings.too_many_thralls"));
			return 0;
		}
		player.sendOverlayMessage(Component.translatable("message.rings.claims_rise", Component.literal(String.join(", ",
				names.stream().map(Component::getString).toList()))));
		return made * CLAIM_COST;
	}

	/** Love: a crystal round what the wearer looks at, which charms it. */
	private static int crystal(ServerLevel level, ServerPlayer player) {
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		if (!(hit.entity() instanceof LivingEntity target) || player.distanceTo(target) > CRYSTAL_RANGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.crystal_no_target"));
			return 0;
		}
		if (!RingConstructs.boxIn(level, target, RingColor.PINK, CRYSTAL_TICKS)) {
			player.sendOverlayMessage(Component.translatable("message.rings.cage_too_big", target.getDisplayName()));
			return 0;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRYSTAL_TICKS, 3));
		if (target instanceof Mob mob) {
			MobMoods.charm(mob, CHARM_TICKS);
		}
		level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.2F, 1.0F);
		return CRYSTAL_COST;
	}

	/** Sends a pink ring's wearer straight to a friend, wherever they are. */
	public static void teleportTo(ServerPlayer player, UUID friendId) {
		if (RingAttachments.activeColor(player) != RingColor.PINK) {
			return;
		}
		ServerPlayer friend = player.level().getServer().getPlayerList().getPlayer(friendId);
		ItemStack ring = RingAttachments.getRing(player);
		if (friend == null || friend == player || !friend.isAlive()) {
			player.sendOverlayMessage(Component.translatable("message.rings.friend_gone"));
			return;
		}
		if (RingItem.charge(ring) < TELEPORT_COST) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", ring.getHoverName()));
			return;
		}
		RingItem.addCharge(ring, -TELEPORT_COST);
		ServerLevel from = player.level();
		from.sendParticles(RingColor.PINK.dust(0.5F, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
		from.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.4F);
		boolean flying = player.getAbilities().flying;
		Vec3 beside = friend.position().add(friend.getViewVector(1.0F).multiply(1.5, 0.0, 1.5));
		player.teleport(new TeleportTransition(friend.level(), beside, Vec3.ZERO, friend.getYRot() + 180.0F, 0.0F, TeleportTransition.DO_NOTHING));
		if (flying && player.getAbilities().mayfly) {
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
		ServerLevel to = friend.level();
		to.sendParticles(RingColor.PINK.dust(0.5F, 1.2F), beside.x, beside.y + 1.0, beside.z, 40, 0.4, 0.8, 0.4, 0.0);
		to.sendParticles(ParticleTypes.HEART, beside.x, beside.y + 2.0, beside.z, 5, 0.4, 0.2, 0.4, 0.0);
		to.playSound(null, beside.x, beside.y, beside.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.rings.went_to", friend.getDisplayName()));
		friend.sendOverlayMessage(Component.translatable("message.rings.came_to", player.getDisplayName()));
	}

	/**
	 * Life: brings back a player who died near by within the last minute, or with nobody to bring back, heals the
	 * wearer fully and clears what ails them.
	 */
	private static int life(ServerLevel level, ServerPlayer player, ItemStack ring) {
		ServerPlayer revived = revive(level, player, ring);
		if (revived != null) {
			player.sendOverlayMessage(Component.translatable("message.rings.revived", revived.getDisplayName()));
			return REVIVE_COST;
		}
		player.setHealth(player.getMaxHealth());
		player.removeAllEffects();
		level.sendParticles(RingColor.WHITE.dust(0.5F, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
		return HEAL_COST;
	}

	private static @Nullable ServerPlayer revive(ServerLevel level, ServerPlayer player, ItemStack ring) {
		if (RingItem.charge(ring) < REVIVE_COST) {
			return null;
		}
		for (Map.Entry<UUID, Death> entry : DEATHS.entrySet()) {
			Death death = entry.getValue();
			ServerPlayer dead = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (dead == null || dead == player || death.dimension() != level.dimension() || level.getGameTime() - death.time() > REVIVE_TICKS
					|| death.pos().distanceTo(player.position()) > REVIVE_RANGE) {
				continue;
			}
			DEATHS.remove(entry.getKey());
			// Still looking at the death screen: they're brought back to life as if they'd pressed respawn, then here
			ServerPlayer living = dead.isDeadOrDying()
					? level.getServer().getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED)
					: dead;
			living.teleport(new TeleportTransition(level, death.pos(), Vec3.ZERO, living.getYRot(), living.getXRot(), TeleportTransition.DO_NOTHING));
			living.setHealth(living.getMaxHealth() / 2.0F);
			living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
			living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 1));
			level.sendParticles(RingColor.WHITE.dust(0.6F, 1.6F), death.pos().x, death.pos().y + 1.0, death.pos().z, 80, 0.5, 1.2, 0.5, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, death.pos().x, death.pos().y + 1.0, death.pos().z, 30, 0.4, 1.0, 0.4, 0.05);
			level.playSound(null, death.pos().x, death.pos().y, death.pos().z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
			living.sendSystemMessage(Component.translatable("message.rings.brought_back", player.getDisplayName()));
			return living;
		}
		return null;
	}

	/** Death: the life of everything round about drained into the wearer. */
	private static int death(ServerLevel level, ServerPlayer player) {
		DamageSource source = player.damageSources().indirectMagic(player, player);
		float drained = 0.0F;
		for (LivingEntity target : around(level, player, DRAIN_RADIUS)) {
			if (target.hurtServer(level, source, DRAIN_DAMAGE)) {
				drained += DRAIN_DAMAGE;
				Vec3 toward = player.position().subtract(target.position()).normalize().scale(0.3);
				level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0, target.getZ(), 0, toward.x, 0.05, toward.z, 1.0);
				level.sendParticles(RingColor.BLACK.dust(0.1F, 1.2F), target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
			}
		}
		player.heal(Math.min(DRAIN_MAX_HEAL, drained));
		ring(level, player, RingColor.BLACK, DRAIN_RADIUS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
		return DRAIN_COST;
	}

	/** The creatures round the wearer a power can strike: not players, and not the wearer's own thralls. */
	private static List<LivingEntity> around(ServerLevel level, ServerPlayer player, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), target -> target != player && target.isAlive()
				&& !(target instanceof Player) && target.distanceTo(player) <= radius
				&& (Thralls.of(target) == null || Thralls.of(target).owner().filter(player.getUUID()::equals).isEmpty()));
	}

	/** A ring of the colour's energy blasting out along the ground. */
	private static void ring(ServerLevel level, ServerPlayer player, RingColor color, double radius) {
		for (double r = 1.0; r <= radius; r += 1.0) {
			int count = (int) (r * 8);
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count;
				level.sendParticles(color.dust(r < 2 ? 0.6F : 0.0F, 1.2F), player.getX() + Math.cos(angle) * r, player.getY() + 0.3, player.getZ() + Math.sin(angle) * r,
						1, 0.05, 0.1, 0.05, 0.0);
			}
		}
	}

	/** The gifts that are always on with the ring. Runs every server tick for players whose ring is on. */
	static void tick(ServerPlayer player, RingColor color) {
		if (player.tickCount % 20 != 0) {
			return;
		}
		ServerLevel level = player.level();
		switch (color) {
			case RED -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false, true));
			case WHITE -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
			case BLUE -> {
				AABB near = player.getBoundingBox().inflate(HOPE_BOOST_RADIUS);
				for (Player other : level.getEntitiesOfClass(Player.class, near, other -> other != player && RingAttachments.activeColor(other) == RingColor.GREEN)) {
					RingItem.addCharge(RingAttachments.getRing(other), HOPE_BOOST);
				}
			}
			case YELLOW -> {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FEAR_AURA), mob -> mob instanceof Enemy && mob.isAlive())) {
					MobMoods.scare(mob, player.position(), 40);
				}
			}
			default -> {
			}
		}
	}
}
