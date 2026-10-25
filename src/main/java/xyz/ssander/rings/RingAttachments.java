package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Per-player data. A missing attachment means "empty" or "off". */
public final class RingAttachments {
	/** The ring in the ring slot. Kept on death only with keep_inventory, since it drops otherwise. */
	public static final AttachmentType<ItemStack> RING = AttachmentRegistry.create(Rings.id("ring"),
			builder -> builder.persistent(ItemStack.OPTIONAL_CODEC).copyOnDeath());

	/** Whether the wearer has switched the ring on. Dying switches it off. Synced to the wearer for the power meter. */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(Rings.id("active"),
			builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Set while flight comes from the ring, so it's only taken away from players we gave it to. */
	public static final AttachmentType<Boolean> FLIGHT_GRANTED = AttachmentRegistry.create(Rings.id("flight_granted"),
			builder -> builder.persistent(Codec.BOOL));

	/** The costume being worn. Synced to every client so everyone sees it. */
	public static final AttachmentType<Identifier> COSTUME = AttachmentRegistry.create(Rings.id("costume"),
			builder -> builder.syncWith(Identifier.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Set while the wearer is flying on the ring's power. Synced to every client for the flying pose and trail. */
	public static final AttachmentType<Boolean> FLYING = AttachmentRegistry.create(Rings.id("flying"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Set while the wearer is firing the ring's beam. Synced to every client so everyone sees the beam. */
	public static final AttachmentType<Boolean> FIRING = AttachmentRegistry.create(Rings.id("firing"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** The colour of the ring in the ring slot, on or off. Synced to every client so everyone sees it on the wearer's hand. */
	public static final AttachmentType<RingColor> WORN_RING = AttachmentRegistry.create(Rings.id("worn_ring"),
			builder -> builder.syncWith(RingColor.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Set while the wearer's force field is up. Synced to every client so everyone sees it. */
	public static final AttachmentType<Boolean> SHIELD = AttachmentRegistry.create(Rings.id("shield"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Where on the Earth a player left for space, so they come back down there. */
	public static final AttachmentType<BlockPos> EARTH_RETURN = AttachmentRegistry.create(Rings.id("earth_return"),
			builder -> builder.persistent(BlockPos.CODEC).copyOnDeath());

	private RingAttachments() {
	}

	static void init() {
		// Loads the class so the attachment types register during mod init
	}

	public static ItemStack getRing(Player player) {
		return player.getAttachedOrElse(RING, ItemStack.EMPTY);
	}

	public static void setRing(Player player, ItemStack stack) {
		player.setAttached(RING, stack.isEmpty() ? null : stack);
	}

	public static boolean isActive(Player player) {
		return player.getAttachedOrElse(ACTIVE, false);
	}

	/** The colour of the worn ring while it's switched on, otherwise null. */
	public static @Nullable RingColor activeColor(Player player) {
		return isActive(player) && getRing(player).getItem() instanceof RingItem ring ? ring.color() : null;
	}
}
