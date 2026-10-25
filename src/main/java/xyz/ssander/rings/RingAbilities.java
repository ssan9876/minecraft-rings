package xyz.ssander.rings;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import xyz.ssander.rings.network.RingActionPayload;
import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * The ring's powers on keys of their own: the force field (round the wearer, or as a bubble carrying whatever they
 * look at) and the scan. Each needs the ring on.
 */
public final class RingAbilities {
	/** Charge used each second while the force field is up, and while carrying something in a bubble. */
	static final int SHIELD_DRAIN = 2;
	static final int CARRY_DRAIN = 2;
	/** Charge to blow a bubble round something, and how far and how big it can be. */
	private static final int BUBBLE_COST = 30;
	private static final double CARRY_RANGE = 16.0;
	private static final double CARRY_MAX_SIZE = 3.0;
	/** Charge for one scan. */
	public static final int SCAN_COST = 30;

	private RingAbilities() {
	}

	public static void use(ServerPlayer player, RingActionPayload.Action action) {
		switch (action) {
			case FORCE_FIELD -> forceField(player);
			case SCAN -> scan(player);
		}
	}

	/**
	 * Releases whatever the wearer carries in a bubble if anything; otherwise blows a bubble round what they look at;
	 * otherwise puts their own force field up or down.
	 */
	private static void forceField(ServerPlayer player) {
		RingColor color = RingAttachments.activeColor(player);
		if (color == null) {
			player.sendOverlayMessage(Component.translatable("message.rings.power_inactive"));
			return;
		}
		ServerLevel level = player.level();
		ItemStack ring = RingAttachments.getRing(player);

		List<ForceBubble> carried = ForceBubble.carriedBy(player);
		if (!carried.isEmpty()) {
			carried.forEach(ForceBubble::pop);
			player.sendOverlayMessage(Component.translatable("message.rings.bubble_released"));
			return;
		}

		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		if (hit.entity() instanceof LivingEntity target && player.distanceTo(target) <= CARRY_RANGE && !target.isPassenger()
				&& target.getBbWidth() <= CARRY_MAX_SIZE && target.getBbHeight() <= CARRY_MAX_SIZE) {
			if (RingItem.charge(ring) < BUBBLE_COST) {
				player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", ring.getHoverName()));
				return;
			}
			ForceBubble bubble = ForceBubble.around(player, target, color);
			level.addFreshEntity(bubble);
			if (!bubble.carry(target)) {
				bubble.discard();
				return;
			}
			RingItem.addCharge(ring, -BUBBLE_COST);
			RingPowers.playAnimation(player, RingAnimationPayload.Kind.CONSTRUCT, color);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.5F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.rings.bubble_carry", target.getDisplayName()));
			return;
		}

		boolean up = !player.getAttachedOrElse(RingAttachments.SHIELD, false);
		player.setAttached(RingAttachments.SHIELD, up ? true : null);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), up ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 0.8F, up ? 0.8F : 1.6F);
		player.sendOverlayMessage(Component.translatable(up ? "message.rings.shield_up" : "message.rings.shield_down"));
	}

	/** Sends out a scan: the wearer's client shows what it finds (see RingScan). */
	private static void scan(ServerPlayer player) {
		RingColor color = RingAttachments.activeColor(player);
		if (color == null) {
			player.sendOverlayMessage(Component.translatable("message.rings.power_inactive"));
			return;
		}
		ItemStack ring = RingAttachments.getRing(player);
		if (RingItem.charge(ring) < SCAN_COST) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", ring.getHoverName()));
			return;
		}
		RingItem.addCharge(ring, -SCAN_COST);
		RingPowers.playAnimation(player, RingAnimationPayload.Kind.SCAN, color);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
	}
}
