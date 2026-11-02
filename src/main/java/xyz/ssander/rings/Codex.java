package xyz.ssander.rings;

import java.util.List;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Lantern Codex: everything a ring-bearer can find, by page, filled in as they find it (the codex button on the
 * skill screen shows it). Each page done earns ring experience. What's been found is kept with the player's mastery,
 * as {@code codex:<entry>}.
 */
public final class Codex {
	/** A page of the codex: its entries, and the ring experience for finding them all. */
	public record Page(String id, List<String> entries, int xp) {
		public String translationKey() {
			return "codex.rings.page." + this.id;
		}
	}

	public static final List<Page> PAGES = List.of(
			new Page("worlds", List.of("space", "moon", "mars", "oa", "korugar"), 300),
			new Page("foes", List.of("manhunter", "parallax", "nekron", "devourer"), 500),
			new Page("wonders", List.of("pilot", "battery", "fear_battery", "shrine", "wreck", "fortress"), 300),
			new Page("rings", List.of("ring_green", "ring_yellow", "ring_red", "ring_blue", "ring_orange", "ring_pink", "ring_white", "ring_black"), 400),
			new Page("corps", List.of("guardian", "mission", "bounty", "duel", "corps"), 200));

	private static final String PREFIX = "codex:";

	private Codex() {
	}

	static void init() {
		// Opening a ruin's chest for the first time finds the ruin
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				BlockEntity entity = level.getBlockEntity(hit.getBlockPos());
				if (entity instanceof RandomizableContainer container && container.getLootTable() != null) {
					if (container.getLootTable().equals(PlanetRuins.SHRINE_LOOT)) {
						discover(serverPlayer, "shrine");
					} else if (container.getLootTable().equals(PlanetRuins.WRECK_LOOT)) {
						discover(serverPlayer, "wreck");
					} else if (container.getLootTable().equals(KorugarFortress.LOOT)) {
						discover(serverPlayer, "fortress");
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean found(Mastery.Data data, String entry) {
		return data.firsts().contains(PREFIX + entry);
	}

	public static String entryKey(String entry) {
		return "codex.rings." + entry;
	}

	/** The player found something: into the codex it goes (once), and a page done pays out. */
	public static void discover(ServerPlayer player, String entry) {
		Mastery.Data data = Mastery.of(player);
		if (found(data, entry)) {
			return;
		}
		Mastery.store(player, data.withFirst(PREFIX + entry));
		player.sendSystemMessage(Component.translatable("codex.rings.found", Component.translatable(entryKey(entry))).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		Mastery.Data now = Mastery.of(player);
		for (Page page : PAGES) {
			if (page.entries().contains(entry) && page.entries().stream().allMatch(each -> found(now, each))) {
				player.sendSystemMessage(Component.translatable("codex.rings.page_done", Component.translatable(page.translationKey()), page.xp())
						.withStyle(ChatFormatting.GOLD));
				Mastery.addXp(player, page.xp());
			}
		}
	}
}
