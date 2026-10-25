package xyz.ssander.rings.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingConstructs.Construct;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.CreateConstructPayload;

/**
 * The construct wheel: open while the construct key is held, it shows the constructs in a ring around the middle
 * of the screen. Point the mouse at one and let go of the key (or click) to make it.
 */
public class ConstructWheelScreen extends Screen {
	private static final int RADIUS = 62;
	private static final int SLOT = 26;
	/** The mouse has to be this far from the middle to pick anything, so letting go in the middle cancels. */
	private static final int DEAD_ZONE = 18;

	private final RingColor color;
	private final ItemStack[] icons;
	private int selected = -1;

	public ConstructWheelScreen(RingColor color) {
		super(Component.translatable("screen.rings.constructs"));
		this.color = color;
		Construct[] constructs = Construct.values();
		this.icons = new ItemStack[constructs.length];
		for (Construct construct : constructs) {
			this.icons[construct.ordinal()] = switch (construct) {
				case WALL -> new ItemStack(Items.SHIELD);
				case BRIDGE -> new ItemStack(Items.SCAFFOLDING);
				case CAGE -> new ItemStack(Items.IRON_BARS);
				case FIST -> new ItemStack(Items.MACE);
				case SWORD -> tinted(new ItemStack(Rings.CONSTRUCT_SWORD));
				case PICKAXE -> tinted(new ItemStack(Rings.CONSTRUCT_PICKAXE));
			};
		}
	}

	private ItemStack tinted(ItemStack stack) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color.energy()));
		return stack;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// A light dim rather than the menu blur, so the world stays in view
		graphics.fill(0, 0, this.width, this.height, 0x50000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		this.selected = this.pick(mouseX, mouseY);

		int centreX = this.width / 2;
		int centreY = this.height / 2;
		int energy = ARGB.opaque(this.color.ui());
		int bright = ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF);
		Construct[] constructs = Construct.values();
		for (Construct construct : constructs) {
			int index = construct.ordinal();
			float angle = Mth.TWO_PI * index / constructs.length;
			int x = centreX + Math.round(Mth.sin(angle) * RADIUS);
			int y = centreY - Math.round(Mth.cos(angle) * RADIUS);
			boolean chosen = index == this.selected;

			int half = SLOT / 2 + (chosen ? 3 : 0);
			graphics.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, chosen ? bright : ARGB.scaleRGB(energy, 0.5F));
			graphics.fill(x - half, y - half, x + half, y + half, chosen ? ARGB.color(220, ARGB.scaleRGB(energy, 0.35F)) : 0xC0101010);
			graphics.item(this.icons[index], x - 8, y - 8);

			Component name = Component.translatable(construct.translationKey());
			graphics.text(this.font, name, x - this.font.width(name) / 2, y + half + 4, chosen ? bright : 0xFFE0E0E0, true);
		}

		// The middle: what letting go makes, and what it costs
		Component title = this.selected >= 0 ? Component.translatable(constructs[this.selected].translationKey()) : this.title;
		graphics.text(this.font, title, centreX - this.font.width(title) / 2, centreY - 9, bright, true);
		if (this.selected >= 0) {
			float percent = 100.0F * constructs[this.selected].cost() / RingItem.MAX_CHARGE;
			Component cost = Component.translatable("screen.rings.construct_cost", String.format("%.1f", percent));
			graphics.text(this.font, cost, centreX - this.font.width(cost) / 2, centreY + 3, 0xFFC0C0C0, true);
		}
	}

	/** Which construct the mouse points at, or -1 near the middle. */
	private int pick(double mouseX, double mouseY) {
		double dx = mouseX - this.width / 2.0;
		double dy = mouseY - this.height / 2.0;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			return -1;
		}
		int count = Construct.values().length;
		double angle = Math.atan2(dx, -dy);
		if (angle < 0) {
			angle += Math.PI * 2;
		}
		return (int) Math.floor((angle / (Math.PI * 2)) * count + 0.5) % count;
	}

	@Override
	public void tick() {
		// Letting go of the construct key makes the construct pointed at (only for keyboard keys: a mouse-button
		// binding can't be held while the wheel has the mouse, so it waits for a click instead)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(RingsClient.constructKey);
		if (key.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(key.getValue())) {
			this.finish();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.selected = this.pick(event.x(), event.y());
		this.finish();
		return true;
	}

	private void finish() {
		if (this.selected >= 0) {
			ClientPlayNetworking.send(new CreateConstructPayload(Construct.values()[this.selected]));
		}
		this.onClose();
	}
}
