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
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.ActivateRingPayload;
import xyz.ssander.rings.network.RingActionPayload;

public class RingsClient implements ClientModInitializer {
	public static KeyMapping activateKey;
	public static KeyMapping beamKey;
	public static KeyMapping constructKey;
	public static KeyMapping forceFieldKey;
	public static KeyMapping scanKey;
	public static KeyMapping powerKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Rings.id("rings"));
		activateKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.activate", InputConstants.KEY_R, category));
		beamKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.beam", InputConstants.KEY_G, category));
		constructKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.construct", InputConstants.KEY_V, category));
		forceFieldKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.force_field", InputConstants.KEY_B, category));
		scanKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.scan", InputConstants.KEY_Z, category));
		powerKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rings.power", InputConstants.KEY_C, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (activateKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ActivateRingPayload());
				}
			}

			while (constructKey.consumeClick()) {
				openConstructWheel(client);
			}
			while (forceFieldKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.FORCE_FIELD));
				}
			}
			while (powerKey.consumeClick()) {
				usePower(client);
			}
			while (scanKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.SCAN));
				}
			}

			RingFlight.tick(client);
			RingAnimations.tick(client);
			RingBeams.tick(client);
			RingScan.tick(client);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(RingBeams::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(SpaceSky::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(ForceFields::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(RingScan::render);

		RingAnimations.init();
		UpdateNotice.init();

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new OwnHeadLayer(avatarRenderer, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false)));
				helper.register(new WornRing.Layer(avatarRenderer, ((SlimArms) avatarRenderer).rings$slim()));
			}
		});

		EntityRendererRegistry.register(Rings.CONSTRUCT_FIST, ConstructFistRenderer::new);
		EntityRendererRegistry.register(Rings.FORCE_BUBBLE, ForceBubbleRenderer::new);
		EntityRendererRegistry.register(Rings.ENERGY_BOLT, EnergyBoltRenderer::new);
		EntityRendererRegistry.register(Rings.CONSTRUCT_WOLF, ConstructBeastRenderer::new);
		EntityRendererRegistry.register(Rings.CONSTRUCT_GOLEM, ConstructBeastRenderer::new);
		EntityRendererRegistry.register(Rings.CONSTRUCT_JET, ConstructJetRenderer::new);

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Rings.id("power_meter"), new RingHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Rings.id("scan"), new RingScan());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Rings.id("oath"), new RingOath());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Rings.id("force_field"), new ForceFields());
	}

	/** The ring power key: the colour's power, or for a pink ring with sneak held, the list of friends to go to. */
	private static void usePower(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		if (RingAttachments.activeColor(client.player) == RingColor.PINK && client.player.isShiftKeyDown()) {
			client.gui.setScreen(new FriendScreen());
			return;
		}
		ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.POWER));
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
