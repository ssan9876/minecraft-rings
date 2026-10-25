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
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
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
	/** Each interaction with a power lantern (every 4 ticks while right-click is held) adds this much. */
	private static final int CHARGE_PER_USE = RingItem.MAX_CHARGE / 10;

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
			playAnimation(player, RingAnimationPayload.Kind.ACTIVATE);
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
			} else if (player.tickCount % 20 == 0
					&& RingItem.addCharge(stack, -(player.getAbilities().flying ? DRAIN_FLYING : DRAIN_ACTIVE)) == 0) {
				player.removeAttached(RingAttachments.ACTIVE);
				player.sendOverlayMessage(Component.translatable("message.rings.out_of_power", stack.getHoverName()));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}

		RingItem active = RingAttachments.isActive(player) ? ring : null;
		updateFlight(player, active != null && active.grantsFlight());
		updateCostume(player, active != null ? active.costume() : null);

		boolean ringFlying = active != null && active.grantsFlight() && player.getAbilities().flying;
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

		// A thin stream of energy from the lantern toward the wearer's hand, stopping short of their face
		Vec3 from = Vec3.atCenterOf(pos);
		Vec3 to = player.position().add(0.0, 0.9, 0.0);
		for (int i = 0; i <= 6; i++) {
			Vec3 point = from.lerp(to, i / 10.0);
			level.sendParticles(lantern.color().dust(0.0F, 0.6F), point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
		}

		playAnimation(player, RingAnimationPayload.Kind.CHARGE);
		level.playSound(null, pos, full ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
				1.0F, 0.6F + 1.2F * charge / RingItem.MAX_CHARGE);
		player.sendOverlayMessage(full
				? Component.translatable("message.rings.charged", stack.getHoverName())
				: Component.translatable("message.rings.charging", charge * 100 / RingItem.MAX_CHARGE + "%"));
	}

	private static boolean matches(ItemStack stack, PowerLanternBlock lantern) {
		return stack.getItem() instanceof RingItem ring && ring.color() == lantern.color();
	}

	/** Plays a ring animation on the player for them and everyone who can see them. */
	private static void playAnimation(ServerPlayer player, RingAnimationPayload.Kind kind) {
		RingAnimationPayload payload = new RingAnimationPayload(player.getId(), kind);
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

	private static void updateCostume(ServerPlayer player, @Nullable Identifier costume) {
		if (!Objects.equals(costume, player.getAttached(RingAttachments.COSTUME))) {
			player.setAttached(RingAttachments.COSTUME, costume);
		}
	}
}
