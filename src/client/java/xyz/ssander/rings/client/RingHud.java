package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingItem;

/** The power meter: the worn ring, a charge bar and the percentage, to the right of the hotbar. */
public class RingHud implements HudElement {
	private static final int FRAME = 0xFF000000;
	private static final int EMPTY = 0xFF282828;
	private static final int LOW = 0xFFFF5050;
	private static final int TEXT_OFF = 0xFFA0A0A0;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}

		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem ringItem)) {
			return;
		}

		// The ring's colour: bright when on, dim when off
		int on = ARGB.opaque(ringItem.color().ui());
		int off = ARGB.scaleRGB(on, 0.55F);
		int textOn = ARGB.srgbLerp(0.55F, on, 0xFFFFFFFF);

		float charge = (float) RingItem.charge(ring) / RingItem.MAX_CHARGE;
		boolean active = player.getAttachedOrElse(RingAttachments.ACTIVE, false);
		boolean low = charge < 0.15F;

		// Right of the hotbar, or past the offhand slot when a left-handed player's offhand sits there
		int x = graphics.guiWidth() / 2 + 91 + 6;
		if (player.getMainArm() == HumanoidArm.LEFT && !player.getOffhandItem().isEmpty()) {
			x += 29;
		}
		int y = graphics.guiHeight() - 19;

		graphics.item(ring, x, y);

		// Vertical bar, filling from the bottom. Blinks when low and switched on.
		int barX = x + 18;
		graphics.fill(barX, y, barX + 5, y + 16, FRAME);
		graphics.fill(barX + 1, y + 1, barX + 4, y + 15, EMPTY);
		int filled = Math.round(14 * charge);
		boolean blinkOff = low && active && (player.tickCount / 5) % 2 == 0;
		if (filled > 0 && !blinkOff) {
			graphics.fill(barX + 1, y + 15 - filled, barX + 4, y + 15, low ? LOW : active ? on : off);
		}

		graphics.text(minecraft.font, Math.round(charge * 100) + "%", barX + 8, y + 4, low ? LOW : active ? textOn : TEXT_OFF, true);
	}
}
