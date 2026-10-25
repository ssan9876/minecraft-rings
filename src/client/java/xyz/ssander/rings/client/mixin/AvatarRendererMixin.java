package xyz.ssander.rings.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.client.Costumes;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	// Swaps in the costume's skin and cape. First-person hands read this state too.
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void rings$applyCostume(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}

		Identifier costume = player.getAttached(RingAttachments.COSTUME);
		PlayerSkin.Patch patch = costume != null ? Costumes.get(costume) : null;
		if (patch == null) {
			return;
		}

		state.skin = state.skin.with(patch);
		// The costume is the whole outfit, so show all of it whatever the player's skin settings are
		state.showHat = true;
		state.showJacket = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showCape |= patch.cape().isPresent();
	}
}
