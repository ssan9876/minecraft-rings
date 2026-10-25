package xyz.ssander.rings.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingGlide;
import xyz.ssander.rings.RingReturn;

@Mixin(Player.class)
public abstract class PlayerMixin {
	// Where vanilla drops the inventory (so not with keep_inventory), the ring goes off to choose a new wearer. With
	// nobody to choose, it stays in the ring slot, which is kept through death.
	@Inject(method = "dropEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V", shift = At.Shift.AFTER))
	private void rings$ringChoosesHeir(ServerLevel level, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer self)) {
			return;
		}
		ItemStack ring = RingAttachments.getRing(self);
		ServerPlayer heir = ring.isEmpty() ? null : RingReturn.chooseHeir(self);
		if (heir != null) {
			RingAttachments.setRing(self, ItemStack.EMPTY);
			RingReturn.sendToHeir(self, ring, heir);
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
