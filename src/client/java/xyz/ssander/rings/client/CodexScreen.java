package xyz.ssander.rings.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import xyz.ssander.rings.Codex;
import xyz.ssander.rings.Mastery;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;

/**
 * The Lantern Codex: a page for each kind of thing a ring-bearer can find, tabs along the top, each entry named once
 * found and hidden until then, and how far through the page they are. A page done has earned its ring experience.
 */
public class CodexScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 212;

	private final Screen parent;
	private int page;

	public CodexScreen(Screen parent) {
		super(Component.translatable("screen.rings.codex"));
		this.parent = parent;
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	private int top() {
		return (this.height - HEIGHT) / 2;
	}

	@Override
	protected void init() {
		int tabWidth = (WIDTH - 8) / Codex.PAGES.size();
		for (int i = 0; i < Codex.PAGES.size(); i++) {
			int index = i;
			this.addRenderableWidget(Button.builder(Component.translatable(Codex.PAGES.get(i).translationKey()), button -> this.page = index)
					.bounds(this.left() + 4 + i * tabWidth, this.top() + 18, tabWidth - 2, 18).build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> this.onClose())
				.bounds(this.left() + WIDTH - 64, this.top() + HEIGHT - 24, 60, 18).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private RingColor color() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && RingAttachments.getRing(minecraft.player).getItem() instanceof RingItem ring ? ring.color() : RingColor.GREEN;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = this.left();
		int top = this.top();
		int energy = ARGB.opaque(this.color().ui());
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xE00A0A12);
		graphics.outline(left, top, WIDTH, HEIGHT, energy);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, this.title, left + 6, top + 6, ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF), true);

		Minecraft minecraft = Minecraft.getInstance();
		Mastery.Data data = minecraft.player != null ? Mastery.of(minecraft.player) : Mastery.Data.NEW;
		Codex.Page page = Codex.PAGES.get(this.page);
		int found = 0;
		int y = top + 44;
		for (String entry : page.entries()) {
			boolean known = Codex.found(data, entry);
			if (known) {
				found++;
			}
			Component name = known ? Component.translatable(Codex.entryKey(entry)) : Component.translatable("codex.rings.unknown");
			graphics.text(this.font, Component.literal(known ? "✔ " : "· ").append(name), left + 12, y, known ? 0xFFE8E8E8 : 0xFF707070, false);
			if (known) {
				graphics.text(this.font, Component.translatable(Codex.entryKey(entry) + ".desc").withStyle(ChatFormatting.GRAY),
						left + 130, y, 0xFFA0A0A0, false);
			}
			y += 14;
		}
		boolean done = found == page.entries().size();
		Component progress = done ? Component.translatable("codex.rings.page_complete", page.xp())
				: Component.translatable("codex.rings.page_progress", found, page.entries().size(), page.xp());
		graphics.textWithWordWrap(this.font, progress, left + 6, top + HEIGHT - 36, WIDTH - 76, done ? 0xFFFFD060 : 0xFFB0B0B0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
