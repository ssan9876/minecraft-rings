package xyz.ssander.rings.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingGlide;

@Mixin(Player.class)
public abstract class PlayerMixin {
	// Drops the ring with the rest of the inventory. Vanilla only drops it without keep_inventory.
	@Inject(method = "dropEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V", shift = At.Shift.AFTER))
	private void rings$dropRing(ServerLevel level, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		ItemStack ring = RingAttachments.getRing(self);
		if (!ring.isEmpty()) {
			RingAttachments.setRing(self, ItemStack.EMPTY);
			ItemEntity drop = self.createItemStackToDrop(ring, true, false);
			if (drop != null) {
				level.addFreshEntity(drop);
			}
		}
	}

	// An active ring lets its wearer glide like an elytra (the ring's elytra mode, see RingGlide)
	@Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
	private void rings$canGlideOnRing(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && RingGlide.canGlide((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
