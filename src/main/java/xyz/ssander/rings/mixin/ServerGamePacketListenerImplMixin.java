package xyz.ssander.rings.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingSlot;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	// The creative inventory sends slot changes by index, and vanilla only accepts 1-45. Accept the ring slot too.
	@Inject(method = "handleSetCreativeModeSlot", cancellable = true, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER))
	private void rings$setCreativeRingSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		InventoryMenu menu = this.player.inventoryMenu;
		int slotNum = packet.slotNum();
		if (slotNum < 0 || slotNum >= menu.slots.size() || !(menu.getSlot(slotNum) instanceof RingSlot)) {
			return;
		}

		ci.cancel();
		if (!this.player.hasInfiniteMaterials()) {
			return;
		}

		Slot slot = menu.getSlot(slotNum);
		ItemStack stack = packet.itemStack();
		if (stack.isEmpty() || slot.mayPlace(stack) && stack.getCount() == 1) {
			slot.setByPlayer(stack);
			menu.setRemoteSlot(slotNum, stack);
			menu.broadcastChanges();
		} else {
			// Puts the client's view of the slot back
			menu.broadcastFullState();
		}
	}
}
