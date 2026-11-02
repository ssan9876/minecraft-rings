package xyz.ssander.rings;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /rings}, for operators: set a player's ring level, give them ring experience, unlock the whole tree or start
 * them over, and bring a ship down (falling from the sky near you, or built right where you stand).
 */
public final class RingCommands {
	private RingCommands() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("rings")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("level").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("level", IntegerArgumentType.integer(1, Mastery.MAX_LEVEL))
								.executes(context -> each(context, player -> Mastery.setLevel(player, IntegerArgumentType.getInteger(context, "level")),
										"command.rings.level")))))
				.then(Commands.literal("xp").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("amount", IntegerArgumentType.integer(1))
								.executes(context -> each(context, player -> Mastery.addXp(player, IntegerArgumentType.getInteger(context, "amount")),
										"command.rings.xp")))))
				.then(Commands.literal("unlockall").then(Commands.argument("targets", EntityArgument.players())
						.executes(context -> each(context, Mastery::unlockAll, "command.rings.unlockall"))))
				.then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players())
						.executes(context -> each(context, Mastery::reset, "command.rings.reset"))))
				.then(Commands.literal("info").then(Commands.argument("target", EntityArgument.player())
						.executes(context -> info(context, EntityArgument.getPlayer(context, "target")))))
				.then(Commands.literal("crash")
						.executes(context -> crash(context, null, false))
						.then(Commands.argument("color", StringArgumentType.word()).suggests(RingCommands::colors)
								.executes(context -> crash(context, StringArgumentType.getString(context, "color"), false))))
				.then(Commands.literal("crashsite")
						.executes(context -> crash(context, null, true))
						.then(Commands.argument("color", StringArgumentType.word()).suggests(RingCommands::colors)
								.executes(context -> crash(context, StringArgumentType.getString(context, "color"), true)))));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> colors(
			CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(java.util.Arrays.stream(RingColor.values()).map(RingColor::getSerializedName), builder);
	}

	private static int each(CommandContext<CommandSourceStack> context, Consumer<ServerPlayer> action, String message) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
		for (ServerPlayer player : players) {
			action.accept(player);
			context.getSource().sendSuccess(() -> Component.translatable(message, player.getDisplayName(), Mastery.level(player)), true);
		}
		return players.size();
	}

	private static int info(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		Mastery.Data data = Mastery.of(player);
		context.getSource().sendSuccess(() -> Component.translatable("command.rings.info", player.getDisplayName(), data.level(), data.xp(),
				data.points(), String.join(", ", data.skills().stream().map(Skill::id).toList())), false);
		return data.level();
	}

	private static int crash(CommandContext<CommandSourceStack> context, String colorName, boolean here) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		RingColor color = CrashSite.pickColor(level.getRandom());
		if (colorName != null) {
			color = null;
			for (RingColor each : RingColor.values()) {
				if (each.getSerializedName().equals(colorName)) {
					color = each;
				}
			}
			if (color == null) {
				context.getSource().sendFailure(Component.translatable("command.rings.no_color", colorName));
				return 0;
			}
		}
		if (here) {
			BlockPos pilot = CrashSite.build(level, player.blockPosition(), level.getRandom(), color, Heightmap.Types.WORLD_SURFACE, Block.UPDATE_ALL, false);
			if (pilot == null) {
				context.getSource().sendFailure(Component.translatable("command.rings.crash_failed"));
				return 0;
			}
			// Out of the wreck, which was built where they stood
			player.teleportTo(player.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()) + 1.0, player.getZ());
			context.getSource().sendSuccess(() -> Component.translatable("command.rings.crashsite", pilot.toShortString()), true);
			return 1;
		}
		Crashes.forget(level.getServer());
		BlockPos at = Crashes.fall(level, player, color, 40.0, 70.0);
		context.getSource().sendSuccess(() -> Component.translatable("command.rings.crash", at.toShortString()), true);
		return 1;
	}
}
