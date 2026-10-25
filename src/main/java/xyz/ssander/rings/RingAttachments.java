package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Per-player data. A missing attachment means "empty" or "off". */
public final class RingAttachments {
	/** The ring in the ring slot. Kept on death only with keep_inventory, since it drops otherwise. */
	public static final AttachmentType<ItemStack> RING = AttachmentRegistry.create(Rings.id("ring"),
			builder -> builder.persistent(ItemStack.OPTIONAL_CODEC).copyOnDeath());

	/** Whether the wearer has switched the ring on. Dying switches it off. */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(Rings.id("active"),
			builder -> builder.persistent(Codec.BOOL));

	/** Set while flight comes from the ring, so it's only taken away from players we gave it to. */
	public static final AttachmentType<Boolean> FLIGHT_GRANTED = AttachmentRegistry.create(Rings.id("flight_granted"),
			builder -> builder.persistent(Codec.BOOL));

	/** The costume being worn. Synced to every client so everyone sees it. */
	public static final AttachmentType<Identifier> COSTUME = AttachmentRegistry.create(Rings.id("costume"),
			builder -> builder.syncWith(Identifier.STREAM_CODEC, AttachmentSyncPredicate.all()));

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
}
