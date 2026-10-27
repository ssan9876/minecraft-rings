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

import xyz.ssander.rings.ColorPowers;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingConstructs.Construct;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.CreateConstructPayload;

/**
 * The construct wheel: open while the construct key is held, it shows the constructs in a ring around the middle
 * of the screen. Point the mouse at one and let go of the key (or click) to make it. Click the eye in the middle to
 * make any item instead (ConstructAnyScreen); letting go of the key there cancels.
 */
public class ConstructWheelScreen extends Screen {
	private static final int RADIUS = 88;
	private static final int SLOT = 26;
	/** The mouse has to be this far from the middle to pick anything, so letting go in the middle cancels. */
	private static final int DEAD_ZONE = 18;
	/** The wheel sits a little above the middle of the screen, clear of the hotbar. */
	private static final int RAISE = 12;

	private static final ItemStack EYE = new ItemStack(Items.ENDER_EYE);

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
				case WALL -> new ItemStack(Items.STONE_BRICK_WALL);
				case BRIDGE -> new ItemStack(Items.SCAFFOLDING);
				case STAIRS -> new ItemStack(Items.QUARTZ_STAIRS);
				case DOME -> new ItemStack(Items.GLASS);
				case CAGE -> new ItemStack(Items.IRON_BARS);
				case GRAB -> new ItemStack(Items.LEAD);
				case FIST -> new ItemStack(Items.MACE);
				case HAMMER -> new ItemStack(Items.ANVIL);
				case SWORD -> tinted(new ItemStack(Rings.CONSTRUCT_SWORD));
				case SHIELD -> tinted(new ItemStack(Rings.CONSTRUCT_SHIELD));
				case AXE -> tinted(new ItemStack(Rings.CONSTRUCT_AXE));
				case PICKAXE -> tinted(new ItemStack(Rings.CONSTRUCT_PICKAXE));
				case SHOVEL -> tinted(new ItemStack(Rings.CONSTRUCT_SHOVEL));
				case WOLF -> new ItemStack(Items.BONE);
				case GOLEM -> new ItemStack(Items.CARVED_PUMPKIN);
				case JET -> new ItemStack(Items.ELYTRA);
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
		int centreY = this.height / 2 - RAISE;
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
		}

		// The eye in the middle, for making any item
		boolean eye = this.selected < 0;
		int half = SLOT / 2 + (eye ? 3 : 0);
		graphics.fill(centreX - half - 1, centreY - half - 1, centreX + half + 1, centreY + half + 1, eye ? bright : ARGB.scaleRGB(energy, 0.5F));
		graphics.fill(centreX - half, centreY - half, centreX + half, centreY + half, eye ? ARGB.color(220, ARGB.scaleRGB(energy, 0.35F)) : 0xC0101010);
		graphics.item(EYE, centreX - 8, centreY - 8);

		// Above and below it: what letting go makes, and what it costs
		Component title = this.selected >= 0 ? Component.translatable(constructs[this.selected].translationKey())
				: Component.translatable("screen.rings.construct_any");
		graphics.text(this.font, title, centreX - this.font.width(title) / 2, centreY - SLOT / 2 - 16, bright, true);
		Component below = this.selected >= 0
				? Component.translatable("screen.rings.construct_cost",
						String.format("%.1f", 100.0F * ColorPowers.constructCost(constructs[this.selected], this.color) / RingItem.MAX_CHARGE))
				: Component.translatable("screen.rings.construct_any_click");
		graphics.text(this.font, below, centreX - this.font.width(below) / 2, centreY + SLOT / 2 + 7, 0xFFC0C0C0, true);
	}

	/** Which construct the mouse points at, or -1 on the eye in the middle. */
	private int pick(double mouseX, double mouseY) {
		double dx = mouseX - this.width / 2.0;
		double dy = mouseY - (this.height / 2.0 - RAISE);
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
		if (this.selected < 0) {
			this.minecraft.gui.setScreen(new ConstructAnyScreen(this.color));
			return true;
		}
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
