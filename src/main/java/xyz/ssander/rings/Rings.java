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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import xyz.ssander.rings.network.ActivateRingPayload;

public class Rings implements ModInitializer {
	public static final String MOD_ID = "rings";

	/** Grants flight and turns the wearer into the lantern suit while active. */
	public static final RingItem HERO_RING = register("hero_ring",
			props -> new RingItem(props, true, id("lantern")),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** Charges rings: hold right-click on it while wearing (or holding) one. */
	public static final Block POWER_LANTERN = registerBlock("power_lantern", PowerLanternBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.EMERALD)
					.strength(3.5F)
					.sound(SoundType.LANTERN)
					.lightLevel(state -> 15)
					.noOcclusion());
	public static final Item POWER_LANTERN_ITEM = register("power_lantern",
			props -> new BlockItem(POWER_LANTERN, props), new Item.Properties().useBlockDescriptionPrefix());

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	@Override
	public void onInitialize() {
		RingAttachments.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(HERO_RING);
			output.accept(POWER_LANTERN_ITEM);
		});

		PayloadTypeRegistry.serverboundPlay().register(ActivateRingPayload.TYPE, ActivateRingPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActivateRingPayload.TYPE, (payload, context) -> RingPowers.toggle(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RingPowers.tick(player);
			}
		});
	}
}
