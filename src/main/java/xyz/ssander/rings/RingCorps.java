package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Corps: ring-bearers banding together. {@code /corps create <name>} founds one, the leader {@code /corps invite}s
 * players, who {@code /corps join} it; {@code /corps chat} (or {@code /cc}) talks to everyone in it. Members' names show
 * their Corps, their rings and ring creatures don't hurt each other (unless they're duelling), and a creature one of
 * them hunts for a Guardian's mission counts for every member on the same hunt. The Guardians keep a roll of honour of
 * the greatest ring-bearers and Corps.
 */
public final class RingCorps {
	/** A Corps: its name, leader, members and those invited, and the missions its members have done. */
	public record Corps(String name, UUID leader, List<UUID> members, List<UUID> invited, int missions) {
		static final Codec<Corps> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Corps::name),
				UUIDUtil.CODEC.fieldOf("leader").forGetter(Corps::leader),
				UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Corps::members),
				UUIDUtil.CODEC.listOf().optionalFieldOf("invited", List.of()).forGetter(Corps::invited),
				Codec.INT.optionalFieldOf("missions", 0).forGetter(Corps::missions)
		).apply(instance, Corps::new));
	}

	/** What the roll of honour knows of a ring-bearer. */
	public record Entry(String name, int level, int missions) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Entry::name),
				Codec.INT.fieldOf("level").forGetter(Entry::level),
				Codec.INT.optionalFieldOf("missions", 0).forGetter(Entry::missions)
		).apply(instance, Entry::new));
	}

	/** Every Corps, by name, and the roll of honour, by player: kept on the Overworld. */
	private static final AttachmentType<Map<String, Corps>> ALL = AttachmentRegistry.create(Rings.id("corps"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Corps.CODEC)));
	private static final AttachmentType<Map<String, Entry>> ROLL = AttachmentRegistry.create(Rings.id("roll_of_honour"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Entry.CODEC)));
	/** The name of the player's Corps, for everyone to see on their nameplate. */
	public static final AttachmentType<String> NAME = AttachmentRegistry.create(Rings.id("corps_name"),
			builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private static final int MAX_NAME = 16;

	private RingCorps() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> register(dispatcher));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RingCorps::allowDamage);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			refreshName(handler.player);
			onRoll(handler.player);
		});
	}

	private static Map<String, Corps> all(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ALL, Map.of());
	}

	private static void save(MinecraftServer server, Map<String, Corps> corps) {
		server.overworld().setAttached(ALL, Map.copyOf(corps));
	}

	/** The Corps the player is in, if any. */
	public static @Nullable Corps of(MinecraftServer server, UUID player) {
		for (Corps corps : all(server).values()) {
			if (corps.members().contains(player)) {
				return corps;
			}
		}
		return null;
	}

	/** Whether the two are in the same Corps. */
	public static boolean together(Player one, Player other) {
		if (one == other || one.level().getServer() == null) {
			return false;
		}
		Corps corps = of(one.level().getServer(), one.getUUID());
		return corps != null && corps.members().contains(other.getUUID());
	}

	/** Every member of the player's Corps who's online, but them. */
	public static List<ServerPlayer> comrades(ServerPlayer player) {
		Corps corps = of(player.level().getServer(), player.getUUID());
		List<ServerPlayer> online = new ArrayList<>();
		if (corps != null) {
			for (UUID member : corps.members()) {
				ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(member);
				if (other != null && other != player) {
					online.add(other);
				}
			}
		}
		return online;
	}

	private static void refreshName(ServerPlayer player) {
		Corps corps = of(player.level().getServer(), player.getUUID());
		String name = corps == null ? null : corps.name();
		if (!java.util.Objects.equals(name, player.getAttached(NAME))) {
			player.setAttached(NAME, name);
		}
	}

	private static void refreshAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshName(player);
		}
	}

	/** Rings and ring creatures don't hurt members of their own Corps (duellists excepted). */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer target)) {
			return true;
		}
		Player attacker = attackerOf(source.getEntity());
		return attacker == null || !together(target, attacker) || RingDuels.duelling(target, attacker);
	}

	private static @Nullable Player attackerOf(@Nullable Entity entity) {
		if (entity instanceof Player player) {
			return player;
		}
		if (entity instanceof Mob mob && Thralls.of(mob) != null) {
			return Thralls.master(mob);
		}
		return null;
	}

	// The roll of honour

	/** Puts the player's latest standing on the roll. */
	static void onRoll(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Map<String, Entry> roll = new HashMap<>(server.overworld().getAttachedOrElse(ROLL, Map.of()));
		Entry was = roll.get(player.getStringUUID());
		roll.put(player.getStringUUID(), new Entry(player.getGameProfile().name(), Mastery.level(player), was == null ? 0 : was.missions()));
		server.overworld().setAttached(ROLL, Map.copyOf(roll));
	}

	/** A mission done: counted for the player, and their Corps. */
	static void missionDone(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Map<String, Entry> roll = new HashMap<>(server.overworld().getAttachedOrElse(ROLL, Map.of()));
		Entry was = roll.get(player.getStringUUID());
		roll.put(player.getStringUUID(), new Entry(player.getGameProfile().name(), Mastery.level(player), was == null ? 1 : was.missions() + 1));
		server.overworld().setAttached(ROLL, Map.copyOf(roll));
		Corps corps = of(server, player.getUUID());
		if (corps != null) {
			Map<String, Corps> all = new HashMap<>(all(server));
			all.put(corps.name(), new Corps(corps.name(), corps.leader(), corps.members(), corps.invited(), corps.missions() + 1));
			save(server, all);
		}
	}

	/** The roll of honour, as the Guardians tell it: the five greatest ring-bearers, and the three greatest Corps. */
	static Component roll(MinecraftServer server) {
		Map<String, Entry> roll = server.overworld().getAttachedOrElse(ROLL, Map.of());
		List<Entry> best = roll.values().stream()
				.sorted(Comparator.comparingInt(Entry::level).thenComparingInt(Entry::missions).reversed())
				.limit(5).toList();
		MutableComponent text = Component.empty();
		int place = 1;
		for (Entry entry : best) {
			text.append(Component.translatable("roll.rings.bearer", place++, entry.name(), entry.level(), entry.missions())).append("\n");
		}
		List<Corps> corps = all(server).values().stream()
				.sorted(Comparator.comparingInt((Corps c) -> strength(roll, c)).thenComparingInt(Corps::missions).reversed())
				.limit(3).toList();
		place = 1;
		for (Corps each : corps) {
			text.append(Component.translatable("roll.rings.corps", place++, each.name(), each.members().size(), strength(roll, each), each.missions())).append("\n");
		}
		return text;
	}

	/** A Corps' strength: the ring levels of its members, together. */
	private static int strength(Map<String, Entry> roll, Corps corps) {
		int total = 0;
		for (UUID member : corps.members()) {
			Entry entry = roll.get(member.toString());
			total += entry == null ? 1 : entry.level();
		}
		return total;
	}

	// Commands

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("corps")
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
						.executes(context -> create(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
						.executes(context -> invite(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word())
						.executes(context -> join(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
				.then(Commands.literal("leave").executes(context -> leave(context.getSource().getPlayerOrException())))
				.then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player())
						.executes(context -> kick(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("info").executes(context -> info(context.getSource().getPlayerOrException())))
				.then(Commands.literal("chat").then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(context -> chat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message"))))));
		dispatcher.register(Commands.literal("cc").then(Commands.argument("message", StringArgumentType.greedyString())
				.executes(context -> chat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")))));
	}

	private static int fail(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
		return 0;
	}

	private static void tell(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD));
	}

	private static int create(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();
		if (!(RingAttachments.getRing(player).getItem() instanceof RingItem)) {
			return fail(player, "corps.rings.no_ring");
		}
		if (of(server, player.getUUID()) != null) {
			return fail(player, "corps.rings.already");
		}
		if (name.length() > MAX_NAME || all(server).keySet().stream().anyMatch(name::equalsIgnoreCase)) {
			return fail(player, "corps.rings.bad_name", MAX_NAME);
		}
		Map<String, Corps> all = new HashMap<>(all(server));
		all.put(name, new Corps(name, player.getUUID(), List.of(player.getUUID()), List.of(), 0));
		save(server, all);
		refreshName(player);
		Codex.discover(player, "corps");
		tell(player, "corps.rings.created", name);
		return 1;
	}

	private static int invite(ServerPlayer player, ServerPlayer target) {
		MinecraftServer server = player.level().getServer();
		Corps corps = of(server, player.getUUID());
		if (corps == null || !corps.leader().equals(player.getUUID())) {
			return fail(player, "corps.rings.not_leader");
		}
		if (of(server, target.getUUID()) != null) {
			return fail(player, "corps.rings.taken", target.getDisplayName());
		}
		List<UUID> invited = new ArrayList<>(corps.invited());
		if (!invited.contains(target.getUUID())) {
			invited.add(target.getUUID());
		}
		Map<String, Corps> all = new HashMap<>(all(server));
		all.put(corps.name(), new Corps(corps.name(), corps.leader(), corps.members(), List.copyOf(invited), corps.missions()));
		save(server, all);
		tell(player, "corps.rings.invited", target.getDisplayName());
		tell(target, "corps.rings.you_invited", player.getDisplayName(), corps.name());
		return 1;
	}

	private static int join(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();
		Corps corps = all(server).get(name);
		if (corps == null || !corps.invited().contains(player.getUUID())) {
			return fail(player, "corps.rings.not_invited", name);
		}
		if (of(server, player.getUUID()) != null) {
			return fail(player, "corps.rings.already");
		}
		List<UUID> members = new ArrayList<>(corps.members());
		members.add(player.getUUID());
		List<UUID> invited = new ArrayList<>(corps.invited());
		invited.remove(player.getUUID());
		Map<String, Corps> all = new HashMap<>(all(server));
		all.put(name, new Corps(name, corps.leader(), List.copyOf(members), List.copyOf(invited), corps.missions()));
		save(server, all);
		refreshName(player);
		Codex.discover(player, "corps");
		announce(server, all.get(name), Component.translatable("corps.rings.joined", player.getDisplayName(), name));
		return 1;
	}

	private static int leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Corps corps = of(server, player.getUUID());
		if (corps == null) {
			return fail(player, "corps.rings.none");
		}
		remove(server, corps, player.getUUID());
		tell(player, "corps.rings.left", corps.name());
		return 1;
	}

	private static int kick(ServerPlayer player, ServerPlayer target) {
		MinecraftServer server = player.level().getServer();
		Corps corps = of(server, player.getUUID());
		if (corps == null || !corps.leader().equals(player.getUUID()) || !corps.members().contains(target.getUUID()) || target == player) {
			return fail(player, "corps.rings.not_leader");
		}
		remove(server, corps, target.getUUID());
		tell(target, "corps.rings.kicked", corps.name());
		tell(player, "corps.rings.you_kicked", target.getDisplayName());
		return 1;
	}

	/** Takes a member out; the last to go disbands it, and a leader who goes hands it to the longest-standing member. */
	private static void remove(MinecraftServer server, Corps corps, UUID member) {
		List<UUID> members = new ArrayList<>(corps.members());
		members.remove(member);
		Map<String, Corps> all = new HashMap<>(all(server));
		if (members.isEmpty()) {
			all.remove(corps.name());
		} else {
			UUID leader = corps.leader().equals(member) ? members.getFirst() : corps.leader();
			all.put(corps.name(), new Corps(corps.name(), leader, List.copyOf(members), corps.invited(), corps.missions()));
		}
		save(server, all);
		refreshAll(server);
	}

	private static int info(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Corps corps = of(server, player.getUUID());
		if (corps == null) {
			return fail(player, "corps.rings.none");
		}
		List<String> names = new ArrayList<>();
		Map<String, Entry> roll = server.overworld().getAttachedOrElse(ROLL, Map.of());
		for (UUID member : corps.members()) {
			Entry entry = roll.get(member.toString());
			names.add((member.equals(corps.leader()) ? "★ " : "") + (entry == null ? member.toString() : entry.name() + " (Lv " + entry.level() + ")"));
		}
		tell(player, "corps.rings.info", corps.name(), String.join(", ", names), corps.missions());
		return 1;
	}

	private static int chat(ServerPlayer player, String message) {
		MinecraftServer server = player.level().getServer();
		Corps corps = of(server, player.getUUID());
		if (corps == null) {
			return fail(player, "corps.rings.none");
		}
		announce(server, corps, Component.translatable("corps.rings.chat", corps.name(), player.getDisplayName(), message));
		return 1;
	}

	private static void announce(MinecraftServer server, Corps corps, Component message) {
		RingColor color = RingColor.GREEN;
		for (UUID member : corps.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				player.sendSystemMessage(message.copy().withColor(color.ui()));
			}
		}
	}
}
