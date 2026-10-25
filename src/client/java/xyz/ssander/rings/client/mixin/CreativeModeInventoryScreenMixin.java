package xyz.ssander.rings.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingContainer;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	// The creative inventory tab lays out the survival slots by index, which would put the ring slot on
	// top of the second hotbar slot. Move it right of the armor, mirroring the offhand slot.
	@Inject(method = "selectTab", at = @At("TAIL"))
	private void rings$placeRingSlot(CreativeModeTab tab, CallbackInfo ci) {
		for (Slot slot : ((AbstractContainerScreen<?>) (Object) this).getMenu().slots) {
			if (slot.container instanceof RingContainer) {
				SlotAccessor accessor = (SlotAccessor) slot;
				accessor.rings$setX(127);
				accessor.rings$setY(20);
			}
		}
	}
}
