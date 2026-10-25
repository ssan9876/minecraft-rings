package xyz.ssander.rings;

import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import xyz.ssander.rings.network.ActivateRingPayload;

public class Rings implements ModInitializer {
	public static final String MOD_ID = "rings";

	/** Grants flight and turns the wearer into the lantern suit while active. */
	public static final RingItem HERO_RING = register("hero_ring",
			props -> new RingItem(props, true, id("lantern")),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	@Override
	public void onInitialize() {
		RingAttachments.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(HERO_RING));

		PayloadTypeRegistry.serverboundPlay().register(ActivateRingPayload.TYPE, ActivateRingPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActivateRingPayload.TYPE, (payload, context) -> RingPowers.toggle(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RingPowers.tick(player);
			}
		});
	}
}
