package xyz.ssander.rings.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.client.SuperFlight;

@Mixin(Player.class)
public abstract class PlayerTravelMixin {
	// Super flight moves the local player in place of vanilla's flying movement
	@Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
	private void rings$superFlight(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && SuperFlight.travel(player)) {
			ci.cancel();
		}
	}
}
