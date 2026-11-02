package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.OpenDialoguePayload;

/**
 * The Guardians' missions: ask a Guardian on Oa for work and they set a task (bring moonstone from the Moon, crystals
 * from Mars or will shards from Oa's own rock, or hunt Manhunters or one of the great foes), and reward it with ring
 * experience, sometimes an Oan relic. One at a time; a player takes the next once they've turned the last one in.
 */
public final class Missions {
	public enum Kind {
		BRING,
		HUNT
	}

	public enum Mission implements StringRepresentable {
		MOONSTONE(Kind.BRING, 16, 250, 0.3F, 1, () -> PlanetBlocks.MOONSTONE, null),
		MARS_CRYSTALS(Kind.BRING, 12, 250, 0.3F, 1, () -> PlanetBlocks.MARS_CRYSTAL.asItem(), null),
		WILL_SHARDS(Kind.BRING, 8, 150, 0.15F, 1, () -> PlanetBlocks.WILL_SHARD, null),
		MANHUNTERS(Kind.HUNT, 5, 300, 0.4F, 1, null, entity -> entity instanceof Manhunter),
		NEKRON(Kind.HUNT, 1, 500, 1.0F, 15, null, entity -> entity instanceof Nekron),
		PARALLAX(Kind.HUNT, 1, 500, 1.0F, 15, null, entity -> entity instanceof Parallax),
		DEVOURER(Kind.HUNT, 1, 700, 1.0F, 20, null, entity -> entity instanceof Devourer);

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

		public Component description() {
			return Component.translatable("mission.rings." + this.getSerializedName(), this.count);
		}

		public Component reward() {
			Component relic = this.relicChance >= 1.0F ? Component.translatable("mission.rings.reward_relic")
					: this.relicChance > 0.0F ? Component.translatable("mission.rings.reward_maybe_relic") : Component.empty();
			return Component.translatable("mission.rings.reward", this.xp, relic);
		}
	}

	/** The player's mission, and how far into it they are (creatures hunted; what's brought is counted at hand-in). */
	public record Active(Mission mission, int progress) {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Mission.CODEC.fieldOf("mission").forGetter(Active::mission),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(Active::progress)
		).apply(instance, Active::new));
	}

	public static final AttachmentType<Active> MISSION = AttachmentRegistry.create(Rings.id("mission"),
			builder -> builder.persistent(Active.CODEC).copyOnDeath());

	private Missions() {
	}

	static void init() {
		// Loads the class so the attachment registers during mod init
	}

	public static @Nullable Active of(ServerPlayer player) {
		return player.getAttached(MISSION);
	}

	/** How far into it the player is: hunted so far, or how many of the things they carry. */
	private static int progress(ServerPlayer player, Active active) {
		Mission mission = active.mission();
		return mission.kind == Kind.HUNT ? active.progress() : count(player, mission.item.get());
	}

	private static Component progressText(ServerPlayer player, Active active) {
		return Component.translatable("mission.rings.progress", Math.min(progress(player, active), active.mission().count), active.mission().count);
	}

	/** The player asked a Guardian for work: their current task, or a new one. */
	static void offer(ServerPlayer player, OaGuardian guardian) {
		Active active = of(player);
		if (active != null) {
			reply(player, guardian, "mission_active", active.mission().description(), progressText(player, active));
			return;
		}
		int level = Mastery.level(player);
		List<Mission> open = new ArrayList<>();
		for (Mission mission : Mission.values()) {
			if (level >= mission.minLevel) {
				open.add(mission);
			}
		}
		Mission mission = open.get(player.getRandom().nextInt(open.size()));
		player.setAttached(MISSION, new Active(mission, 0));
		reply(player, guardian, "mission_new", mission.description(), mission.reward());
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
			reply(player, guardian, "mission_not_done", mission.description(), progressText(player, active));
			return;
		}
		if (mission.kind == Kind.BRING) {
			take(player, mission.item.get(), mission.count);
		}
		player.removeAttached(MISSION);
		boolean relic = player.getRandom().nextFloat() < mission.relicChance;
		if (relic) {
			ItemStack stack = new ItemStack(Rings.OAN_RELIC);
			if (!player.getInventory().add(stack)) {
				player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
			}
		}
		Mastery.addXp(player, mission.xp);
		Mastery.first(player, Mastery.First.MISSION);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		reply(player, guardian, "mission_done", Component.literal(String.valueOf(mission.xp)),
				relic ? Component.translatable("mission.rings.reward_relic") : Component.empty());
	}

	/** Something died at the player's hand: it may be what they were sent to hunt. */
	static void killed(ServerPlayer player, Entity killed) {
		Active active = of(player);
		if (active == null || active.mission().kind != Kind.HUNT || !active.mission().quarry.test(killed)) {
			return;
		}
		Active next = new Active(active.mission(), active.progress() + 1);
		player.setAttached(MISSION, next);
		player.sendOverlayMessage(Component.translatable("mission.rings.hunted", active.mission().description(),
				Math.min(next.progress(), next.mission().count), next.mission().count).withStyle(ChatFormatting.GOLD));
	}

	private static int count(ServerPlayer player, Item item) {
		return player.getInventory().countItem(item);
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
