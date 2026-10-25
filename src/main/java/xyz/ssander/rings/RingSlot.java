package xyz.ssander.rings;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The ring slot added to the player's inventory, above the offhand slot. */
public class RingSlot extends Slot {
	public static final Identifier EMPTY_ICON = Rings.id("container/slot/ring");

	public RingSlot(RingContainer container, int x, int y) {
		super(container, 0, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return itemStack.getItem() instanceof RingItem;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public Identifier getNoItemIcon() {
		return EMPTY_ICON;
	}

	/** Index of the ring slot in the menu, or -1 if it has none. */
	public static int indexIn(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot instanceof RingSlot) {
				return slot.index;
			}
		}

		return -1;
	}
}
