package xyz.ssander.rings;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One-slot container backed by the player's {@link RingAttachments#RING} attachment. */
public class RingContainer implements Container {
	private final Player player;

	public RingContainer(Player player) {
		this.player = player;
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return this.getItem(0).isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return RingAttachments.getRing(this.player);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack stack = this.getItem(0);
		if (stack.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}

		ItemStack removed = stack.split(count);
		this.setItem(0, stack);
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack stack = this.getItem(0);
		this.setItem(0, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack itemStack) {
		RingAttachments.setRing(this.player, itemStack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		this.setItem(0, ItemStack.EMPTY);
	}
}
