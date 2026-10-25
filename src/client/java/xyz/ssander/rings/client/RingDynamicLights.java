package xyz.ssander.rings.client;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.Rings;

/**
 * With LambDynamicLights installed, players glow with their ring's energy while suited up, lighting up what's around
 * them, and brighter still while firing the beam. Only LambDynamicLights calls this (its "lambdynlights:initializer"
 * entrypoint), so without it nothing here loads.
 */
public class RingDynamicLights implements DynamicLightsInitializer {
	private static final int SUITED_LIGHT = 12;
	private static final int FIRING_LIGHT = 15;

	@Override
	public void onInitializeDynamicLights(DynamicLightsContext context) {
		SuitedLuminance.type = EntityLuminance.Type.registerSimple(Rings.id("suited"), SuitedLuminance.INSTANCE);
		context.entityLightSourceManager().onRegisterEvent().register(registry -> registry.register(EntityTypes.PLAYER, SuitedLuminance.INSTANCE));
	}

	/** A player's light: their ring's glow while its suit is on, none otherwise. */
	private static final class SuitedLuminance implements EntityLuminance {
		static final SuitedLuminance INSTANCE = new SuitedLuminance();
		static Type type;

		private SuitedLuminance() {
		}

		@Override
		public Type type() {
			return type;
		}

		@Override
		public int getLuminance(ItemLightSourceManager items, Entity entity) {
			if (!(entity instanceof Player player) || player.getAttached(RingAttachments.COSTUME) == null) {
				return 0;
			}
			return RingBeam.isFiring(player) ? FIRING_LIGHT : SUITED_LIGHT;
		}
	}
}
