package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingStyles;
import xyz.ssander.rings.SuitStyle;
import xyz.ssander.rings.network.ChooseSuitPayload;

/**
 * Pick which suit your rings dress you in. The player stands beside the list, so with the ring on you see each suit
 * as you pick it; the choice is kept, and every ring you wear after uses it. The first suits are everyone's; the rest
 * are earned (by the ring's level, or the pilot's uniform by taking a ring from a dying bearer), and show what they
 * need until then.
 */
public class SuitScreen extends Screen {
	private static final int BUTTON_WIDTH = 124;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int COLUMNS = 2;
	private static final int PREVIEW_WIDTH = 90;
	private static final int PREVIEW_HEIGHT = 120;

	public SuitScreen() {
		super(Component.translatable("screen.rings.suits"));
	}

	private static int rows() {
		return (SuitStyle.values().length + COLUMNS - 1) / COLUMNS;
	}

	private int listLeft() {
		int total = PREVIEW_WIDTH + 12 + COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
		return this.width / 2 - total / 2 + PREVIEW_WIDTH + 12;
	}

	private int listTop() {
		return this.height / 2 - rows() * (BUTTON_HEIGHT + GAP) / 2;
	}

	private int buttonX(int index) {
		return this.listLeft() + (index / rows()) * (BUTTON_WIDTH + GAP);
	}

	private int buttonY(int index) {
		return this.listTop() + (index % rows()) * (BUTTON_HEIGHT + GAP);
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		SuitStyle[] styles = SuitStyle.values();
		for (int i = 0; i < styles.length; i++) {
			SuitStyle style = styles[i];
			boolean unlocked = minecraft.player != null && style.unlocked(minecraft.player);
			Component label = unlocked ? Component.translatable(style.translationKey())
					: Component.translatable(style.translationKey()).withStyle(ChatFormatting.DARK_GRAY);
			Button button = Button.builder(label, b -> ClientPlayNetworking.send(new ChooseSuitPayload(style)))
					.bounds(this.buttonX(i), this.buttonY(i), BUTTON_WIDTH, BUTTON_HEIGHT).build();
			if (!unlocked) {
				button.active = false;
				button.setTooltip(Tooltip.create(style.lockedReason()));
			}
			this.addRenderableWidget(button);
		}

		// How the ring's glow and constructs look (sent as /ring commands, which check what the player has earned)
		if (minecraft.player != null) {
			int y = this.listTop() + rows() * (BUTTON_HEIGHT + GAP) + 6;
			RingStyles.Glow glow = RingStyles.glow(minecraft.player);
			RingStyles.Glow nextGlow = RingStyles.Glow.values()[(glow.ordinal() + 1) % RingStyles.Glow.values().length];
			this.addRenderableWidget(Button.builder(Component.translatable("screen.rings.glow", Component.translatable("style.rings.glow." + glow.getSerializedName())),
					b -> {
						minecraft.player.connection.sendCommand("ring glow " + nextGlow.getSerializedName());
						this.rebuildLater();
					}).bounds(this.listLeft(), y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("screen.rings.glow_tip"))).build());
			RingStyles.ConstructStyle style = RingStyles.constructs(minecraft.player);
			RingStyles.ConstructStyle nextStyle = RingStyles.ConstructStyle.values()[(style.ordinal() + 1) % RingStyles.ConstructStyle.values().length];
			this.addRenderableWidget(Button.builder(Component.translatable("screen.rings.constructs",
							Component.translatable("style.rings.constructs." + style.getSerializedName())),
					b -> {
						minecraft.player.connection.sendCommand("ring constructs " + nextStyle.getSerializedName());
						this.rebuildLater();
					}).bounds(this.listLeft() + BUTTON_WIDTH + GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("screen.rings.constructs_tip"))).build());
			RingStyles.Emblem emblem = RingStyles.emblem(minecraft.player);
			RingStyles.Emblem nextEmblem = RingStyles.Emblem.values()[(emblem.ordinal() + 1) % RingStyles.Emblem.values().length];
			this.addRenderableWidget(Button.builder(Component.translatable("screen.rings.emblem",
							Component.translatable("style.rings.emblem." + emblem.getSerializedName())),
					b -> {
						minecraft.player.connection.sendCommand("ring emblem " + nextEmblem.getSerializedName());
						this.rebuildLater();
					}).bounds(this.listLeft(), y + BUTTON_HEIGHT + GAP, BUTTON_WIDTH * 2 + GAP, BUTTON_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("screen.rings.emblem_tip"))).build());
		}
	}

	/** Redraws the buttons once the server has had a moment to change the style. */
	private int rebuildIn = -1;

	private void rebuildLater() {
		this.rebuildIn = 4;
	}

	@Override
	public void tick() {
		if (this.rebuildIn > 0 && --this.rebuildIn == 0) {
			this.rebuildWidgets();
		}
	}

	private RingColor color() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && RingAttachments.getRing(minecraft.player).getItem() instanceof RingItem ring ? ring.color() : RingColor.GREEN;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Minecraft minecraft = Minecraft.getInstance();
		int energy = ARGB.opaque(this.color().ui());
		int bright = ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF);
		int top = this.listTop();
		graphics.centeredText(this.font, this.title, this.width / 2, top - 30, bright);
		graphics.centeredText(this.font, Component.translatable("screen.rings.suits_note"), this.width / 2, top - 17, 0xFFC0C0C0);

		if (minecraft.player != null) {
			// The chosen one, marked round its button
			SuitStyle chosen = RingAttachments.suit(minecraft.player);
			int index = chosen.ordinal();
			graphics.outline(this.buttonX(index) - 2, this.buttonY(index) - 2, BUTTON_WIDTH + 4, BUTTON_HEIGHT + 4, bright);

			int x0 = this.listLeft() - 12 - PREVIEW_WIDTH;
			int y0 = this.height / 2 - PREVIEW_HEIGHT / 2;
			graphics.fill(x0, y0, x0 + PREVIEW_WIDTH, y0 + PREVIEW_HEIGHT, 0x80101010);
			graphics.outline(x0, y0, PREVIEW_WIDTH, PREVIEW_HEIGHT, ARGB.scaleRGB(energy, 0.6F));
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0 + 1, y0 + 1, x0 + PREVIEW_WIDTH - 1, y0 + PREVIEW_HEIGHT - 1,
					48, 0.0625F, mouseX, mouseY, minecraft.player);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
