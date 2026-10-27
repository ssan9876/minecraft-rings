package xyz.ssander.rings.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.network.ConstructItemPayload;

/**
 * The construct wheel's eye: type the name of any item and make it out of the ring's energy, paid for out of its
 * charge (the rarer, the dearer). Click an item for one, shift-click for a stack. It stays open to make more.
 */
public class ConstructAnyScreen extends Screen {
	private static final int COLUMNS = 9;
	private static final int ROWS = 6;
	private static final int SLOT = 18;
	private static final int SEARCH_HEIGHT = 18;
	private static final int GAP = 6;

	private final RingColor color;
	private List<ItemStack> all = List.of();
	private List<ItemStack> shown = List.of();
	/** How many rows down the list is scrolled. */
	private int scroll;
	private int left;
	private int top;
	private EditBox search;

	public ConstructAnyScreen(RingColor color) {
		super(Component.translatable("screen.rings.construct_any"));
		this.color = color;
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		if (this.all.isEmpty() && minecraft.level != null) {
			FeatureFlagSet features = minecraft.level.enabledFeatures();
			this.all = BuiltInRegistries.ITEM.stream()
					.filter(item -> item != Items.AIR)
					.map(ItemStack::new)
					.filter(stack -> RingConstructs.canConstruct(stack, features))
					.toList();
		}
		this.left = this.width / 2 - COLUMNS * SLOT / 2;
		this.top = this.height / 2 - (SEARCH_HEIGHT + GAP + ROWS * SLOT) / 2;
		String typed = this.search != null ? this.search.getValue() : "";
		this.search = new EditBox(this.font, this.left, this.top, COLUMNS * SLOT, SEARCH_HEIGHT, Component.translatable("screen.rings.construct_any_hint"));
		this.search.setHint(Component.translatable("screen.rings.construct_any_hint"));
		this.search.setMaxLength(50);
		this.search.setValue(typed);
		this.search.setResponder(this::filter);
		this.addRenderableWidget(this.search);
		this.setInitialFocus(this.search);
		this.filter(typed);
	}

	private void filter(String typed) {
		String query = typed.trim().toLowerCase(Locale.ROOT);
		this.shown = query.isEmpty() ? this.all : this.all.stream()
				.filter(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
						|| BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(query.replace(' ', '_')))
				.toList();
		this.scroll = 0;
	}

	private int gridTop() {
		return this.top + SEARCH_HEIGHT + GAP;
	}

	private int maxScroll() {
		return Math.max(0, Mth.positiveCeilDiv(this.shown.size(), COLUMNS) - ROWS);
	}

	/** The index into the shown items of the slot under the mouse, or -1. */
	private int slotAt(double mouseX, double mouseY) {
		int column = Mth.floor((mouseX - this.left) / SLOT);
		int row = Mth.floor((mouseY - this.gridTop()) / SLOT);
		if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
			return -1;
		}
		int index = (row + this.scroll) * COLUMNS + column;
		return index < this.shown.size() ? index : -1;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int energy = ARGB.opaque(this.color.ui());
		int bright = ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF);
		graphics.centeredText(this.font, this.title, this.width / 2, this.top - 26, bright);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			float charge = 100.0F * RingItem.charge(RingAttachments.getRing(minecraft.player)) / RingItem.MAX_CHARGE;
			Component left = Component.translatable("screen.rings.construct_cost", String.format("%.0f", charge));
			graphics.centeredText(this.font, left, this.width / 2, this.top - 13, 0xFFC0C0C0);
		}

		int gridTop = this.gridTop();
		int gridBottom = gridTop + ROWS * SLOT;
		graphics.fill(this.left - 2, gridTop - 2, this.left + COLUMNS * SLOT + 2, gridBottom + 2, ARGB.scaleRGB(energy, 0.5F));
		graphics.fill(this.left - 1, gridTop - 1, this.left + COLUMNS * SLOT + 1, gridBottom + 1, 0xE0101010);
		int hovered = this.slotAt(mouseX, mouseY);
		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = (row + this.scroll) * COLUMNS + column;
				if (index >= this.shown.size()) {
					break;
				}
				int x = this.left + column * SLOT;
				int y = gridTop + row * SLOT;
				if (index == hovered) {
					graphics.fill(x, y, x + SLOT, y + SLOT, ARGB.color(160, energy));
				}
				graphics.item(this.shown.get(index), x + 1, y + 1);
			}
		}
		if (this.shown.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("screen.rings.construct_any_none"), this.width / 2, gridTop + ROWS * SLOT / 2 - 4, 0xFFE0E0E0);
		}
		graphics.centeredText(this.font, Component.translatable("screen.rings.construct_any_help"), this.width / 2, gridBottom + 8, 0xFFA0A0A0);

		if (hovered >= 0) {
			ItemStack stack = this.shown.get(hovered);
			int cost = RingConstructs.itemCost(stack);
			int stackSize = stack.getMaxStackSize();
			Component price = stackSize > 1
					? Component.translatable("screen.rings.construct_any_cost", percent(cost), stackSize, percent(cost * stackSize))
					: Component.translatable("screen.rings.construct_cost", percent(cost));
			graphics.setTooltipForNextFrame(this.font, List.of(stack.getHoverName(), price.copy().withStyle(style -> style.withColor(0xA0A0A0))),
					Optional.empty(), mouseX, mouseY);
		}
	}

	private static String percent(int charge) {
		return String.format("%.1f", 100.0F * charge / RingItem.MAX_CHARGE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = this.slotAt(event.x(), event.y());
		if (index >= 0) {
			ClientPlayNetworking.send(new ConstructItemPayload(this.shown.get(index).typeHolder(), event.hasShiftDown()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, this.maxScroll());
		return true;
	}
}
