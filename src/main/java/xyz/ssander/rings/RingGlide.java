package xyz.ssander.rings;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The ring's elytra mode: flying fast on a ring is real elytra flight (vanilla's fall flying, with its pose, low
 * hitbox and camera), powered by the ring instead of wings. The ring wearer's client switches into it and steers it
 * (see SuperFlight); this is what lets vanilla accept it without an elytra.
 */
public final class RingGlide {
	private RingGlide() {
	}

	/**
	 * Whether the player can glide on their ring right now: it's on and grants flight, and they're in the air and not
	 * hovering (vanilla only glides when creative flight is off).
	 */
	public static boolean canGlide(Player player) {
		return RingAttachments.isActive(player)
				&& RingAttachments.getRing(player).getItem() instanceof RingItem ring && ring.grantsFlight()
				&& player.getAbilities().mayfly
				&& !player.getAbilities().flying
				&& !player.onGround()
				&& !player.isPassenger()
				&& !player.hasEffect(MobEffects.LEVITATION);
	}
}
