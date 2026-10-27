package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.SuitStyle;
import xyz.ssander.rings.network.ChooseSuitPayload;

/**
 * Pick which suit your rings dress you in. The player stands beside the list, so with the ring on you see each suit
 * as you pick it; the choice is kept, and every ring you wear after uses it.
 */
public class SuitScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PREVIEW_WIDTH = 90;
	private static final int PREVIEW_HEIGHT = 120;

	public SuitScreen() {
		super(Component.translatable("screen.rings.suits"));
	}

	@Override
	protected void init() {
		SuitStyle[] styles = SuitStyle.values();
		int left = this.width / 2 - (PREVIEW_WIDTH + BUTTON_WIDTH + 12) / 2 + PREVIEW_WIDTH + 12;
		int top = this.height / 2 - styles.length * (BUTTON_HEIGHT + 4) / 2;
		for (int i = 0; i < styles.length; i++) {
			SuitStyle style = styles[i];
			this.addRenderableWidget(Button.builder(Component.translatable(style.translationKey()), button -> {
				ClientPlayNetworking.send(new ChooseSuitPayload(style));
			}).bounds(left, top + i * (BUTTON_HEIGHT + 4), BUTTON_WIDTH, BUTTON_HEIGHT).build());
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
		SuitStyle[] styles = SuitStyle.values();
		int top = this.height / 2 - styles.length * (BUTTON_HEIGHT + 4) / 2;
		graphics.centeredText(this.font, this.title, this.width / 2, top - 30, bright);
		graphics.centeredText(this.font, Component.translatable("screen.rings.suits_note"), this.width / 2, top - 17, 0xFFC0C0C0);

		// The chosen one, marked beside its button
		if (minecraft.player != null) {
			SuitStyle chosen = RingAttachments.suit(minecraft.player);
			int left = this.width / 2 - (PREVIEW_WIDTH + BUTTON_WIDTH + 12) / 2 + PREVIEW_WIDTH + 12;
			int y = top + chosen.ordinal() * (BUTTON_HEIGHT + 4);
			graphics.outline(left - 2, y - 2, BUTTON_WIDTH + 4, BUTTON_HEIGHT + 4, bright);

			int x0 = this.width / 2 - (PREVIEW_WIDTH + BUTTON_WIDTH + 12) / 2;
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
