package xyz.ssander.rings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Ring duels: {@code /duel <player>} challenges another ring-bearer, and they {@code /duel accept} (or decline). While
 * they duel they can hurt each other even with PvP off, and a blow that would kill one ends the duel instead, leaving
 * them on half a heart: the other wins, and both earn ring experience, the winner more. A duel also ends, drawn, after
 * five minutes, or if they get too far apart or either leaves.
 */
public final class RingDuels {
	private static final int CHALLENGE_TICKS = 60 * 20;
	private static final int DUEL_TICKS = 5 * 60 * 20;
	private static final double MAX_APART = 96.0;
	private static final int WIN_XP = 60;
	private static final int LOSE_XP = 15;

	private record Challenge(UUID challenger, long expires) {
	}

	private record Duel(UUID a, UUID b, long ends) {
		UUID other(UUID one) {
			return one.equals(this.a) ? this.b : this.a;
		}
	}

	/** Challenges by who was challenged, and duels by each duellist. */
	private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
	private static final Map<UUID, Duel> DUELS = new HashMap<>();

	private RingDuels() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> register(dispatcher));
		ServerLivingEntityEvents.ALLOW_DEATH.register(RingDuels::allowDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(server, handler.player.getUUID(), null));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("duel")
				.then(Commands.literal("accept").executes(context -> accept(context.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("decline").executes(context -> accept(context.getSource().getPlayerOrException(), false)))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> challenge(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))));
	}

	/** Whether the two are duelling each other. */
	public static boolean duelling(Player one, Player other) {
		Duel duel = DUELS.get(one.getUUID());
		return duel != null && duel.other(one.getUUID()).equals(other.getUUID());
	}

	private static boolean hasRing(Player player) {
		return RingAttachments.getRing(player).getItem() instanceof RingItem;
	}

	private static int challenge(ServerPlayer challenger, ServerPlayer target) {
		if (target == challenger) {
			challenger.sendSystemMessage(Component.translatable("duel.rings.self").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!hasRing(challenger) || !hasRing(target)) {
			challenger.sendSystemMessage(Component.translatable("duel.rings.no_ring").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (DUELS.containsKey(challenger.getUUID()) || DUELS.containsKey(target.getUUID())) {
			challenger.sendSystemMessage(Component.translatable("duel.rings.busy").withStyle(ChatFormatting.RED));
			return 0;
		}
		CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), challenger.level().getGameTime() + CHALLENGE_TICKS));
		challenger.sendSystemMessage(Component.translatable("duel.rings.sent", target.getDisplayName()).withStyle(ChatFormatting.GOLD));
		target.sendSystemMessage(Component.translatable("duel.rings.challenged", challenger.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int accept(ServerPlayer target, boolean accepted) {
		Challenge challenge = CHALLENGES.remove(target.getUUID());
		MinecraftServer server = target.level().getServer();
		ServerPlayer challenger = challenge == null ? null : server.getPlayerList().getPlayer(challenge.challenger());
		if (challenge == null || challenger == null || target.level().getGameTime() > challenge.expires()) {
			target.sendSystemMessage(Component.translatable("duel.rings.no_challenge").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!accepted) {
			challenger.sendSystemMessage(Component.translatable("duel.rings.declined", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
			target.sendSystemMessage(Component.translatable("duel.rings.you_declined").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		if (DUELS.containsKey(challenger.getUUID()) || DUELS.containsKey(target.getUUID())) {
			target.sendSystemMessage(Component.translatable("duel.rings.busy").withStyle(ChatFormatting.RED));
			return 0;
		}
		Duel duel = new Duel(challenger.getUUID(), target.getUUID(), target.level().getGameTime() + DUEL_TICKS);
		DUELS.put(challenger.getUUID(), duel);
		DUELS.put(target.getUUID(), duel);
		for (ServerPlayer player : new ServerPlayer[] {challenger, target}) {
			player.sendSystemMessage(Component.translatable("duel.rings.begin", challenger.getDisplayName(), target.getDisplayName())
					.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
		}
		return 1;
	}

	/** Ends duels that have run out of time or whose duellists drifted apart. Runs every server tick. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || DUELS.isEmpty()) {
			return;
		}
		for (Duel duel : DUELS.values().stream().distinct().toList()) {
			ServerPlayer a = server.getPlayerList().getPlayer(duel.a());
			ServerPlayer b = server.getPlayerList().getPlayer(duel.b());
			if (a == null || b == null || a.level() != b.level() || a.distanceTo(b) > MAX_APART || a.level().getGameTime() > duel.ends()) {
				end(server, duel.a(), null);
			}
		}
	}

	/** A duellist would die at their opponent's hand: the duel's over instead, and the opponent has won. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer loser)) {
			return true;
		}
		Duel duel = DUELS.get(loser.getUUID());
		Entity attacker = source.getEntity();
		if (duel == null || attacker == null || !duel.other(loser.getUUID()).equals(attacker.getUUID())) {
			return true;
		}
		loser.setHealth(1.0F);
		loser.clearFire();
		end(loser.level().getServer(), loser.getUUID(), attacker.getUUID());
		return false;
	}

	/** Ends the duel the player is in: won by the given player, or with none, drawn. */
	private static void end(MinecraftServer server, UUID player, @Nullable UUID winner) {
		Duel duel = DUELS.remove(player);
		if (duel == null) {
			return;
		}
		DUELS.remove(duel.other(player));
		ServerPlayer a = server.getPlayerList().getPlayer(duel.a());
		ServerPlayer b = server.getPlayerList().getPlayer(duel.b());
		if (winner == null) {
			for (ServerPlayer each : new ServerPlayer[] {a, b}) {
				if (each != null) {
					each.sendSystemMessage(Component.translatable("duel.rings.draw").withStyle(ChatFormatting.GRAY));
				}
			}
			return;
		}
		ServerPlayer won = server.getPlayerList().getPlayer(winner);
		ServerPlayer lost = won == a ? b : a;
		if (won == null || lost == null) {
			return;
		}
		Mastery.addXp(won, WIN_XP);
		Mastery.addXp(lost, LOSE_XP);
		Mastery.first(won, Mastery.First.DUEL);
		ServerLevel level = won.level();
		level.playSound(null, won.getX(), won.getY(), won.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		Component message = Component.translatable("duel.rings.won", won.getDisplayName(), lost.getDisplayName()).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, won.getBoundingBox().inflate(64.0))) {
			near.sendSystemMessage(message);
		}
		if (lost.level() != level || lost.distanceTo(won) > 64.0) {
			lost.sendSystemMessage(message);
		}
	}
}
