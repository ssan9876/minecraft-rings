package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;

/**
 * Players' own force fields: a sphere of the ring's energy round each player who has theirs up, and for your own, a
 * tint of it round the edge of the screen in first person (from inside, the sphere itself doesn't show).
 */
public final class ForceFields implements HudElement {
	private static final float RADIUS = 1.3F;

	/** Submits every player's force field. Runs as the level is drawn. */
	public static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (Player player : level.players()) {
			RingColor color = shieldColor(player);
			if (color == null || player == minecraft.getCameraEntity() && camera.isFirstPerson) {
				continue;
			}
			Vec3 centre = player.getPosition(partialTick).add(0.0, player.getBbHeight() / 2.0, 0.0).subtract(camera.pos);
			EnergySphere.submit(context.submitNodeCollector(), context.poseStack(), centre, RADIUS, Vec3.ZERO, color,
					player.tickCount + partialTick, 0.9F);
		}
	}

	private static RingColor shieldColor(Player player) {
		if (!player.getAttachedOrElse(RingAttachments.SHIELD, false)) {
			return null;
		}
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		return costume != null ? costume.color() : RingColor.GREEN;
	}

	/** Your own force field, from inside: the screen's edges tinted with it. */
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		RingColor color = player == null ? null : shieldColor(player);
		if (color == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int edge = Math.max(12, height / 10);
		int rgb = color == RingColor.BLACK ? 0x000000 : color.energy();
		int strong = ARGB.color(color == RingColor.BLACK ? 110 : 70, rgb);
		int clear = ARGB.color(0, rgb);
		graphics.fillGradient(0, 0, width, edge, strong, clear);
		graphics.fillGradient(0, height - edge, width, height, clear, strong);
	}
}
