package xyz.ssander.rings.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.ActivateRingPayload;

public class RingsClient implements ClientModInitializer {
	public static KeyMapping activateKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Rings.id("rings"));
		activateKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.activate", InputConstants.KEY_R, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (activateKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ActivateRingPayload());
				}
			}

			RingFlight.tick(client);
		});
	}
}
