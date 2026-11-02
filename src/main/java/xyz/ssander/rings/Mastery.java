package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How far a player has grown with their ring. Wearing it, flying, fighting, building and charging all earn ring
 * experience; each level up to {@link #MAX_LEVEL} gives a skill point, spent on the skill tree ({@link Skill}). A
 * new bearer starts at level 1 with one point, so someone who has had a ring a while is far stronger. It's the
 * player's, not the ring's: it stays with them through death and whatever ring they wear.
 */
public final class Mastery {
	public static final int MAX_LEVEL = 40;
	/** The level that players who already had a ring before levels came in start at. */
	private static final int VETERAN_LEVEL = 10;

	/**
	 * A player's ring experience (all they've earned), the skills they've learned, the extra skill points Oan relics
	 * have given them, and the firsts they've been rewarded for (see {@link First}).
	 */
	public record Data(int xp, List<Skill> skills, int bonus, List<String> firsts) {
		public static final Data NEW = new Data(0, List.of());
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("xp").forGetter(Data::xp),
				Skill.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(Data::skills),
				Codec.INT.optionalFieldOf("bonus", 0).forGetter(Data::bonus),
				Codec.STRING.listOf().optionalFieldOf("firsts", List.of()).forGetter(Data::firsts)
		).apply(instance, Data::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Data::xp,
				Skill.STREAM_CODEC.apply(ByteBufCodecs.list()), Data::skills,
				ByteBufCodecs.VAR_INT, Data::bonus,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Data::firsts,
				Data::new);

		public Data(int xp, List<Skill> skills) {
			this(xp, skills, 0, List.of());
		}

		public int level() {
			return levelFor(this.xp);
		}

		public boolean has(Skill skill) {
			return this.skills.contains(skill);
		}

		/** Skill points earned (one a level, and one for each Oan relic) and not yet spent. */
		public int points() {
			int spent = 0;
			for (Skill skill : this.skills) {
				spent += skill.cost();
			}
			return this.level() + this.bonus - spent;
		}

		/** Why the skill can't be learned yet, or null if it can. */
		public @Nullable Component blocker(Skill skill) {
			if (this.has(skill)) {
				return Component.translatable("skill.rings.learned");
			}
			if (skill.parent() != null && !this.has(skill.parent())) {
				return Component.translatable("skill.rings.needs", Component.translatable(skill.parent().translationKey()));
			}
			if (this.level() < skill.minLevel()) {
				return Component.translatable("skill.rings.needs_level", skill.minLevel());
			}
			if (this.points() < skill.cost()) {
				return Component.translatable("skill.rings.needs_points", skill.cost());
			}
			return null;
		}

		Data withXp(int xp) {
			return new Data(xp, this.skills, this.bonus, this.firsts);
		}

		Data with(Skill skill) {
			List<Skill> skills = new ArrayList<>(this.skills);
			skills.add(skill);
			return new Data(this.xp, List.copyOf(skills), this.bonus, this.firsts);
		}

		Data withSkills(List<Skill> skills) {
			return new Data(this.xp, List.copyOf(skills), this.bonus, this.firsts);
		}

		Data withBonus(int bonus) {
			return new Data(this.xp, this.skills, bonus, this.firsts);
		}

		Data withFirst(String first) {
			List<String> firsts = new ArrayList<>(this.firsts);
			firsts.add(first);
			return new Data(this.xp, this.skills, this.bonus, List.copyOf(firsts));
		}
	}

	/** Synced to the player themselves, for the skill screen, the HUD and what their client lets them do. */
	public static final AttachmentType<Data> MASTERY = AttachmentRegistry.create(Rings.id("mastery"),
			builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/**
	 * The player's ring level, for everyone else: their Corps rank shows under their name (see {@link #rank}). Kept in
	 * step with {@link #MASTERY} by {@link #store}.
	 */
	public static final AttachmentType<Integer> RANK = AttachmentRegistry.create(Rings.id("rank"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** The Corps ranks, from the ring level each starts at. */
	private static final int[] RANK_LEVELS = {1, 5, 10, 20, 30, 40};
	private static final String[] RANK_NAMES = {"recruit", "lantern", "veteran", "honor_guard", "alpha", "champion"};

	/** The Corps rank for a ring level, as a lang key. */
	public static String rank(int level) {
		String name = RANK_NAMES[0];
		for (int i = 0; i < RANK_LEVELS.length; i++) {
			if (level >= RANK_LEVELS[i]) {
				name = RANK_NAMES[i];
			}
		}
		return "rank.rings." + name;
	}

	/** Saves the player's mastery, and their rank for everyone to see. */
	static void store(ServerPlayer player, Data data) {
		player.setAttached(MASTERY, data);
		int level = data.level();
		if (player.getAttachedOrElse(RANK, 0) != level) {
			player.setAttached(RANK, level);
		}
	}

	/** Set once a dying ring-bearer has handed a player their ring (it unlocks the pilot's suit). */
	public static final AttachmentType<Boolean> HEIR = AttachmentRegistry.create(Rings.id("heir"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private Mastery() {
	}

	static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register(Mastery::died);
	}

	/** The ring experience it takes to reach a level, counted from nothing. */
	public static int xpFor(int level) {
		int n = Math.max(0, level - 1);
		return 30 * n + 8 * n * n;
	}

	public static int levelFor(int xp) {
		int level = 1;
		while (level < MAX_LEVEL && xp >= xpFor(level + 1)) {
			level++;
		}
		return level;
	}

	public static Data of(Player player) {
		return player.getAttachedOrElse(MASTERY, Data.NEW);
	}

	public static int level(Player player) {
		return of(player).level();
	}

	public static boolean has(Player player, Skill skill) {
		return of(player).has(skill);
	}

	public static boolean isHeir(Player player) {
		return player.getAttachedOrElse(HEIR, false);
	}

	/**
	 * Tells the player they need a skill for what they just tried, and returns false, unless they have it (true).
	 */
	public static boolean require(ServerPlayer player, Skill skill) {
		if (has(player, skill)) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.rings.needs_skill", Component.translatable(skill.translationKey()))
				.withStyle(ChatFormatting.GRAY));
		return false;
	}

	/**
	 * Players already wearing a ring when levels came in are veterans: they start well up the tree rather than back at
	 * the bottom. Everyone else starts fresh.
	 */
	private static void joined(ServerPlayer player) {
		if (player.hasAttached(MASTERY)) {
			store(player, of(player));
			return;
		}
		boolean veteran = RingAttachments.getRing(player).getItem() instanceof RingItem
				|| player.getInventory().contains(stack -> stack.getItem() instanceof RingItem);
		store(player, new Data(veteran ? xpFor(VETERAN_LEVEL) : 0, List.of()));
		if (veteran) {
			player.sendSystemMessage(Component.translatable("message.rings.veteran", VETERAN_LEVEL).withStyle(ChatFormatting.GREEN));
		}
	}

	/** Gives the player ring experience, levelling them up when it's enough. On Oa, in the Battery's light, it's doubled. */
	public static void addXp(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return;
		}
		if (player.level().dimension() == Space.Planet.OA.dimension()) {
			amount *= 2;
		}
		Data data = of(player);
		int before = data.level();
		Data after = data.withXp(Math.min(data.xp() + amount, xpFor(MAX_LEVEL)));
		if (after.xp() == data.xp()) {
			return;
		}
		store(player, after);
		if (after.level() > before) {
			levelledUp(player, after);
		}
	}

	private static void levelledUp(ServerPlayer player, Data data) {
		ServerLevel level = player.level();
		RingColor color = RingAttachments.getRing(player).getItem() instanceof RingItem ring ? ring.color() : RingColor.GREEN;
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.rings.level_up_title", data.level())
				.withColor(color.ui())));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.rings.level_up_subtitle", data.points())));
		player.sendSystemMessage(Component.translatable("message.rings.level_up", data.level(), data.points()).withColor(color.ui()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.sendParticles(color.dust(0.4F, 1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 70, 0.6, 1.0, 0.6, 0.0);
	}

	/** The player asked to learn a skill on the skill screen. */
	public static void learn(ServerPlayer player, Skill skill) {
		Data data = of(player);
		Component blocker = data.blocker(skill);
		if (blocker != null) {
			player.sendOverlayMessage(blocker);
			return;
		}
		store(player, data.with(skill));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.rings.learned", Component.translatable(skill.translationKey())));
	}

	/** Sets the player's level (for operators, and the tests), keeping whichever skills still fit. */
	public static void setLevel(ServerPlayer player, int level) {
		int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
		Data data = new Data(xpFor(clamped), List.of(), of(player).bonus(), of(player).firsts());
		for (Skill skill : of(player).skills()) {
			if (data.blocker(skill) == null) {
				data = data.with(skill);
			}
		}
		store(player, data);
	}

	/** Every skill, the top level and every suit (for operators, and the tests). */
	public static void unlockAll(ServerPlayer player) {
		store(player, new Data(xpFor(MAX_LEVEL), List.of(Skill.values()), of(player).bonus(), of(player).firsts()));
		player.setAttached(HEIR, true);
	}

	/**
	 * Unlearns every skill, giving back the points (a Guardian's respec). Returns false if there was nothing to unlearn.
	 */
	public static boolean unlearnAll(ServerPlayer player) {
		Data data = of(player);
		if (data.skills().isEmpty()) {
			return false;
		}
		store(player, data.withSkills(List.of()));
		return true;
	}

	/** An extra skill point (an Oan relic's). */
	public static void addBonusPoint(ServerPlayer player) {
		Data data = of(player);
		store(player, data.withBonus(data.bonus() + 1));
	}

	/** One-off rewards of ring experience for the first time a player does something. */
	public enum First {
		SPACE(100),
		MOON(150),
		MARS(150),
		OA(200),
		BATTERY(50),
		MISSION(100),
		DUEL(50),
		PARALLAX(500),
		NEKRON(500),
		DEVOURER(800);

		private final int xp;

		First(int xp) {
			this.xp = xp;
		}

		public String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Rewards the player the first time they do something; nothing after that. */
	public static void first(ServerPlayer player, First first) {
		Data data = of(player);
		if (data.firsts().contains(first.key())) {
			return;
		}
		store(player, data.withFirst(first.key()));
		player.sendSystemMessage(Component.translatable("first.rings." + first.key(), first.xp).withStyle(ChatFormatting.GOLD));
		addXp(player, first.xp);
	}

	/** Back to a new bearer (for operators, and the tests). */
	public static void reset(ServerPlayer player) {
		store(player, Data.NEW);
	}

	// What the skills do, read by the powers they change

	/** The share of the charge the ring uses: a quarter less with efficiency, half with willpower. */
	public static double drainFactor(Player player) {
		Data data = of(player);
		return data.has(Skill.WILLPOWER) ? 0.5 : data.has(Skill.EFFICIENCY) ? 0.75 : 1.0;
	}

	/**
	 * A charge cost after efficiency, rounded at random so small costs come out right on average (a cost of 1 at a
	 * quarter off is free one time in four).
	 */
	public static int cost(Player player, int cost) {
		double scaled = cost * drainFactor(player);
		int whole = (int) Math.floor(scaled);
		return whole + (player.getRandom().nextDouble() < scaled - whole ? 1 : 0);
	}

	/** The ring's armor, before a red ring's rage halves it. */
	public static double armor(Player player) {
		Data data = of(player);
		return data.has(Skill.FORTRESS) ? 12.0 : data.has(Skill.HARD_LIGHT_SKIN) ? 8.0 : 4.0;
	}

	public static double toughness(Player player) {
		Data data = of(player);
		return data.has(Skill.FORTRESS) ? 4.0 : data.has(Skill.HARD_LIGHT_SKIN) ? 2.0 : 0.0;
	}

	public static float beamDamage(Player player) {
		Data data = of(player);
		return data.has(Skill.ANNIHILATOR) ? 9.0F : data.has(Skill.LANCE) ? 6.0F : data.has(Skill.FOCUSED_BEAM) ? 4.0F : 2.5F;
	}

	/** How fast the player hovers on the ring: a little faster each level, up to nearly twice vanilla's flight. */
	public static float flyingSpeed(Player player) {
		return 0.05F + 0.001F * (level(player) - 1);
	}

	/** How long walls, bridges, cages and construct creatures last, as a share of their usual time. */
	public static int lifetime(Player player, int ticks) {
		return has(player, Skill.ENDURING) ? ticks * 2 : ticks;
	}

	/** Ring experience for what the player did. */
	public static final int XP_WORN = 1;
	public static final int XP_FLYING = 1;
	public static final int XP_BEAM_HIT = 1;
	public static final int XP_CONSTRUCT = 2;
	public static final int XP_ITEM = 1;
	public static final int XP_POWER = 3;
	public static final int XP_ABILITY = 2;
	public static final int XP_CHARGED = 10;

	/** Something died: whoever killed it with their ring on (or by their ring's creatures) earns experience. */
	private static void died(LivingEntity entity, DamageSource source) {
		ServerPlayer killer = killer(entity, source);
		if (killer == null || killer == entity) {
			return;
		}
		Missions.killed(killer, entity);
		if (RingAttachments.activeColor(killer) == null) {
			return;
		}
		addXp(killer, xpFor(entity));
		if (entity instanceof Parallax) {
			first(killer, First.PARALLAX);
		} else if (entity instanceof Nekron) {
			first(killer, First.NEKRON);
		} else if (entity instanceof Devourer) {
			first(killer, First.DEVOURER);
		}
	}

	private static @Nullable ServerPlayer killer(LivingEntity entity, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			return player;
		}
		if (attacker != null && entity.level() instanceof ServerLevel level) {
			// A ring's creatures earn for their maker
			Thralls.Thrall thrall = attacker instanceof LivingEntity living ? Thralls.of(living) : null;
			if (thrall != null && thrall.owner().isPresent()) {
				return level.getServer().getPlayerList().getPlayer(thrall.owner().get());
			}
		}
		return null;
	}

	private static int xpFor(LivingEntity killed) {
		if (killed instanceof Devourer) {
			return 600;
		}
		if (killed instanceof Parallax || killed instanceof Nekron) {
			return 400;
		}
		if (killed instanceof Manhunter) {
			return 25;
		}
		if (killed instanceof Player) {
			return 30;
		}
		if (killed instanceof Enemy) {
			return Math.max(3, Math.min(60, Math.round(killed.getMaxHealth() / 4.0F)));
		}
		return 1;
	}
}
