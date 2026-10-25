package xyz.ssander.rings.client;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import xyz.ssander.rings.ColorPowers;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.network.TeleportToPayload;

/** The pink ring's other power: pick a friend who's online and go straight to them, wherever they are. */
public class FriendScreen extends Screen {
	private static final int BUTTON_WIDTH = 160;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MAX_SHOWN = 10;

	public FriendScreen() {
		super(Component.translatable("screen.rings.friends"));
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		List<PlayerInfo> friends = minecraft.getConnection() == null ? List.of() : minecraft.getConnection().getOnlinePlayers().stream()
				.filter(info -> minecraft.player == null || !info.getProfile().id().equals(minecraft.player.getUUID()))
				.limit(MAX_SHOWN)
				.toList();
		int top = this.height / 2 - friends.size() * (BUTTON_HEIGHT + 4) / 2;
		for (int i = 0; i < friends.size(); i++) {
			PlayerInfo friend = friends.get(i);
			this.addRenderableWidget(Button.builder(Component.literal(friend.getProfile().name()), button -> {
				ClientPlayNetworking.send(new TeleportToPayload(friend.getProfile().id()));
				this.onClose();
			}).bounds(this.width / 2 - BUTTON_WIDTH / 2, top + i * (BUTTON_HEIGHT + 4), BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int pink = ARGB.opaque(RingColor.PINK.ui());
		graphics.centeredText(this.font, this.title, this.width / 2, 20, pink);
		Component cost = Component.translatable("screen.rings.construct_cost", String.format("%.1f", 100.0F * ColorPowers.TELEPORT_COST / RingItem.MAX_CHARGE));
		graphics.centeredText(this.font, cost, this.width / 2, 34, 0xFFC0C0C0);
		if (this.children().isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("screen.rings.no_friends"), this.width / 2, this.height / 2, 0xFFE0E0E0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
