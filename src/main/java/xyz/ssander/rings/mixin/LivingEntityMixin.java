package xyz.ssander.rings.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingGlide;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	// Gliding on a ring wears out no elytra (vanilla would pick a glider to damage, and there's none), and the ring
	// breaks the fall when it lands
	@Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
	private void rings$ringGlide(CallbackInfo ci) {
		if ((Object) this instanceof Player player && player.isFallFlying() && RingGlide.canGlide(player)) {
			player.resetFallDistance();
			ci.cancel();
		}
	}
}
