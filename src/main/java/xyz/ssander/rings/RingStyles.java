package xyz.ssander.rings;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Making the ring one's own: an engraving on it (from ring level 5), how the wearer's outline glows (steady, or from
 * level 10 pulsing, or from level 20 not at all), and how their hard-light constructs look (solid, a bright frame, or a
 * faint ghost, once they've learned Shaper). {@code /ring engrave|glow|constructs}, or the buttons on the suit screen.
 */
public final class RingStyles {
	public static final int ENGRAVE_LEVEL = 5;
	public static final int MAX_ENGRAVING = 32;

	public enum Glow implements StringRepresentable {
		STEADY(1),
		PULSE(10),
		OFF(20);

		public static final StringRepresentable.EnumCodec<Glow> CODEC = StringRepresentable.fromEnum(Glow::values);
		public static final StreamCodec<ByteBuf, Glow> STREAM_CODEC = ByteBufCodecs.idMapper(
				ByIdMap.continuous(Glow::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Glow::ordinal);

		private final int level;

		Glow(int level) {
			this.level = level;
		}

		public int level() {
			return this.level;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** The emblem on the wearer's suit: the lantern every suit has, or (from ring level 15) another. */
	public enum Emblem implements StringRepresentable {
		LANTERN,
		STAR,
		FLAME,
		HEART,
		EYE,
		DROP,
		SKULL,
		CROWN,
		BOLT;

		public static final int LEVEL = 15;
		public static final StringRepresentable.EnumCodec<Emblem> CODEC = StringRepresentable.fromEnum(Emblem::values);
		public static final StreamCodec<ByteBuf, Emblem> STREAM_CODEC = ByteBufCodecs.idMapper(
				ByIdMap.continuous(Emblem::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Emblem::ordinal);

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public enum ConstructStyle implements StringRepresentable {
		SOLID,
		FRAME,
		GHOST;

		public static final StringRepresentable.EnumCodec<ConstructStyle> CODEC = StringRepresentable.fromEnum(ConstructStyle::values);
		public static final StreamCodec<ByteBuf, ConstructStyle> STREAM_CODEC = ByteBufCodecs.idMapper(
				ByIdMap.continuous(ConstructStyle::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), ConstructStyle::ordinal);

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** How the wearer's outline glows. Synced to every client. */
	public static final AttachmentType<Glow> GLOW = AttachmentRegistry.create(Rings.id("glow_style"),
			builder -> builder.persistent(Glow.CODEC).copyOnDeath().syncWith(Glow.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** The emblem on the wearer's suit. Synced to every client. */
	public static final AttachmentType<Emblem> EMBLEM = AttachmentRegistry.create(Rings.id("emblem"),
			builder -> builder.persistent(Emblem.CODEC).copyOnDeath().syncWith(Emblem.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** How the player's hard light looks. Synced to them, for the suit screen. */
	public static final AttachmentType<ConstructStyle> CONSTRUCTS = AttachmentRegistry.create(Rings.id("construct_style"),
			builder -> builder.persistent(ConstructStyle.CODEC).copyOnDeath().syncWith(ConstructStyle.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private RingStyles() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> register(dispatcher));
	}

	public static Glow glow(Player player) {
		return player.getAttachedOrElse(GLOW, Glow.STEADY);
	}

	public static Emblem emblem(Player player) {
		return player.getAttachedOrElse(EMBLEM, Emblem.LANTERN);
	}

	public static ConstructStyle constructs(Player player) {
		return player.getAttachedOrElse(CONSTRUCTS, ConstructStyle.SOLID);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ring")
				.then(Commands.literal("engrave")
						.executes(context -> engrave(context.getSource().getPlayerOrException(), ""))
						.then(Commands.argument("text", StringArgumentType.greedyString())
								.executes(context -> engrave(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "text")))))
				.then(Commands.literal("glow").then(Commands.argument("style", StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								java.util.Arrays.stream(Glow.values()).map(Glow::getSerializedName), builder))
						.executes(context -> glow(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "style")))))
				.then(Commands.literal("emblem").then(Commands.argument("emblem", StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								java.util.Arrays.stream(Emblem.values()).map(Emblem::getSerializedName), builder))
						.executes(context -> emblem(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "emblem")))))
				.then(Commands.literal("constructs").then(Commands.argument("style", StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								java.util.Arrays.stream(ConstructStyle.values()).map(ConstructStyle::getSerializedName), builder))
						.executes(context -> constructs(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "style"))))));
	}

	private static int fail(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
		return 0;
	}

	private static int engrave(ServerPlayer player, String text) {
		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem)) {
			return fail(player, "style.rings.no_ring");
		}
		if (Mastery.level(player) < ENGRAVE_LEVEL) {
			return fail(player, "style.rings.needs_level", ENGRAVE_LEVEL);
		}
		if (text.length() > MAX_ENGRAVING) {
			return fail(player, "style.rings.too_long", MAX_ENGRAVING);
		}
		if (text.isBlank()) {
			ring.remove(RingComponents.ENGRAVING);
			player.sendSystemMessage(Component.translatable("style.rings.unengraved", ring.getHoverName()).withStyle(ChatFormatting.GOLD));
		} else {
			ring.set(RingComponents.ENGRAVING, text);
			player.sendSystemMessage(Component.translatable("style.rings.engraved", ring.getHoverName(), text).withStyle(ChatFormatting.GOLD));
		}
		return 1;
	}

	private static int glow(ServerPlayer player, String name) {
		for (Glow glow : Glow.values()) {
			if (glow.getSerializedName().equals(name)) {
				if (Mastery.level(player) < glow.level()) {
					return fail(player, "style.rings.needs_level", glow.level());
				}
				player.setAttached(GLOW, glow == Glow.STEADY ? null : glow);
				player.sendOverlayMessage(Component.translatable("style.rings.glow_set", Component.translatable("style.rings.glow." + name)));
				return 1;
			}
		}
		return fail(player, "style.rings.unknown", name);
	}

	private static int emblem(ServerPlayer player, String name) {
		for (Emblem emblem : Emblem.values()) {
			if (emblem.getSerializedName().equals(name)) {
				if (emblem != Emblem.LANTERN && Mastery.level(player) < Emblem.LEVEL) {
					return fail(player, "style.rings.needs_level", Emblem.LEVEL);
				}
				player.setAttached(EMBLEM, emblem == Emblem.LANTERN ? null : emblem);
				player.sendOverlayMessage(Component.translatable("style.rings.emblem_set", Component.translatable("style.rings.emblem." + name)));
				return 1;
			}
		}
		return fail(player, "style.rings.unknown", name);
	}

	private static int constructs(ServerPlayer player, String name) {
		for (ConstructStyle style : ConstructStyle.values()) {
			if (style.getSerializedName().equals(name)) {
				if (style != ConstructStyle.SOLID && !Mastery.has(player, Skill.SHAPER)) {
					return fail(player, "message.rings.needs_skill", Component.translatable(Skill.SHAPER.translationKey()));
				}
				player.setAttached(CONSTRUCTS, style == ConstructStyle.SOLID ? null : style);
				player.sendOverlayMessage(Component.translatable("style.rings.constructs_set", Component.translatable("style.rings.constructs." + name)));
				return 1;
			}
		}
		return fail(player, "style.rings.unknown", name);
	}
}
