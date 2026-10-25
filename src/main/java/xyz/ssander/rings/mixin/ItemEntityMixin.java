package xyz.ssander.rings.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.ConstructItem;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingReturn;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	// A construct tool let go of fades away at once
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void rings$fadeConstruct(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.getItem().getItem() instanceof ConstructItem && self.level() instanceof ServerLevel level) {
			level.sendParticles(RingConstructs.colorOf(self.getItem()).dust(0.4F, 0.8F), self.getX(), self.getY() + 0.3, self.getZ(), 8, 0.2, 0.2, 0.2, 0.0);
			self.discard();
			ci.cancel();
		} else if (self.getItem().getItem() instanceof RingItem && self.level() instanceof ServerLevel level && RingReturn.tick(self, level)) {
			ci.cancel();
		}
	}

	// A ring flying back to its wearer goes on their finger when it reaches them
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void rings$ringReturns(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.getItem().getItem() instanceof RingItem && RingReturn.touch(self, player)) {
			ci.cancel();
		}
	}
}
