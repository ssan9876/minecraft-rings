package xyz.ssander.rings;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** A ring worn in the ring slot. Its powers switch on and off with the activate key and run on its charge. */
public class RingItem extends Item {
	/** A full ring: 30 minutes switched on, or 10 minutes of flying (see RingPowers). */
	public static final int MAX_CHARGE = 1800;
	private static final int BAR_COLOR_LOW = 0xFF5050;

	private final RingColor color;

	public RingItem(Properties properties, RingColor color) {
		super(properties.component(RingComponents.CHARGE, MAX_CHARGE));
		this.color = color;
	}

	public RingColor color() {
		return this.color;
	}

	public boolean grantsFlight() {
		return true;
	}

	/** The costume the wearer turns into while active, or null for none. */
	public @Nullable Identifier costume() {
		return this.color.costume();
	}

	public static int charge(ItemStack stack) {
		return stack.getOrDefault(RingComponents.CHARGE, 0);
	}

	/** Adds (or with a negative amount, drains) charge, kept within 0 and the maximum. Returns the new charge. */
	public static int addCharge(ItemStack stack, int amount) {
		int charge = Mth.clamp(charge(stack) + amount, 0, MAX_CHARGE);
		stack.set(RingComponents.CHARGE, charge);
		return charge;
	}

	/** What the ring's colour can do (see ColorPowers), and what an orange ring has claimed. */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.rings." + this.color.id() + ".power").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.rings." + this.color.id() + ".gift").withStyle(ChatFormatting.DARK_GRAY));
		List<Identifier> claims = stack.getOrDefault(RingComponents.CLAIMS, List.of());
		if (!claims.isEmpty()) {
			builder.accept(Component.translatable("tooltip.rings.claims", Component.literal(String.join(", ", claims.stream()
					.map(id -> BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription().getString()).toList()))).withStyle(ChatFormatting.GOLD));
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return charge(stack) < MAX_CHARGE;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charge(stack) / MAX_CHARGE);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return charge(stack) < MAX_CHARGE * 0.15F ? BAR_COLOR_LOW : this.color.ui();
	}
}
