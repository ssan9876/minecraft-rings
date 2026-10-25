package xyz.ssander.rings.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.ConstructItem;
import xyz.ssander.rings.RingConstructs;

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
		}
	}
}
