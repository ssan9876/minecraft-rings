package xyz.ssander.rings;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** A ring worn in the ring slot. Its powers switch on and off with the activate key and run on its charge. */
public class RingItem extends Item {
	/** A full ring: 30 minutes switched on, or 10 minutes of flying (see RingPowers). */
	public static final int MAX_CHARGE = 1800;
	private static final int BAR_COLOR = 0x3CFF5A;
	private static final int BAR_COLOR_LOW = 0xFF5050;

	private final boolean flight;
	private final @Nullable Identifier costume;

	public RingItem(Properties properties, boolean flight, @Nullable Identifier costume) {
		super(properties.component(RingComponents.CHARGE, MAX_CHARGE));
		this.flight = flight;
		this.costume = costume;
	}

	public boolean grantsFlight() {
		return this.flight;
	}

	/** The costume the wearer turns into while active, or null for none. */
	public @Nullable Identifier costume() {
		return this.costume;
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
		return charge(stack) < MAX_CHARGE * 0.15F ? BAR_COLOR_LOW : BAR_COLOR;
	}
}
