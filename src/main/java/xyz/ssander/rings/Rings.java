package xyz.ssander.rings;

import java.util.EnumMap;
import java.util.Map;
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

import xyz.ssander.rings.network.ActivateRingPayload;
import xyz.ssander.rings.network.FireBeamPayload;
import xyz.ssander.rings.network.RingAnimationPayload;

public class Rings implements ModInitializer {
	public static final String MOD_ID = "rings";

	private static final Map<RingColor, RingItem> RINGS = new EnumMap<>(RingColor.class);
	private static final Map<RingColor, PowerLanternBlock> LANTERNS = new EnumMap<>(RingColor.class);
	private static final Map<RingColor, Item> LANTERN_ITEMS = new EnumMap<>(RingColor.class);

	static {
		for (RingColor color : RingColor.values()) {
			// Grants flight and turns the wearer into the colour's suit while active
			RINGS.put(color, register(color.ringId(), props -> new RingItem(props, color),
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

			// Charges rings of its colour: hold right-click on it while wearing (or holding) one
			PowerLanternBlock lantern = registerBlock(color.lanternId(), props -> new PowerLanternBlock(props, color),
					BlockBehaviour.Properties.of()
							.mapColor(color.mapColor())
							.strength(3.5F)
							.sound(SoundType.LANTERN)
							.lightLevel(state -> 15)
							.noOcclusion());
			LANTERNS.put(color, lantern);
			LANTERN_ITEMS.put(color, register(color.lanternId(), props -> new BlockItem(lantern, props),
					new Item.Properties().useBlockDescriptionPrefix()));
		}
	}

	public static RingItem ring(RingColor color) {
		return RINGS.get(color);
	}

	public static PowerLanternBlock lantern(RingColor color) {
		return LANTERNS.get(color);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static <T extends Block> T registerBlock(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
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
			RINGS.values().forEach(output::accept);
			LANTERN_ITEMS.values().forEach(output::accept);
		});

		PayloadTypeRegistry.serverboundPlay().register(ActivateRingPayload.TYPE, ActivateRingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RingAnimationPayload.TYPE, RingAnimationPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FireBeamPayload.TYPE, FireBeamPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireBeamPayload.TYPE, (payload, context) -> RingBeam.setFiring(context.player(), payload.firing()));
		ServerPlayNetworking.registerGlobalReceiver(ActivateRingPayload.TYPE, (payload, context) -> RingPowers.toggle(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RingPowers.tick(player);
			}
		});
	}
}
