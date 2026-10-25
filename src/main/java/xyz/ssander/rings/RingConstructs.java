package xyz.ssander.rings;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;

import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * Constructs: things the wearer makes out of the ring's light by will alone, picked from the construct wheel. Each
 * costs some of the ring's charge and is made in its colour.
 */
public final class RingConstructs {
	/** The constructs, in the order the wheel shows them. */
	public enum Construct {
		/** A wall of hard light in front of the wearer, 5 wide and 4 high. */
		WALL(30, "wall"),
		/** A bridge of hard light from the wearer's feet, 3 wide and 16 long. */
		BRIDGE(30, "bridge"),
		/** A box of hard light around whatever the wearer looks at. */
		CAGE(45, "cage"),
		/** A giant fist thrown where the wearer looks. */
		FIST(20, "fist"),
		/** A sword, as strong as diamond. */
		SWORD(45, "sword"),
		/** A pickaxe, as fast as netherite. */
		PICKAXE(45, "pickaxe");

		private final int cost;
		private final String name;

		Construct(int cost, String name) {
			this.cost = cost;
			this.name = name;
		}

		public int cost() {
			return this.cost;
		}

		public String translationKey() {
			return "construct.rings." + this.name;
		}
	}

	private static final int WALL_LIFETIME = 30 * 20;
	private static final int BRIDGE_LIFETIME = 60 * 20;
	private static final int CAGE_LIFETIME = 15 * 20;
	/** Construct tools last at most this long, and only while the ring stays on. */
	private static final int TOOL_LIFETIME = 5 * 60 * 20;
	/** How far away the cage can reach, and the biggest thing it fits around, in blocks. */
	private static final double CAGE_RANGE = 24.0;
	private static final double CAGE_MAX_SIZE = 4.0;
	/** Ticks between constructs. */
	private static final int COOLDOWN = 10;

	private static final Map<ServerPlayer, Long> LAST_MADE = new WeakHashMap<>();

	private RingConstructs() {
	}

	/** The wearer picked a construct from the wheel. */
	public static void make(ServerPlayer player, Construct construct) {
		ItemStack stack = RingAttachments.getRing(player);
		if (!(stack.getItem() instanceof RingItem ring) || !RingAttachments.isActive(player)) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_inactive"));
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - LAST_MADE.getOrDefault(player, Long.MIN_VALUE / 2) < COOLDOWN) {
			return;
		}
		if (RingItem.charge(stack) < construct.cost()) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", stack.getHoverName()));
			return;
		}

		RingColor color = ring.color();
		boolean made = switch (construct) {
			case WALL -> wall(level, player, color);
			case BRIDGE -> bridge(level, player, color);
			case CAGE -> cage(level, player, color);
			case FIST -> level.addFreshEntity(ConstructFist.throwFrom(player, color));
			case SWORD -> tool(player, Rings.CONSTRUCT_SWORD, color, now);
			case PICKAXE -> tool(player, Rings.CONSTRUCT_PICKAXE, color, now);
		};
		if (!made) {
			return;
		}

		LAST_MADE.put(player, now);
		RingItem.addCharge(stack, -construct.cost());
		RingPowers.playAnimation(player, RingAnimationPayload.Kind.CONSTRUCT, color);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.sendParticles(color.dust(0.5F, 1.0F), player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.0);
	}

	private static boolean wall(ServerLevel level, ServerPlayer player, RingColor color) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos centre = player.blockPosition().relative(facing, 3);
		int placed = 0;
		for (int across = -2; across <= 2; across++) {
			for (int up = 0; up < 4; up++) {
				if (HardLightBlock.place(level, centre.relative(side, across).above(up), color, WALL_LIFETIME)) {
					placed++;
				}
			}
		}
		return placed > 0 || failed(player);
	}

	private static boolean bridge(ServerLevel level, ServerPlayer player, RingColor color) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.blockPosition().below();
		int placed = 0;
		for (int along = 0; along < 16; along++) {
			for (int across = -1; across <= 1; across++) {
				if (HardLightBlock.place(level, start.relative(facing, along).relative(side, across), color, BRIDGE_LIFETIME)) {
					placed++;
				}
			}
		}
		return placed > 0 || failed(player);
	}

	private static boolean cage(ServerLevel level, ServerPlayer player, RingColor color) {
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		Entity target = hit.entity();
		if (!(target instanceof LivingEntity) || player.distanceTo(target) > CAGE_RANGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.cage_no_target"));
			return false;
		}
		AABB box = target.getBoundingBox();
		if (box.getXsize() > CAGE_MAX_SIZE || box.getYsize() > CAGE_MAX_SIZE || box.getZsize() > CAGE_MAX_SIZE) {
			player.sendOverlayMessage(Component.translatable("message.rings.cage_too_big", target.getDisplayName()));
			return false;
		}

		// A hollow box one block out from the target all round, floor and roof included
		int minX = Mth.floor(box.minX) - 1, minY = Mth.floor(box.minY) - 1, minZ = Mth.floor(box.minZ) - 1;
		int maxX = Mth.floor(box.maxX) + 1, maxY = Mth.ceil(box.maxY), maxZ = Mth.floor(box.maxZ) + 1;
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ) {
						HardLightBlock.place(level, new BlockPos(x, y, z), color, CAGE_LIFETIME);
					}
				}
			}
		}
		target.setDeltaMovement(0.0, 0.0, 0.0);
		return true;
	}

	private static boolean tool(ServerPlayer player, Item item, RingColor color, long now) {
		ItemStack tool = new ItemStack(item);
		tool.set(DataComponents.DYED_COLOR, new DyedItemColor(color.energy()));
		tool.set(RingComponents.CONSTRUCT_EXPIRES, now + TOOL_LIFETIME);
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, tool);
			return true;
		}
		if (player.getInventory().add(tool)) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.rings.construct_no_room"));
		return false;
	}

	private static boolean failed(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.rings.construct_blocked"));
		return false;
	}

	/** The ring colour a construct tool was made in. */
	public static RingColor colorOf(ItemStack stack) {
		int rgb = DyedItemColor.getOrDefault(stack, RingColor.GREEN.energy());
		for (RingColor color : RingColor.values()) {
			if ((color.energy() & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
				return color;
			}
		}
		return RingColor.GREEN;
	}
}
