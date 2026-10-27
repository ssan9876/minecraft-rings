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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
		/** A staircase of hard light climbing ahead of the wearer, 3 wide and 12 steps up. */
		STAIRS(30, "stairs"),
		/** A dome of hard light over the wearer, 4 blocks out. */
		DOME(40, "dome"),
		/** A box of hard light around whatever the wearer looks at. */
		CAGE(45, "cage"),
		/** A chain of light that yanks whatever the wearer looks at toward them. */
		GRAB(25, "grab"),
		/** A giant fist thrown where the wearer looks. */
		FIST(20, "fist"),
		/** A giant hammer brought down where the wearer looks, with a shockwave all round. */
		HAMMER(30, "hammer"),
		/** A sword, as strong as diamond. */
		SWORD(45, "sword"),
		/** A shield, for the off hand. */
		SHIELD(40, "shield"),
		/** An axe, as fast as netherite. */
		AXE(45, "axe"),
		/** A pickaxe, as fast as netherite. */
		PICKAXE(45, "pickaxe"),
		/** A shovel, as fast as netherite. */
		SHOVEL(45, "shovel"),
		/** A wolf of light that fights for its maker. */
		WOLF(60, "wolf"),
		/** A golem of light, slow and mighty, that fights for its maker. */
		GOLEM(90, "golem"),
		/** A jet of light, with its maker at the controls and room for three more. */
		JET(60, "jet");

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
	private static final int STAIRS_LIFETIME = 45 * 20;
	private static final int DOME_LIFETIME = 20 * 20;
	private static final double DOME_RADIUS = 4.0;
	/** How far the grab and the hammer reach, in blocks. */
	private static final double REACH = 32.0;
	/** Construct tools last at most this long, and only while the ring stays on. */
	private static final int TOOL_LIFETIME = 5 * 60 * 20;
	/** How far away the cage can reach, and the biggest thing it fits around, in blocks. */
	private static final double CAGE_RANGE = 24.0;
	private static final double CAGE_MAX_SIZE = 4.0;
	/** How long construct creatures last, and how many a wearer can have at once. */
	private static final int WOLF_LIFETIME = 60 * 20;
	private static final int GOLEM_LIFETIME = 45 * 20;
	private static final int MAX_BEASTS = 3;
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
		int cost = ColorPowers.constructCost(construct, ring.color());
		if (RingItem.charge(stack) < cost) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_no_power", stack.getHoverName()));
			return;
		}

		RingColor color = ring.color();
		boolean made = switch (construct) {
			case WALL -> wall(level, player, color);
			case BRIDGE -> bridge(level, player, color);
			case STAIRS -> stairs(level, player, color);
			case DOME -> dome(level, player, color);
			case CAGE -> cage(level, player, color);
			case GRAB -> grab(level, player, color);
			case FIST -> level.addFreshEntity(ConstructFist.throwFrom(player, color));
			case HAMMER -> level.addFreshEntity(ConstructFist.hammerOver(player, hammerTarget(player), color));
			case SWORD -> tool(player, Rings.CONSTRUCT_SWORD, color, now);
			case SHIELD -> tool(player, Rings.CONSTRUCT_SHIELD, color, now);
			case AXE -> tool(player, Rings.CONSTRUCT_AXE, color, now);
			case PICKAXE -> tool(player, Rings.CONSTRUCT_PICKAXE, color, now);
			case SHOVEL -> tool(player, Rings.CONSTRUCT_SHOVEL, color, now);
			case WOLF -> beast(level, player, Rings.CONSTRUCT_WOLF, color, WOLF_LIFETIME);
			case GOLEM -> beast(level, player, Rings.CONSTRUCT_GOLEM, color, GOLEM_LIFETIME);
			case JET -> jet(level, player, color);
		};
		if (!made) {
			return;
		}

		LAST_MADE.put(player, now);
		RingItem.addCharge(stack, -cost);
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

	private static boolean stairs(ServerLevel level, ServerPlayer player, RingColor color) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.blockPosition().relative(facing);
		int placed = 0;
		for (int step = 0; step < 12; step++) {
			for (int across = -1; across <= 1; across++) {
				if (HardLightBlock.place(level, start.relative(facing, step).above(step).relative(side, across), color, STAIRS_LIFETIME)) {
					placed++;
				}
			}
		}
		return placed > 0 || failed(player);
	}

	private static boolean dome(ServerLevel level, ServerPlayer player, RingColor color) {
		// A one-block shell, from the ground up, centred on the wearer's feet
		BlockPos centre = player.blockPosition();
		int reach = Mth.ceil(DOME_RADIUS);
		int placed = 0;
		for (int x = -reach; x <= reach; x++) {
			for (int y = 0; y <= reach; y++) {
				for (int z = -reach; z <= reach; z++) {
					double distance = Math.sqrt(x * x + y * y + z * z);
					if (distance > DOME_RADIUS - 0.5 && distance <= DOME_RADIUS + 0.5
							&& HardLightBlock.place(level, centre.offset(x, y, z), color, DOME_LIFETIME)) {
						placed++;
					}
				}
			}
		}
		return placed > 0 || failed(player);
	}

	private static boolean grab(ServerLevel level, ServerPlayer player, RingColor color) {
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		if (!(hit.entity() instanceof LivingEntity target) || player.distanceTo(target) > REACH) {
			player.sendOverlayMessage(Component.translatable("message.rings.grab_no_target"));
			return false;
		}

		// A chain of light from the ring fist to the target, then a yank that lands it just in front of the wearer
		Vec3 from = player.getEyePosition().subtract(0.0, 0.4, 0.0);
		Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		for (int i = 0; i <= 24; i++) {
			Vec3 link = from.lerp(to, i / 24.0);
			level.sendParticles(color.dust(i % 2 == 0 ? 0.6F : 0.0F, 0.9F), link.x, link.y, link.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		Vec3 pull = player.position().subtract(target.position());
		double distance = pull.length();
		target.setDeltaMovement(pull.normalize().scale(Math.min(2.4, 0.5 + distance * 0.16)).add(0.0, 0.35 + distance * 0.015, 0.0));
		target.needsSync = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	/** Where the hammer comes down: the block or creature looked at, or the ground at the end of its reach. */
	private static Vec3 hammerTarget(ServerPlayer player) {
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		Vec3 end = player.getEyePosition().distanceTo(hit.end()) > REACH
				? player.getEyePosition().add(player.getViewVector(1.0F).scale(REACH))
				: hit.end();
		return hit.entity() != null ? hit.entity().position() : end;
	}

	private static boolean cage(ServerLevel level, ServerPlayer player, RingColor color) {
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		Entity target = hit.entity();
		if (!(target instanceof LivingEntity) || player.distanceTo(target) > CAGE_RANGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.cage_no_target"));
			return false;
		}
		if (!boxIn(level, target, color, CAGE_LIFETIME)) {
			player.sendOverlayMessage(Component.translatable("message.rings.cage_too_big", target.getDisplayName()));
			return false;
		}
		return true;
	}

	/**
	 * Closes a hollow box of hard light round the target, one block out all round, floor and roof included, and
	 * stops it moving. Returns false, making nothing, if it's too big.
	 */
	static boolean boxIn(ServerLevel level, Entity target, RingColor color, int lifetime) {
		AABB box = target.getBoundingBox();
		if (box.getXsize() > CAGE_MAX_SIZE || box.getYsize() > CAGE_MAX_SIZE || box.getZsize() > CAGE_MAX_SIZE) {
			return false;
		}
		int minX = Mth.floor(box.minX) - 1, minY = Mth.floor(box.minY) - 1, minZ = Mth.floor(box.minZ) - 1;
		int maxX = Mth.floor(box.maxX) + 1, maxY = Mth.ceil(box.maxY), maxZ = Mth.floor(box.maxZ) + 1;
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ) {
						HardLightBlock.place(level, new BlockPos(x, y, z), color, lifetime);
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
		// The shield goes on the off hand, where it blocks while the sword swings
		if (item == Rings.CONSTRUCT_SHIELD && player.getOffhandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.OFF_HAND, tool);
			return true;
		}
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

	/** A creature of light, a couple of blocks ahead of the wearer, that fights for them. */
	private static boolean beast(ServerLevel level, ServerPlayer player, EntityType<ConstructBeast> type, RingColor color, int lifetime) {
		int beasts = level.getEntities(EntityTypeTest.forClass(ConstructBeast.class),
				beast -> Thralls.of(beast) != null && Thralls.of(beast).owner().filter(player.getUUID()::equals).isPresent()).size();
		if (beasts >= MAX_BEASTS) {
			player.sendOverlayMessage(Component.translatable("message.rings.too_many_thralls"));
			return false;
		}
		// Ahead of the wearer, side by side with any already made
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 ahead = player.position().add(-Mth.sin(yaw) * 2.5, 0.0, Mth.cos(yaw) * 2.5).add(right.scale((beasts - 1) * 1.8));
		return Thralls.raise(level, type, ahead, player.getUUID(), color, lifetime) != null;
	}

	/** A jet of light, with the wearer climbing straight into the pilot's seat. */
	private static boolean jet(ServerLevel level, ServerPlayer player, RingColor color) {
		if (player.isPassenger()) {
			player.sendOverlayMessage(Component.translatable("message.rings.construct_blocked"));
			return false;
		}
		// One jet at a time: making another lets the old one go
		for (ConstructJet old : level.getEntities(EntityTypeTest.forClass(ConstructJet.class), jet -> jet.owner() == player)) {
			old.fade(level);
		}
		ConstructJet jet = ConstructJet.madeBy(player, color);
		level.addFreshEntity(jet);
		if (!player.startRiding(jet, true, true)) {
			jet.discard();
			return false;
		}
		return true;
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
