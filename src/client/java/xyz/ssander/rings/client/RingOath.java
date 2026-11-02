package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;

/**
 * The lantern oath, spoken while charging: its four lines appear one by one across the screen, in the colour of the
 * lantern, while the local player holds their ring to it. Each colour has its own oath (in the language file, as
 * {@code oath.rings.<colour>.1} to {@code .4}). A full ring shows the whole oath, which lingers and then fades.
 */
public final class RingOath implements HudElement {
	private static final int LINES = 4;
	/** Ticks between lines (four lines over roughly the six seconds a charge takes), each fading in over the second. */
	private static final int LINE_TICKS = 28;
	private static final int FADE_IN = 8;
	/** After the last top-up, the oath stays this long and then fades out over the next. */
	private static final int LINGER = 30;
	private static final int FADE_OUT = 25;
	/** A gap longer than this between top-ups starts the oath over. */
	private static final int RESTART_GAP = 40;
	private static final float SCALE = 1.5F;

	private static RingColor color = RingColor.GREEN;
	private static int start = Integer.MIN_VALUE;
	private static int last = Integer.MIN_VALUE;
	private static boolean full;

	/** The local player topped up their ring at a lantern of this colour (or is holding it out to one). */
	static void charging(Player player, RingColor lantern) {
		int now = player.tickCount;
		if (start == Integer.MIN_VALUE || now - last > RESTART_GAP || lantern != color) {
			start = now;
			full = false;
		}
		last = now;
		color = lantern;
	}

	/** The local player's ring is full: the whole oath shows. */
	static void full(Player player, RingColor lantern) {
		charging(player, lantern);
		full = true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || start == Integer.MIN_VALUE) {
			return;
		}
		float now = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false);
		float sinceLast = now - last;
		if (sinceLast > LINGER + FADE_OUT || now < start) {
			start = Integer.MIN_VALUE;
			return;
		}
		float fadeOut = 1.0F - Mth.clamp((sinceLast - LINGER) / FADE_OUT, 0.0F, 1.0F);

		int textColor = ARGB.srgbLerp(0.35F, ARGB.opaque(color.ui()), 0xFFFFFFFF);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() * 0.22F);
		pose.scale(SCALE, SCALE);
		for (int line = 0; line < LINES; line++) {
			float shown = full ? 1.0F : Mth.clamp((now - start - line * LINE_TICKS) / FADE_IN, 0.0F, 1.0F);
			float alpha = shown * fadeOut;
			if (alpha < 0.05F) {
				continue;
			}
			Component text = Component.translatable("oath.rings." + color.id() + "." + (line + 1));
			int x = -minecraft.font.width(text) / 2;
			graphics.text(minecraft.font, text, x, line * 11, ARGB.color(Math.round(alpha * 255), textColor), true);
		}
		pose.popMatrix();

		// How full the ring is, filling as the oath goes on
		ItemStack ring = RingAttachments.getRing(player).getItem() instanceof RingItem ? RingAttachments.getRing(player) : player.getMainHandItem();
		if (ring.getItem() instanceof RingItem && fadeOut > 0.05F) {
			float charge = (float) RingItem.charge(ring) / RingItem.MAX_CHARGE;
			int width = 120;
			int x0 = graphics.guiWidth() / 2 - width / 2;
			int y0 = Math.round(graphics.guiHeight() * 0.22F + LINES * 11 * SCALE + 8);
			int alpha = Math.round(fadeOut * 255);
			graphics.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + 5, ARGB.color(Math.round(alpha * 0.7F), 0x000000));
			graphics.fill(x0, y0, x0 + Math.round(width * charge), y0 + 4, ARGB.color(alpha, ARGB.opaque(color.ui())));
			Component percent = Component.literal(Math.round(charge * 100) + "%");
			graphics.text(minecraft.font, percent, graphics.guiWidth() / 2 - minecraft.font.width(percent) / 2, y0 + 8,
					ARGB.color(alpha, textColor), true);
		}
	}
}
