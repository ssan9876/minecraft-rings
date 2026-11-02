package xyz.ssander.rings.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import xyz.ssander.rings.RingDuels;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	// Duellists can hurt each other even where PvP is off (see RingDuels)
	@Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
	private void rings$duellistsCanFight(Player target, CallbackInfoReturnable<Boolean> cir) {
		if (RingDuels.duelling((ServerPlayer) (Object) this, target)) {
			cir.setReturnValue(true);
		}
	}
}
