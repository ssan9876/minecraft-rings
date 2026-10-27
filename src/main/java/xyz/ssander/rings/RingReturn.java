package xyz.ssander.rings;

import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rings find their way to a wearer. One left lying around flies back to whoever dropped it, and one that falls into
 * the void comes straight back. When its wearer dies, it goes looking for a new one: the nearest player without a
 * ring. With nobody to choose, it stays with its wearer through death.
 */
public final class RingReturn {
	/** A dropped ring lies this many ticks before flying back (so handing it over means them picking it up first). */
	private static final int RETURN_DELAY = 10 * 20;
	/** How far a ring flies to get back to its wearer, and how far it looks for a new one. */
	private static final double RETURN_RANGE = 128.0;
	private static final double CHOOSE_RANGE = 64.0;
	/** Top speed in blocks per tick. */
	private static final double SPEED = 1.2;

	/** The player a flying ring is heading for. Kept with the item, so it keeps going after a reload. */
	public static final AttachmentType<UUID> HOMING = AttachmentRegistry.create(Rings.id("homing"),
			builder -> builder.persistent(UUIDUtil.CODEC));

	private RingReturn() {
	}

	static void init() {
		// Loads the class so the attachment type registers during mod init
	}

	/**
	 * The ring of a dying wearer picks who it goes to: the nearest other player near by with an empty ring slot (not
	 * whoever killed them), or null to stay with the wearer.
	 */
	public static @Nullable ServerPlayer chooseHeir(ServerPlayer wearer) {
		Entity killer = wearer.getKillCredit();
		ServerPlayer heir = null;
		for (ServerPlayer other : wearer.level().players()) {
			if (other == wearer || other == killer || other.isSpectator() || !other.isAlive() || !RingAttachments.getRing(other).isEmpty()
					|| other.distanceTo(wearer) > CHOOSE_RANGE) {
				continue;
			}
			if (heir == null || other.distanceTo(wearer) < heir.distanceTo(wearer)) {
				heir = other;
			}
		}
		return heir;
	}

	/** Sends the ring of a dying wearer flying off to the new wearer it chose. */
	public static void sendToHeir(ServerPlayer wearer, ItemStack ring, ServerPlayer heir) {
		ServerLevel level = wearer.level();
		// It's theirs now, to call as their own
		RingCall.bind(ring, heir, true);
		ItemEntity item = new ItemEntity(level, wearer.getX(), wearer.getEyeY(), wearer.getZ(), ring);
		item.setAttached(HOMING, heir.getUUID());
		item.setTarget(heir.getUUID());
		level.addFreshEntity(item);
		wearer.sendSystemMessage(Component.translatable("message.rings.ring_left", ring.getHoverName()));
		heir.sendSystemMessage(Component.translatable("message.rings.ring_chose", ring.getHoverName()));
	}

	/** Flies a ring lying in the world to its wearer if it should, in place of its normal tick. Returns whether it did. */
	public static boolean tick(ItemEntity item, ServerLevel level) {
		Player target = target(item, level);
		if (target == null) {
			return false;
		}

		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).subtract(item.position());
		double distance = to.length();
		if (distance < 1.2) {
			deliver(item, level, target);
			return true;
		}
		Vec3 velocity = to.scale(Math.min(SPEED, 0.25 + distance * 0.06) / distance);
		item.setNoGravity(true);
		item.setDeltaMovement(velocity);
		item.setPos(item.position().add(velocity));
		item.needsSync = true;
		if (item.getItem().getItem() instanceof RingItem ring) {
			level.sendParticles(ring.color().dust(0.3F, 0.8F), item.getX(), item.getY() + 0.2, item.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
		return true;
	}

	/** Who a ring lying in the world should fly to now, if anyone. */
	private static @Nullable Player target(ItemEntity item, ServerLevel level) {
		UUID homing = item.getAttached(HOMING);
		if (homing != null) {
			return level.getPlayerByUUID(homing);
		}
		// Back to whoever dropped it, once it's lain a while or if it's fallen out of the world
		if ((item.getAge() > RETURN_DELAY || item.getY() < level.getMinY()) && item.getOwner() instanceof Player owner
				&& owner.isAlive() && owner.level() == level && owner.distanceTo(item) < RETURN_RANGE) {
			item.setAttached(HOMING, owner.getUUID());
			return owner;
		}
		return null;
	}

	/**
	 * A player touched a ring lying in the world. If it was flying to them, it goes straight on their finger (or in
	 * their inventory) rather than being picked up as usual. Returns whether it did.
	 */
	public static boolean touch(ItemEntity item, Player player) {
		if (!(item.level() instanceof ServerLevel level) || !player.getUUID().equals(item.getAttached(HOMING))) {
			return false;
		}
		deliver(item, level, player);
		return item.isRemoved();
	}

	/** Puts the ring on the player's finger, or in their inventory if they already wear one. */
	private static void deliver(ItemEntity item, ServerLevel level, Player player) {
		ItemStack ring = item.getItem().copy();
		if (RingAttachments.getRing(player).isEmpty()) {
			RingAttachments.setRing(player, ring);
		} else if (!player.getInventory().add(ring)) {
			// Nowhere to put it: it stays where it is for them to pick up
			item.removeAttached(HOMING);
			item.setNoGravity(false);
			return;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
		if (ring.getItem() instanceof RingItem ringItem) {
			level.sendParticles(ringItem.color().dust(0.5F, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.4, 0.3, 0.0);
		}
		player.sendOverlayMessage(Component.translatable("message.rings.ring_returned", ring.getHoverName()));
		item.discard();
	}
}
