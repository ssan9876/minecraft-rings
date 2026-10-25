package xyz.ssander.rings.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityTypes;

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
			RingAnimations.tick(client);
		});

		RingAnimations.init();

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new OwnHeadLayer(avatarRenderer, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false)));
			}
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Rings.id("power_meter"), new RingHud());
	}
}
