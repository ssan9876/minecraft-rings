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
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import xyz.ssander.rings.network.ActivateRingPayload;
import xyz.ssander.rings.network.CreateConstructPayload;
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

	/** What walls, bridges and cages are made of (no item: it only ever comes from a ring). */
	public static final HardLightBlock HARD_LIGHT = registerBlock("hard_light", HardLightBlock::new,
			BlockBehaviour.Properties.of()
					.strength(50.0F, 1200.0F)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> 10)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.IMMOVEABLE)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false));

	public static final EntityType<ConstructFist> CONSTRUCT_FIST = registerEntity("construct_fist",
			EntityType.Builder.<ConstructFist>of(ConstructFist::new, MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(1).noSummon());

	/** Construct tools, made by the construct wheel in the ring's colour (their dye) and gone when it switches off. */
	public static final ConstructItem CONSTRUCT_SWORD = register("construct_sword", ConstructItem::new,
			constructTool(new Item.Properties().sword(ToolMaterial.DIAMOND, 3.0F, -2.4F)));
	public static final ConstructItem CONSTRUCT_AXE = register("construct_axe", ConstructItem::new,
			constructTool(new Item.Properties().axe(ToolMaterial.NETHERITE, 5.0F, -3.0F)));
	public static final ConstructItem CONSTRUCT_PICKAXE = register("construct_pickaxe", ConstructItem::new,
			constructTool(new Item.Properties().pickaxe(ToolMaterial.NETHERITE, 1.0F, -2.8F)));
	public static final ConstructItem CONSTRUCT_SHOVEL = register("construct_shovel", ConstructItem::new,
			constructTool(new Item.Properties().shovel(ToolMaterial.NETHERITE, 1.5F, -3.0F)));

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

	private static Item.Properties constructTool(Item.Properties properties) {
		return properties
				.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
				.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
						.withHidden(DataComponents.DYED_COLOR, true)
						.withHidden(DataComponents.UNBREAKABLE, true));
	}

	private static <T extends Entity> EntityType<T> registerEntity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
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
		PayloadTypeRegistry.serverboundPlay().register(CreateConstructPayload.TYPE, CreateConstructPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CreateConstructPayload.TYPE, (payload, context) -> RingConstructs.make(context.player(), payload.construct()));
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
