package xyz.ssander.rings.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.ActivateRingPayload;

public class RingsClient implements ClientModInitializer {
	public static KeyMapping activateKey;
	public static KeyMapping beamKey;
	public static KeyMapping constructKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Rings.id("rings"));
		activateKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.activate", InputConstants.KEY_R, category));
		beamKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.beam", InputConstants.KEY_G, category));
		constructKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.construct", InputConstants.KEY_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (activateKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ActivateRingPayload());
				}
			}

			while (constructKey.consumeClick()) {
				openConstructWheel(client);
			}

			RingFlight.tick(client);
			RingAnimations.tick(client);
			RingBeams.tick(client);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(RingBeams::render);

		RingAnimations.init();

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new OwnHeadLayer(avatarRenderer, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false)));
			}
		});

		EntityRendererRegistry.register(Rings.CONSTRUCT_FIST, ConstructFistRenderer::new);

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Rings.id("power_meter"), new RingHud());
	}

	/** Holding the construct key opens the wheel, if the ring is on. */
	private static void openConstructWheel(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		if (!(RingAttachments.getRing(client.player).getItem() instanceof RingItem ring) || !RingAttachments.isActive(client.player)) {
			client.player.sendOverlayMessage(Component.translatable("message.rings.construct_inactive"));
			return;
		}
		client.gui.setScreen(new ConstructWheelScreen(ring.color()));
	}
}
