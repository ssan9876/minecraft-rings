package xyz.ssander.rings.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.RingContainer;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingSlot;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractCraftingMenu {
	private InventoryMenuMixin(MenuType<?> menuType, int containerId, int width, int height) {
		super(menuType, containerId, width, height);
	}

	// Added last (index 46), after the vanilla slots, so their hard-coded indexes stay valid
	@Inject(method = "<init>", at = @At("TAIL"))
	private void rings$addRingSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		this.addSlot(new RingSlot(new RingContainer(owner), 77, 44));
	}

	// Shift-clicking a ring puts it in the ring slot when that's empty, like armor
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void rings$quickMoveRing(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		Slot slot = this.slots.get(slotIndex);
		int ringIndex = RingSlot.indexIn(this);
		if (slot instanceof RingSlot || ringIndex < 0 || this.slots.get(ringIndex).hasItem()
				|| !(slot.getItem().getItem() instanceof RingItem)) {
			return;
		}

		ItemStack stack = slot.getItem();
		ItemStack before = stack.copy();
		if (this.moveItemStackTo(stack, ringIndex, ringIndex + 1, false)) {
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY, before);
			} else {
				slot.setChanged();
			}

			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
