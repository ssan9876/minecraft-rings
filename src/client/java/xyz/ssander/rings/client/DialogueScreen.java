package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.CrashPilot;
import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.ShipBlocks;
import xyz.ssander.rings.network.OpenDialoguePayload;
import xyz.ssander.rings.network.DialogueActionPayload;

/**
 * Talking to a crash site: the dying pilot, or the ship's console. Along the bottom of the screen, the speaker (the
 * pilot themselves, turning to look at you, or the console's screen) and their words, typed out as they're said, then
 * the answers you can give. Click or press space to hear the rest at once.
 */
public class DialogueScreen extends Screen {
	private static final int MIN_PANEL_HEIGHT = 100;
	private static final int PORTRAIT_WIDTH = 70;
	private static final int BUTTON_HEIGHT = 18;
	/** Characters typed out each tick. */
	private static final float TYPING = 1.6F;

	private final Dialogues.Dialogue dialogue;
	private final String start;
	private final RingColor color;
	private final int entityId;
	private final java.util.List<Component> args;
	private Dialogues.Node node;
	private float typed;
	private String words = "";
	private int panelHeight = MIN_PANEL_HEIGHT;

	public DialogueScreen(OpenDialoguePayload payload) {
		super(Component.translatable("screen.rings.dialogue"));
		this.dialogue = payload.dialogue();
		this.start = payload.node();
		this.color = payload.color();
		this.entityId = payload.entityId();
		this.args = payload.args();
		Dialogues.Node first = this.dialogue.node(payload.node());
		this.node = first != null ? first : this.dialogue.node(Dialogues.PILOT_START);
	}

	public Dialogues.Node node() {
		return this.node;
	}

	private Component speaker() {
		return switch (this.node.speaker()) {
			case PILOT -> CrashPilot.name(this.color);
			case SHIP -> Component.translatable("ship.rings.computer");
			case GUARDIAN -> Component.translatable("entity.rings.oa_guardian");
		};
	}

	/** The speaker's colour: the pilot's ring, the ship's screen, the Guardians' blue. */
	private int speakerColor() {
		return switch (this.node.speaker()) {
			case PILOT -> ARGB.opaque(this.color.ui());
			case SHIP -> 0xFF60C8F0;
			case GUARDIAN -> 0xFF6E9CF0;
		};
	}

	private Component text() {
		Object[] args = new Object[2 + this.args.size()];
		args[0] = CrashPilot.name(this.color);
		args[1] = Component.translatable("color.rings." + this.color.id());
		for (int i = 0; i < this.args.size(); i++) {
			args[2 + i] = this.args.get(i);
		}
		return Component.translatable(this.dialogue.textKey(this.node), args);
	}

	private int panelTop() {
		return this.height - this.panelHeight - 6;
	}

	private int textLeft() {
		return 12 + PORTRAIT_WIDTH + 10;
	}

	private int textWidth() {
		return Math.min(this.width - this.textLeft() - 12, 360);
	}

	@Override
	protected void init() {
		this.words = this.text().getString();
		int lines = this.font.split(FormattedText.of(this.words), this.textWidth()).size();
		this.panelHeight = Math.max(MIN_PANEL_HEIGHT, 18 + lines * 10 + 6 + this.node.options().size() * (BUTTON_HEIGHT + 2) + 4);
		int left = this.textLeft();
		int y = this.panelTop() + 18 + lines * 10 + 6;
		int width = this.textWidth();
		for (Dialogues.Option option : this.node.options()) {
			Component label = Component.literal("› ").append(Component.translatable(this.dialogue.optionKey(option)));
			this.addRenderableWidget(Button.builder(label, button -> this.choose(option))
					.bounds(left, y, width, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + 2;
		}
	}

	/** The player gave an answer. */
	public void choose(Dialogues.Option option) {
		if (option.action() != Dialogues.Action.NONE) {
			ClientPlayNetworking.send(new DialogueActionPayload(this.entityId, option.action()));
		}
		if (option.action().serverAnswers()) {
			// What's said next comes back from the server
			return;
		}
		if (option.next() == null) {
			this.onClose();
			return;
		}
		Dialogues.Node next = this.dialogue.node(Dialogues.BACK.equals(option.next()) ? this.start : option.next());
		if (next == null) {
			this.onClose();
			return;
		}
		this.node = next;
		this.typed = 0.0F;
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6F));
		this.rebuildWidgets();
	}

	@Override
	public void tick() {
		this.typed += TYPING;
		Entity speaker = this.speakerEntity();
		if (this.entityId >= 0 && speaker == null) {
			// They've passed (or gone out of reach)
			if (this.typed > this.words.length() + 60) {
				this.onClose();
			}
		}
	}

	private @Nullable LivingEntity speakerEntity() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && this.entityId >= 0 && minecraft.level.getEntity(this.entityId) instanceof LivingEntity living ? living : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		this.typed = this.words.length();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 32 && this.typed < this.words.length()) {
			this.typed = this.words.length();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Only the panel is dimmed, so the crash site stays in view
		graphics.fillGradient(0, this.panelTop() - 40, this.width, this.height, 0x00000000, 0xC0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int energy = ARGB.opaque(this.color.ui());
		int bright = ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF);
		int top = this.panelTop();

		// The speaker: the pilot, or the console
		int px = 12;
		graphics.fill(px, top, px + PORTRAIT_WIDTH, top + this.panelHeight, 0xC0101018);
		graphics.outline(px, top, PORTRAIT_WIDTH, this.panelHeight, this.speakerColor());
		LivingEntity speaker = this.speakerEntity();
		if (speaker != null && this.node.speaker() != Dialogues.Speaker.SHIP) {
			// Upright for the portrait, however they're sitting in the world
			CrashPilotRenderer.portrait = true;
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, px + 1, top + 1, px + PORTRAIT_WIDTH - 1, top + this.panelHeight - 1,
					40, 0.0625F, mouseX, mouseY, speaker);
			CrashPilotRenderer.portrait = false;
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(px + PORTRAIT_WIDTH / 2.0F - 16, top + this.panelHeight / 2.0F - 20);
			graphics.pose().scale(2.0F, 2.0F);
			graphics.item(new ItemStack(ShipBlocks.SHIP_CONSOLE), 0, 0);
			graphics.pose().popMatrix();
		}

		// Who's speaking, and what they say so far, on a backing so chat behind doesn't show through
		int left = this.textLeft();
		graphics.fill(left - 6, top, left + this.textWidth() + 6, top + this.panelHeight, 0xC0101018);
		graphics.text(this.font, this.speaker(), left, top + 4, ARGB.srgbLerp(0.4F, this.speakerColor(), 0xFFFFFFFF), true);
		int shown = Math.min(this.words.length(), (int) this.typed);
		graphics.textWithWordWrap(this.font, FormattedText.of(this.words.substring(0, shown)), left, top + 18, this.textWidth(), 0xFFEFEFEF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
