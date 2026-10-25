package xyz.ssander.rings;

import java.util.Objects;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.RingAnimationPayload;

/** Server-side ring logic: switching it on and off, its power, and keeping its powers in line every tick. */
public final class RingPowers {
	/** Slow falling after flight ends in mid-air, so survival players don't fall to their death. */
	private static final int SAFE_LANDING_TICKS = 100;
	/** Charge used each second while the ring is on, and while flying on it. */
	private static final int DRAIN_ACTIVE = 1;
	private static final int DRAIN_FLYING = 3;
	/** Extra charge used each second in super flight (flying faster than {@link #SUPER_FLIGHT_SPEED} blocks a tick). */
	private static final int DRAIN_SUPER_FLIGHT = 5;
	private static final double SUPER_FLIGHT_SPEED = 2.0;
	/** Extra charge used each second while firing the beam. */
	private static final int DRAIN_FIRING = 4;
	/** Each interaction with a power lantern (every 4 ticks while right-click is held) adds this much. */
	private static final int CHARGE_PER_USE = RingItem.MAX_CHARGE / 30;
	/** Particles in each pulse of the stream from the lantern to the ring. */
	private static final int STREAM_PARTICLES = 24;
	/** The ring's energy shields its wearer: about a full set of iron armor, on top of what they wear. */
	private static final Identifier RING_ARMOR = Rings.id("ring_armor");
	private static final AttributeModifier RING_TOUGHNESS = new AttributeModifier(Rings.id("ring_toughness"), 2.0, AttributeModifier.Operation.ADD_VALUE);

	private RingPowers() {
	}

	public static void toggle(ServerPlayer player) {
		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem ringItem)) {
			player.sendOverlayMessage(Component.translatable("message.rings.no_ring"));
			return;
		}

		boolean active = !RingAttachments.isActive(player);
		ServerLevel level = player.level();
		if (active && RingItem.charge(ring) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.rings.no_power", ring.getHoverName(), Rings.lantern(ringItem.color()).getName()));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
			return;
		}

		player.setAttached(RingAttachments.ACTIVE, active ? true : null);
		tick(player);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				active ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, active ? 1.4F : 1.2F);
		// The ring's energy bursts out on activation, and a smaller puff on deactivation
		RingColor color = ringItem.color();
		level.sendParticles(active ? color.dust(0.0F, 1.5F) : color.dust(0.0F, 1.0F),
				player.getX(), player.getY() + 1.0, player.getZ(), active ? 60 : 25, 0.5, 0.9, 0.5, 0.0);
		if (active) {
			playAnimation(player, RingAnimationPayload.Kind.ACTIVATE, color);
		}
		player.sendOverlayMessage(Component.translatable(active ? "message.rings.activated" : "message.rings.deactivated", ring.getHoverName()));
	}

	/** Uses power, and grants or removes powers to match the worn ring. Runs every server tick. */
	public static void tick(ServerPlayer player) {
		ItemStack stack = RingAttachments.getRing(player);
		RingItem ring = stack.getItem() instanceof RingItem r ? r : null;
		if (RingAttachments.isActive(player)) {
			if (ring == null) {
				// Taking the ring off switches it off
				player.removeAttached(RingAttachments.ACTIVE);
			} else if (player.tickCount % 20 == 0 && RingItem.addCharge(stack,
					-drain(player)) == 0) {
				player.removeAttached(RingAttachments.ACTIVE);
				player.sendOverlayMessage(Component.translatable("message.rings.out_of_power", stack.getHoverName()));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}

		RingItem active = RingAttachments.isActive(player) ? ring : null;
		if (active == null) {
			if (RingBeam.isFiring(player)) {
				player.removeAttached(RingAttachments.FIRING);
			}
			if (player.getAttachedOrElse(RingAttachments.SHIELD, false)) {
				player.removeAttached(RingAttachments.SHIELD);
			}
		} else if (RingBeam.isFiring(player)) {
			RingBeam.tick(player);
		}
		updateFlight(player, active != null && active.grantsFlight());
		updateArmor(player, active != null ? active.color() : null);
		if (active != null) {
			ColorPowers.tick(player, active.color());
		}
		updateCostume(player, active != null ? active.costume() : null);
		RingColor worn = ring != null ? ring.color() : null;
		if (worn != player.getAttached(RingAttachments.WORN_RING)) {
			player.setAttached(RingAttachments.WORN_RING, worn);
		}

		// Flying covers both hovering (vanilla's creative flight) and the ring's elytra mode
		boolean ringFlying = active != null && active.grantsFlight() && (player.getAbilities().flying || player.isFallFlying());
		if (ringFlying != player.getAttachedOrElse(RingAttachments.FLYING, false)) {
			player.setAttached(RingAttachments.FLYING, ringFlying ? true : null);
		}
	}

	/**
	 * Tops up a ring from a power lantern: the worn ring, or one held in the main hand, whichever matches the
	 * lantern's colour.
	 */
	public static void chargeAt(ServerPlayer player, ServerLevel level, BlockPos pos, PowerLanternBlock lantern) {
		ItemStack worn = RingAttachments.getRing(player);
		ItemStack held = player.getMainHandItem();
		ItemStack stack = matches(worn, lantern) ? worn : matches(held, lantern) ? held : null;
		if (stack == null) {
			ItemStack other = worn.getItem() instanceof RingItem ? worn : held;
			if (other.getItem() instanceof RingItem ring) {
				player.sendOverlayMessage(Component.translatable("message.rings.wrong_lantern",
						other.getHoverName(), Rings.lantern(ring.color()).getName()));
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.8F);
			} else {
				player.sendOverlayMessage(Component.translatable("message.rings.charge_no_ring"));
			}
			return;
		}
		if (RingItem.charge(stack) >= RingItem.MAX_CHARGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.already_charged", stack.getHoverName()));
			return;
		}

		int charge = RingItem.addCharge(stack, CHARGE_PER_USE);
		boolean full = charge >= RingItem.MAX_CHARGE;

		// A stream of energy pouring from the lantern into the ring fist, held out to it in the charging pose: a
		// bright core with the lantern's energy swirling around it, thickest at the lantern
		Vec3 from = Vec3.atCenterOf(pos);
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 to = player.position().add(-Mth.sin(yaw) * 0.7, 1.05, Mth.cos(yaw) * 0.7);
		RingColor color = lantern.color();
		for (int i = 0; i < STREAM_PARTICLES; i++) {
			double along = level.getRandom().nextDouble();
			double spread = 0.12 * (1.0 - along);
			Vec3 point = from.lerp(to, along);
			boolean core = i % 3 == 0;
			level.sendParticles(color.dust(core ? 0.75F : 0.0F, core ? 0.5F : 0.7F + level.getRandom().nextFloat() * 0.4F),
					point.x, point.y, point.z, 1, spread, spread, spread, 0.0);
		}
		level.sendParticles(color.dust(0.5F, 1.0F), from.x, from.y, from.z, 4, 0.2, 0.25, 0.2, 0.0);

		playAnimation(player, full ? RingAnimationPayload.Kind.CHARGE_FULL : RingAnimationPayload.Kind.CHARGE, lantern.color());
		level.playSound(null, pos, full ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
				1.0F, 0.6F + 1.2F * charge / RingItem.MAX_CHARGE);
		player.sendOverlayMessage(full
				? Component.translatable("message.rings.charged", stack.getHoverName())
				: Component.translatable("message.rings.charging", charge * 100 / RingItem.MAX_CHARGE + "%"));
	}

	/** Charge the ring uses this second. */
	private static int drain(ServerPlayer player) {
		boolean flying = player.getAbilities().flying || player.isFallFlying();
		int drain = flying ? DRAIN_FLYING : DRAIN_ACTIVE;
		if (flying && player.getKnownMovement().length() > SUPER_FLIGHT_SPEED) {
			drain += DRAIN_SUPER_FLIGHT;
		}
		if (RingBeam.isFiring(player)) {
			drain += DRAIN_FIRING;
		}
		if (player.getAttachedOrElse(RingAttachments.SHIELD, false)) {
			drain += RingAbilities.SHIELD_DRAIN;
		}
		if (ForceBubble.isCarrying(player)) {
			drain += RingAbilities.CARRY_DRAIN;
		}
		return drain;
	}

	private static boolean matches(ItemStack stack, PowerLanternBlock lantern) {
		return stack.getItem() instanceof RingItem ring && ring.color() == lantern.color();
	}

	/** Plays a ring animation on the player for them and everyone who can see them. */
	static void playAnimation(ServerPlayer player, RingAnimationPayload.Kind kind, RingColor color) {
		RingAnimationPayload payload = new RingAnimationPayload(player.getId(), kind, color);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}

	private static void updateFlight(ServerPlayer player, boolean wanted) {
		Abilities abilities = player.getAbilities();
		boolean granted = player.getAttachedOrElse(RingAttachments.FLIGHT_GRANTED, false);
		if (wanted) {
			// Also re-grants it after a game mode change resets abilities
			if (!abilities.mayfly) {
				abilities.mayfly = true;
				player.onUpdateAbilities();
			}

			if (!granted) {
				player.setAttached(RingAttachments.FLIGHT_GRANTED, true);
			}
		} else if (granted) {
			player.removeAttached(RingAttachments.FLIGHT_GRANTED);
			// Creative and spectator players fly anyway
			if (!player.isCreative() && !player.isSpectator()) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
				if (!player.onGround()) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SAFE_LANDING_TICKS, 0, false, false, true));
				}
			}
		}
	}

	/** The ring's armor, for a ring of the colour (or none for no ring on). */
	private static void updateArmor(ServerPlayer player, @Nullable RingColor color) {
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
		double wanted = color != null ? ColorPowers.armor(color) : 0.0;
		AttributeModifier current = armor == null ? null : armor.getModifier(RING_ARMOR);
		if (armor != null && (current == null ? wanted != 0.0 : current.amount() != wanted)) {
			armor.removeModifier(RING_ARMOR);
			if (wanted != 0.0) {
				armor.addTransientModifier(new AttributeModifier(RING_ARMOR, wanted, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		updateModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), RING_TOUGHNESS, color != null);
	}

	/** Transient, so it isn't saved: the next tick after loading puts it back if the ring is still on. */
	private static void updateModifier(@Nullable AttributeInstance attribute, AttributeModifier modifier, boolean wanted) {
		if (attribute == null || attribute.hasModifier(modifier.id()) == wanted) {
			return;
		}
		if (wanted) {
			attribute.addTransientModifier(modifier);
		} else {
			attribute.removeModifier(modifier.id());
		}
	}

	private static void updateCostume(ServerPlayer player, @Nullable Identifier costume) {
		if (!Objects.equals(costume, player.getAttached(RingAttachments.COSTUME))) {
			player.setAttached(RingAttachments.COSTUME, costume);
		}
	}
}
