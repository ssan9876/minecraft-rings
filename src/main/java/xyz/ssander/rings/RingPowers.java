package xyz.ssander.rings;

import java.util.Objects;

import net.minecraft.core.particles.DustParticleOptions;
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
import org.jspecify.annotations.Nullable;

/** Server-side ring logic: switching it on and off, and keeping its powers in line every tick. */
public final class RingPowers {
	/** Slow falling after flight ends in mid-air, so survival players don't fall to their death. */
	private static final int SAFE_LANDING_TICKS = 100;
	/** The ring's green energy, burst out on activation. */
	private static final DustParticleOptions RING_GLOW = new DustParticleOptions(0x3CFF5A, 1.5F);
	private static final DustParticleOptions RING_GLOW_FADING = new DustParticleOptions(0x1E8C32, 1.0F);

	private RingPowers() {
	}

	public static void toggle(ServerPlayer player) {
		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem)) {
			player.sendOverlayMessage(Component.translatable("message.rings.no_ring"));
			return;
		}

		boolean active = !RingAttachments.isActive(player);
		player.setAttached(RingAttachments.ACTIVE, active ? true : null);
		tick(player);

		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				active ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, active ? 1.4F : 1.2F);
		level.sendParticles(active ? RING_GLOW : RING_GLOW_FADING,
				player.getX(), player.getY() + 1.0, player.getZ(), active ? 60 : 25, 0.5, 0.9, 0.5, 0.0);
		player.sendOverlayMessage(Component.translatable(active ? "message.rings.activated" : "message.rings.deactivated", ring.getHoverName()));
	}

	/** Grants or removes powers to match the worn ring. Runs every server tick. */
	public static void tick(ServerPlayer player) {
		RingItem ring = RingAttachments.getRing(player).getItem() instanceof RingItem r ? r : null;
		if (ring == null && RingAttachments.isActive(player)) {
			// Taking the ring off switches it off
			player.removeAttached(RingAttachments.ACTIVE);
		}

		RingItem active = RingAttachments.isActive(player) ? ring : null;
		updateFlight(player, active != null && active.grantsFlight());
		updateCostume(player, active != null ? active.costume() : null);

		boolean ringFlying = active != null && active.grantsFlight() && player.getAbilities().flying;
		if (ringFlying != player.getAttachedOrElse(RingAttachments.FLYING, false)) {
			player.setAttached(RingAttachments.FLYING, ringFlying ? true : null);
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
