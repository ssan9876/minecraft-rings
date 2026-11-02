package xyz.ssander.rings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.OpenDialoguePayload;

/**
 * The Guardians' missions: ask a Guardian on Oa for work and they set a task, rewarded with ring experience and
 * sometimes an Oan relic. Bring them moonstone from the Moon, crystals from Mars or will shards from Oa's own rock;
 * hunt Manhunters or one of the great foes; scan Mars from far-apart places; carry a creature to the Moon in a bubble;
 * or find an explorer stranded on Mars and bring them home to the Earth. One at a time, and once a day a bounty pays
 * double. A creature hunted by a Corps comrade on the same hunt counts too.
 */
public final class Missions {
	public enum Kind {
		BRING,
		HUNT,
		/** Scan the planet from places far apart. */
		SCAN,
		/** Arrive at a place carrying something in a bubble. */
		CARRY,
		/** Find the stranded explorer, and arrive home with them in a bubble. */
		RESCUE
	}

	public enum Mission implements StringRepresentable {
		MOONSTONE(Kind.BRING, 16, 250, 0.3F, 1, () -> PlanetBlocks.MOONSTONE, null),
		MARS_CRYSTALS(Kind.BRING, 12, 250, 0.3F, 1, () -> PlanetBlocks.MARS_CRYSTAL.asItem(), null),
		WILL_SHARDS(Kind.BRING, 8, 150, 0.15F, 1, () -> PlanetBlocks.WILL_SHARD, null),
		MANHUNTERS(Kind.HUNT, 5, 300, 0.4F, 1, null, entity -> entity instanceof Manhunter),
		NEKRON(Kind.HUNT, 1, 500, 1.0F, 15, null, entity -> entity instanceof Nekron),
		PARALLAX(Kind.HUNT, 1, 500, 1.0F, 15, null, entity -> entity instanceof Parallax),
		DEVOURER(Kind.HUNT, 1, 700, 1.0F, 20, null, entity -> entity instanceof Devourer),
		SCAN_MARS(Kind.SCAN, 3, 250, 0.25F, 1, null, null),
		ESCORT_MOON(Kind.CARRY, 1, 300, 0.3F, 4, null, null),
		RESCUE_MARS(Kind.RESCUE, 1, 450, 0.6F, 8, null, null);

		public static final StringRepresentable.EnumCodec<Mission> CODEC = StringRepresentable.fromEnum(Mission::values);

		private final Kind kind;
		private final int count;
		private final int xp;
		private final float relicChance;
		private final int minLevel;
		private final @Nullable Supplier<Item> item;
		private final @Nullable Predicate<Entity> quarry;

		Mission(Kind kind, int count, int xp, float relicChance, int minLevel, @Nullable Supplier<Item> item, @Nullable Predicate<Entity> quarry) {
			this.kind = kind;
			this.count = count;
			this.xp = xp;
			this.relicChance = relicChance;
			this.minLevel = minLevel;
			this.item = item;
			this.quarry = quarry;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Kind kind() {
			return this.kind;
		}
	}

	/**
	 * The player's mission: how far into it they are (creatures hunted, places scanned), where it's about (the last
	 * scan, or the stranded explorer), and whether it's the day's bounty.
	 */
	public record Active(Mission mission, int progress, Optional<BlockPos> place, boolean bounty) {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Mission.CODEC.fieldOf("mission").forGetter(Active::mission),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(Active::progress),
				BlockPos.CODEC.optionalFieldOf("place").forGetter(Active::place),
				Codec.BOOL.optionalFieldOf("bounty", false).forGetter(Active::bounty)
		).apply(instance, Active::new));

		public Active(Mission mission, int progress) {
			this(mission, progress, Optional.empty(), false);
		}

		Active progressed(int progress, Optional<BlockPos> place) {
			return new Active(this.mission, progress, place, this.bounty);
		}

		int xp() {
			return this.bounty ? this.mission.xp * 2 : this.mission.xp;
		}

		float relicChance() {
			return this.bounty ? Math.min(1.0F, this.mission.relicChance * 2.0F) : this.mission.relicChance;
		}

		Component description() {
			if (this.mission.kind == Kind.RESCUE && this.place.isPresent()) {
				BlockPos at = this.place.get();
				return Component.translatable("mission.rings.rescue_mars_at", at.getX(), at.getZ());
			}
			return Component.translatable("mission.rings." + this.mission.getSerializedName(), this.mission.count);
		}

		Component reward() {
			float chance = this.relicChance();
			Component relic = chance >= 1.0F ? Component.translatable("mission.rings.reward_relic")
					: chance > 0.0F ? Component.translatable("mission.rings.reward_maybe_relic") : Component.empty();
			return Component.translatable("mission.rings.reward", this.xp(), relic);
		}
	}

	public static final AttachmentType<Active> MISSION = AttachmentRegistry.create(Rings.id("mission"),
			builder -> builder.persistent(Active.CODEC).copyOnDeath());
	/** The day (counted from 1970) the player last turned in a bounty: one a day. */
	public static final AttachmentType<Long> BOUNTY_DAY = AttachmentRegistry.create(Rings.id("bounty_day"),
			builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Scans for a scan mission must be this far apart. */
	private static final double SCAN_APART = 64.0;
	/** Marks the stranded explorer, so arriving home with them can be told. */
	public static final String STRANDED = "rings_stranded";

	private Missions() {
	}

	static void init() {
		// Loads the class so the attachments register during mod init
	}

	public static @Nullable Active of(ServerPlayer player) {
		return player.getAttached(MISSION);
	}

	private static long today() {
		return LocalDate.now().toEpochDay();
	}

	/** How far into it the player is: hunted, scanned or done so far, or how many of the things they carry. */
	private static int progress(ServerPlayer player, Active active) {
		Mission mission = active.mission();
		return mission.kind == Kind.BRING ? player.getInventory().countItem(mission.item.get()) : active.progress();
	}

	private static Component progressText(ServerPlayer player, Active active) {
		return Component.translatable("mission.rings.progress", Math.min(progress(player, active), active.mission().count), active.mission().count);
	}

	private static List<Mission> open(ServerPlayer player) {
		int level = Mastery.level(player);
		List<Mission> open = new ArrayList<>();
		for (Mission mission : Mission.values()) {
			if (level >= mission.minLevel) {
				open.add(mission);
			}
		}
		return open;
	}

	/** The player asked a Guardian for work: their current task, or a new one. */
	static void offer(ServerPlayer player, OaGuardian guardian) {
		Active active = of(player);
		if (active != null) {
			reply(player, guardian, "mission_active", active.description(), progressText(player, active));
			return;
		}
		List<Mission> open = open(player);
		start(player, guardian, new Active(open.get(player.getRandom().nextInt(open.size())), 0), "mission_new");
	}

	/** The player asked for the day's bounty: the same task for everyone that day, at double the reward. */
	static void bounty(ServerPlayer player, OaGuardian guardian) {
		if (player.getAttachedOrElse(BOUNTY_DAY, Long.MIN_VALUE) == today()) {
			reply(player, guardian, "bounty_done_today");
			return;
		}
		Active active = of(player);
		if (active != null) {
			reply(player, guardian, "mission_active", active.description(), progressText(player, active));
			return;
		}
		List<Mission> open = open(player);
		Mission mission = open.get(new Random(today()).nextInt(open.size()));
		start(player, guardian, new Active(mission, 0, Optional.empty(), true), "bounty_new");
	}

	private static void start(ServerPlayer player, OaGuardian guardian, Active active, String node) {
		if (active.mission().kind == Kind.RESCUE) {
			BlockPos at = strand(player);
			if (at == null) {
				reply(player, guardian, "mission_none");
				return;
			}
			active = new Active(active.mission(), 0, Optional.of(at), active.bounty());
		}
		player.setAttached(MISSION, active);
		reply(player, guardian, node, active.description(), active.reward());
	}

	/** Puts the explorer to be rescued somewhere out on Mars, and says where. */
	private static @Nullable BlockPos strand(ServerPlayer player) {
		ServerLevel mars = player.level().getServer().getLevel(Space.Planet.MARS.dimension());
		if (mars == null) {
			return null;
		}
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
		double distance = 150.0 + player.getRandom().nextDouble() * 250.0;
		int x = Mth.floor(Math.cos(angle) * distance);
		int z = Mth.floor(Math.sin(angle) * distance);
		mars.getChunk(x >> 4, z >> 4);
		int y = mars.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		StrandedExplorer explorer = Rings.STRANDED_EXPLORER.create(mars, EntitySpawnReason.EVENT);
		if (explorer == null) {
			return null;
		}
		explorer.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
		explorer.setCustomName(Component.translatable("mission.rings.explorer"));
		explorer.setCustomNameVisible(true);
		explorer.setPersistenceRequired();
		explorer.addTag(STRANDED);
		mars.addFreshEntity(explorer);
		return new BlockPos(x, y, z);
	}

	/** The player says they've done their task. */
	static void turnIn(ServerPlayer player, OaGuardian guardian) {
		Active active = of(player);
		if (active == null) {
			reply(player, guardian, "mission_none");
			return;
		}
		Mission mission = active.mission();
		if (progress(player, active) < mission.count) {
			reply(player, guardian, "mission_not_done", active.description(), progressText(player, active));
			return;
		}
		if (mission.kind == Kind.BRING) {
			take(player, mission.item.get(), mission.count);
		}
		player.removeAttached(MISSION);
		boolean relic = player.getRandom().nextFloat() < active.relicChance();
		if (relic) {
			ItemStack stack = new ItemStack(Rings.OAN_RELIC);
			if (!player.getInventory().add(stack)) {
				player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
			}
		}
		int xp = (int) Math.round(active.xp() * RingsConfig.get().missionXpMultiplier);
		Mastery.addXp(player, xp);
		Mastery.first(player, Mastery.First.MISSION);
		Codex.discover(player, "mission");
		if (active.bounty()) {
			player.setAttached(BOUNTY_DAY, today());
			Codex.discover(player, "bounty");
		}
		RingCorps.missionDone(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		reply(player, guardian, "mission_done", Component.literal(String.valueOf(xp)),
				relic ? Component.translatable("mission.rings.reward_relic") : Component.empty());
	}

	/** The Guardians' roll of honour. */
	static void roll(ServerPlayer player, OaGuardian guardian) {
		reply(player, guardian, "roll", RingCorps.roll(player.level().getServer()));
	}

	private static void advance(ServerPlayer player, Active active, int progress, Optional<BlockPos> place) {
		Active next = active.progressed(progress, place);
		player.setAttached(MISSION, next);
		player.sendOverlayMessage(Component.translatable("mission.rings.progressed", active.description(),
				Math.min(progress, active.mission().count), active.mission().count).withStyle(ChatFormatting.GOLD));
	}

	/** Something died at the player's hand: it may be what they, or their Corps comrades, were sent to hunt. */
	static void killed(ServerPlayer player, Entity killed) {
		List<ServerPlayer> hunters = new ArrayList<>(RingCorps.comrades(player));
		hunters.addFirst(player);
		for (ServerPlayer hunter : hunters) {
			Active active = of(hunter);
			if (active != null && active.mission().kind == Kind.HUNT && active.mission().quarry.test(killed)) {
				advance(hunter, active, active.progress() + 1, active.place());
			}
		}
	}

	/** The player scanned: on Mars, far enough from their last scan, it counts. */
	static void scanned(ServerPlayer player) {
		Active active = of(player);
		if (active == null || active.mission().kind != Kind.SCAN || player.level().dimension() != Space.Planet.MARS.dimension()) {
			return;
		}
		BlockPos here = player.blockPosition();
		if (active.place().isPresent() && active.place().get().distSqr(here) < SCAN_APART * SCAN_APART) {
			player.sendOverlayMessage(Component.translatable("mission.rings.scan_too_close").withStyle(ChatFormatting.GRAY));
			return;
		}
		advance(player, active, active.progress() + 1, Optional.of(here));
	}

	/** The player arrived somewhere, carrying these in bubbles: an escort or a rescue may be done. */
	static void arrived(ServerPlayer player, ResourceKey<Level> where, List<Entity> carried) {
		Active active = of(player);
		if (active == null || active.progress() >= active.mission().count) {
			return;
		}
		if (active.mission().kind == Kind.CARRY && where == Space.Planet.MOON.dimension() && !carried.isEmpty()) {
			advance(player, active, 1, active.place());
		} else if (active.mission().kind == Kind.RESCUE && where == Level.OVERWORLD
				&& carried.stream().anyMatch(entity -> entity.entityTags().contains(STRANDED))) {
			for (Entity explorer : carried) {
				if (explorer.entityTags().contains(STRANDED)) {
					explorer.removeTag(STRANDED);
					explorer.setCustomName(Component.translatable("mission.rings.explorer_saved"));
				}
			}
			advance(player, active, 1, active.place());
		}
	}

	/** Takes so many of an item out of the player's inventory. */
	static void take(ServerPlayer player, Item item, int count) {
		for (int i = 0; i < player.getInventory().getContainerSize() && count > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int taken = Math.min(count, stack.getCount());
				stack.shrink(taken);
				count -= taken;
			}
		}
	}

	/** The Guardian answers, carrying on the player's talk with them. */
	static void reply(ServerPlayer player, OaGuardian guardian, String node, Component... args) {
		ServerPlayNetworking.send(player, new OpenDialoguePayload(Dialogues.Dialogue.GUARDIAN, node, RingColor.GREEN, guardian.getId(), List.of(args)));
	}
}
