package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rings are bound to a player, the first to wear one (or the heir it chooses), and come when that player calls them:
 * out of chests and every other kind of container, shulker boxes and bundles inside those, item frames, armor stands
 * and the hands of mobs, other players' inventories and ender chests, wherever it is in the world. It bursts out and
 * flies back to them through whatever's in the way; from too far off or another world, it streaks in out of the sky.
 * The server remembers where each ring was last seen, so one left somewhere nobody is (its chunk not loaded) is found
 * by waking that spot up for a few seconds and looking there.
 */
public final class RingCall {
	/** Further than this (or in another world), a called ring doesn't fly the whole way but streaks in from near by. */
	private static final double FLY_RANGE = 96.0;
	/** Where a ring from far off appears: this far away from its wearer and this high above them. */
	private static final double STREAK_DISTANCE = 24.0;
	private static final double STREAK_HEIGHT = 14.0;
	/** How long a call keeps looking where a ring was last seen, while that spot wakes up, and how often. */
	private static final int SEARCH_TICKS = 100;
	private static final int SEARCH_EVERY = 5;
	/** How often a ring's whereabouts are noted (it only needs to be near enough to find it again). */
	private static final int SEEN_EVERY = 20;

	/** Where each bound ring was last seen, by its owner and colour ({@link #key}). Kept on the Overworld. */
	private static final AttachmentType<Map<String, GlobalPos>> LAST_SEEN = AttachmentRegistry.create(Rings.id("rings_last_seen"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC)));

	/** Calls still looking where their rings were last seen: by caller, the spots and when to give up. */
	private static final Map<UUID, Search> SEARCHES = new HashMap<>();

	private record Search(List<GlobalPos> spots, long until) {
	}

	private RingCall() {
	}

	static void init() {
		// Loads the class so the attachment type registers during mod init
	}

	/** Binds an unbound ring to the player. */
	public static void bind(ItemStack ring, Player player) {
		bind(ring, player, false);
	}

	/** Binds a ring to the player; one already bound to someone else only if {@code rebind}. */
	public static void bind(ItemStack ring, Player player, boolean rebind) {
		RingComponents.Bond bond = ring.get(RingComponents.BOND);
		if (bond == null || rebind && !bond.owner().equals(player.getUUID())) {
			ring.set(RingComponents.BOND, new RingComponents.Bond(player.getUUID(), player.getGameProfile().name()));
		}
	}

	private static boolean boundTo(ItemStack stack, UUID owner) {
		RingComponents.Bond bond = stack.get(RingComponents.BOND);
		return stack.getItem() instanceof RingItem && bond != null && bond.owner().equals(owner);
	}

	private static String key(UUID owner, RingItem ring) {
		return owner + "/" + ring.color().id();
	}

	/** Notes where a ring is, every so often: one lying in the world, or carried by a player. */
	public static void seen(ItemStack stack, ServerLevel level, Vec3 pos, int tickCount) {
		RingComponents.Bond bond = stack.get(RingComponents.BOND);
		if (tickCount % SEEN_EVERY != 0 || bond == null || !(stack.getItem() instanceof RingItem ring)) {
			return;
		}
		ServerLevel overworld = level.getServer().overworld();
		GlobalPos here = GlobalPos.of(level.dimension(), BlockPos.containing(pos));
		Map<String, GlobalPos> seen = overworld.getAttachedOrElse(LAST_SEEN, Map.of());
		String key = key(bond.owner(), ring);
		if (!here.equals(seen.get(key))) {
			Map<String, GlobalPos> updated = new HashMap<>(seen);
			updated.put(key, here);
			overworld.setAttached(LAST_SEEN, updated);
		}
	}

	/** Runs every server tick: notes where players carry rings, and carries on calls still looking. */
	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % SEEN_EVERY != 0) {
				continue;
			}
			// Whatever they carry was last seen with them: put down in a chest, it's near where they stood
			for (ItemStack stack : carriedBy(player)) {
				seen(stack, player.level(), player.position(), 0);
			}
		}

		if (server.getTickCount() % SEARCH_EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Search>> it = SEARCHES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Search> entry = it.next();
			ServerPlayer caller = server.getPlayerList().getPlayer(entry.getKey());
			if (caller == null) {
				it.remove();
				continue;
			}
			int found = 0;
			for (GlobalPos spot : entry.getValue().spots()) {
				ServerLevel level = server.getLevel(spot.dimension());
				if (level != null) {
					found += searchAround(level, spot.pos(), caller);
				}
			}
			if (found > 0) {
				it.remove();
			} else if (server.overworld().getGameTime() >= entry.getValue().until()) {
				it.remove();
				caller.sendOverlayMessage(Component.translatable("message.rings.call_lost"));
			}
		}
	}

	/** The player called their rings: every ring bound to them that they haven't got on them comes. */
	public static void call(ServerPlayer caller) {
		MinecraftServer server = caller.level().getServer();
		int found = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			found += searchPlayer(player, caller);
		}
		for (ServerLevel level : server.getAllLevels()) {
			// A copy, since calling a ring takes it out of the world and sends it off as a new one
			List<Entity> entities = new ArrayList<>();
			level.getAllEntities().forEach(entities::add);
			for (Entity entity : entities) {
				found += searchEntity(entity, caller);
			}
			int[] inChunks = {0};
			level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> inChunks[0] += searchChunk(level, chunk, caller));
			found += inChunks[0];
		}
		if (found > 0) {
			return;
		}

		// Nowhere awake: wake up where they were last seen, and look there while it loads. Rings they have on them
		// were last seen with them, and aren't missing.
		List<String> carried = new ArrayList<>();
		for (ItemStack stack : carriedBy(caller)) {
			if (boundTo(stack, caller.getUUID()) && stack.getItem() instanceof RingItem ring) {
				carried.add(key(caller.getUUID(), ring));
			}
		}
		String prefix = caller.getUUID() + "/";
		List<GlobalPos> spots = new ArrayList<>();
		server.overworld().getAttachedOrElse(LAST_SEEN, Map.<String, GlobalPos>of()).forEach((key, spot) -> {
			if (key.startsWith(prefix) && !carried.contains(key)) {
				spots.add(spot);
			}
		});
		if (spots.isEmpty()) {
			caller.sendOverlayMessage(Component.translatable(carried.isEmpty() ? "message.rings.call_none" : "message.rings.call_all_here"));
			return;
		}
		for (GlobalPos spot : spots) {
			ServerLevel level = server.getLevel(spot.dimension());
			if (level != null) {
				// A portal ticket wears off on its own, a while after the search is over
				level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(spot.pos()), 2);
			}
		}
		SEARCHES.put(caller.getUUID(), new Search(spots, server.overworld().getGameTime() + SEARCH_TICKS));
		caller.sendOverlayMessage(Component.translatable("message.rings.call_searching"));
	}

	/** What a player has on them: the ring they wear, their inventory, and what they hold in an open screen. */
	private static List<ItemStack> carriedBy(ServerPlayer player) {
		List<ItemStack> stacks = new ArrayList<>();
		stacks.add(RingAttachments.getRing(player));
		player.getInventory().forEach(stacks::add);
		stacks.add(player.containerMenu.getCarried());
		return stacks;
	}

	private static int searchPlayer(ServerPlayer player, ServerPlayer caller) {
		int found = 0;
		// What's on the caller themselves has already come; their ender chest is somewhere else
		if (player != caller) {
			ItemStack worn = RingAttachments.getRing(player);
			if (boundTo(worn, caller.getUUID())) {
				RingAttachments.setRing(player, ItemStack.EMPTY);
				player.removeAttached(RingAttachments.ACTIVE);
				found += calledAway(worn, player, caller);
			}
			found += searchContainer(player.getInventory(), player.level(), player.getEyePosition(), caller, player);
			ItemStack carried = player.containerMenu.getCarried();
			if (boundTo(carried, caller.getUUID())) {
				player.containerMenu.setCarried(ItemStack.EMPTY);
				found += calledAway(carried, player, caller);
			}
		}
		found += searchContainer(player.getEnderChestInventory(), player.level(), player.getEyePosition(), caller, player);
		return found;
	}

	private static int calledAway(ItemStack ring, ServerPlayer holder, ServerPlayer caller) {
		holder.sendSystemMessage(Component.translatable("message.rings.call_away", ring.getHoverName(), caller.getDisplayName()));
		comeTo(ring, holder.level(), holder.getEyePosition(), caller);
		return 1;
	}

	private static int searchEntity(Entity entity, ServerPlayer caller) {
		if (!(entity.level() instanceof ServerLevel level) || entity instanceof Player) {
			return 0;
		}
		UUID owner = caller.getUUID();
		if (entity instanceof ItemEntity item) {
			if (!boundTo(item.getItem(), owner)) {
				return 0;
			}
			if (owner.equals(item.getAttached(RingReturn.HOMING))) {
				// Already on its way
				return 1;
			}
			ItemStack ring = item.getItem().copy();
			item.discard();
			comeTo(ring, level, item.position(), caller);
			return 1;
		}
		Vec3 at = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
		int found = 0;
		if (entity instanceof ItemFrame frame) {
			ItemStack held = frame.getItem();
			if (boundTo(held, owner)) {
				frame.setItem(ItemStack.EMPTY);
				found += comeTo(held, level, at, caller);
			}
		}
		if (entity instanceof Container container) {
			found += searchContainer(container, level, at, caller, null);
		}
		if (entity instanceof InventoryCarrier carrier) {
			found += searchContainer(carrier.getInventory(), level, at, caller, null);
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack held = living.getItemBySlot(slot);
				if (boundTo(held, owner)) {
					living.setItemSlot(slot, ItemStack.EMPTY);
					found += comeTo(held, level, at, caller);
				} else {
					// Out of a bundle or shulker box they're holding
					ItemStack ring = taken(held, owner);
					if (!ring.isEmpty()) {
						found += comeTo(ring, level, at, caller);
					}
				}
			}
		}
		return found;
	}

	private static int searchChunk(ServerLevel level, LevelChunk chunk, ServerPlayer caller) {
		int found = 0;
		for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
			if (blockEntity instanceof Container container) {
				found += searchContainer(container, level, Vec3.atCenterOf(blockEntity.getBlockPos()), caller, null);
			}
		}
		return found;
	}

	/** Looks round a spot where a ring was last seen, once it's loaded. */
	private static int searchAround(ServerLevel level, BlockPos pos, ServerPlayer caller) {
		ChunkPos centre = ChunkPos.containing(pos);
		int found = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
				if (chunk != null) {
					found += searchChunk(level, chunk, caller);
				}
			}
		}
		AABB area = new AABB(centre.getMinBlockX() - 16, level.getMinY(), centre.getMinBlockZ() - 16,
				centre.getMaxBlockX() + 17, level.getMaxY() + 1, centre.getMaxBlockZ() + 17);
		for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
			found += searchEntity(entity, caller);
		}
		return found;
	}

	/** Takes every ring bound to the caller out of the container (and out of anything in it that holds items). */
	private static int searchContainer(Container container, ServerLevel level, Vec3 at, ServerPlayer caller, @Nullable ServerPlayer holder) {
		int found = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			ItemStack ring;
			if (boundTo(stack, caller.getUUID())) {
				container.setItem(slot, ItemStack.EMPTY);
				ring = stack;
			} else {
				ring = taken(stack, caller.getUUID());
				if (ring.isEmpty()) {
					continue;
				}
			}
			container.setChanged();
			found += holder != null ? calledAway(ring, holder, caller) : comeTo(ring, level, at, caller);
		}
		return found;
	}

	/**
	 * Takes a ring bound to the owner out of an item that holds others (a shulker box or bundle, and so on inside
	 * those). Returns the ring, or empty if there wasn't one.
	 */
	private static ItemStack taken(ItemStack holder, UUID owner) {
		ItemStack ring = takeFrom(holder, DataComponents.CONTAINER, stack -> boundTo(stack, owner), owner);
		return ring.isEmpty() ? takeFrom(holder, DataComponents.BUNDLE_CONTENTS, stack -> boundTo(stack, owner), owner) : ring;
	}

	private static <T extends ContainerComponent<T>> ItemStack takeFrom(ItemStack holder, DataComponentType<T> type, Predicate<ItemStack> isRing,
			UUID owner) {
		T contents = holder.get(type);
		if (contents == null) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
		for (int i = 0; i < items.size(); i++) {
			ItemStack inside = items.get(i);
			ItemStack ring;
			if (isRing.test(inside)) {
				ring = inside;
				items.set(i, ItemStack.EMPTY);
			} else {
				ring = taken(inside, owner);
				if (ring.isEmpty()) {
					continue;
				}
			}
			holder.set(type, contents.copyWithContents(items.stream()));
			return ring;
		}
		return ItemStack.EMPTY;
	}

	/** Sends a ring flying from where it was found to the caller. */
	private static int comeTo(ItemStack ring, ServerLevel from, Vec3 at, ServerPlayer caller) {
		RingColor color = ring.getItem() instanceof RingItem item ? item.color() : RingColor.GREEN;
		from.sendParticles(color.dust(0.5F, 1.2F), at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.05);
		from.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 2.0F);

		ServerLevel level = caller.level();
		Vec3 start = at;
		if (from != level || at.distanceTo(caller.position()) > FLY_RANGE || !level.isPositionEntityTicking(BlockPos.containing(at))) {
			// Too far to fly the whole way: it comes in out of the sky, from the way it was if that's this world
			Vec3 way = from == level ? at.subtract(caller.position()).multiply(1.0, 0.0, 1.0) : caller.getLookAngle().multiply(-1.0, 0.0, -1.0);
			way = way.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : way.normalize();
			start = caller.position().add(way.scale(STREAK_DISTANCE)).add(0.0, STREAK_HEIGHT, 0.0);
		}
		ItemEntity item = new ItemEntity(level, start.x, start.y, start.z, ring);
		item.setAttached(RingReturn.HOMING, caller.getUUID());
		item.setNoGravity(true);
		level.addFreshEntity(item);
		caller.sendOverlayMessage(Component.translatable("message.rings.call_coming", ring.getHoverName()));
		return 1;
	}
}
