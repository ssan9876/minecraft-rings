package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * What the planets are made of, and what's to be found in them: each has its own rock (and bricks cut from it), ores
 * of vanilla metals and gems in that rock, and something of its own. Resource files come from tools/gen_resources.py
 * and tools/gen_textures.py, and where they generate from tools/gen_worlds.py.
 */
public final class PlanetBlocks {
	/** Every block here, with its item, in the order they show in the creative inventory. */
	private static final List<Item> ITEMS = new ArrayList<>();

	// The Moon: grey dust over grey rock, iron, and moonstone, which glows
	public static final Block MOON_DUST = block("moon_dust", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND));
	public static final Block MOON_ROCK = block("moon_rock", Block::new, rock(MapColor.STONE));
	public static final Block MOON_ROCK_BRICKS = block("moon_rock_bricks", Block::new, rock(MapColor.STONE));
	public static final Block MOON_IRON_ORE = block("moon_iron_ore", props -> new DropExperienceBlock(UniformInt.of(0, 1), props), ore(MapColor.STONE));
	public static final Block MOONSTONE_ORE = block("moonstone_ore", props -> new DropExperienceBlock(UniformInt.of(3, 7), props),
			ore(MapColor.STONE).lightLevel(state -> 5));
	public static final Block MOONSTONE_BLOCK = block("moonstone_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.ICE).strength(3.0F, 6.0F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(state -> 13));

	// Mars: rust-red rock, rich in iron and gold, with red crystal geodes and ice deep down
	public static final Block MARS_ROCK = block("mars_rock", Block::new, rock(MapColor.TERRACOTTA_RED));
	public static final Block MARS_ROCK_BRICKS = block("mars_rock_bricks", Block::new, rock(MapColor.TERRACOTTA_RED));
	public static final Block MARS_IRON_ORE = block("mars_iron_ore", props -> new DropExperienceBlock(UniformInt.of(0, 1), props), ore(MapColor.TERRACOTTA_RED));
	public static final Block MARS_GOLD_ORE = block("mars_gold_ore", props -> new DropExperienceBlock(UniformInt.of(0, 2), props), ore(MapColor.TERRACOTTA_RED));
	public static final Block MARS_CRYSTAL_BLOCK = block("mars_crystal_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED).strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(state -> 8));
	public static final Block MARS_CRYSTAL = block("mars_crystal", props -> new AmethystClusterBlock(7.0F, 3.0F, props), crystal(MapColor.COLOR_RED, 6));

	// Oa: dark green stone, emeralds, and crystals of will that charge any ring
	public static final Block OAN_STONE = block("oan_stone", Block::new, rock(MapColor.COLOR_GREEN));
	public static final Block OAN_STONE_BRICKS = block("oan_stone_bricks", Block::new, rock(MapColor.COLOR_GREEN));
	public static final Block OAN_EMERALD_ORE = block("oan_emerald_ore", props -> new DropExperienceBlock(UniformInt.of(3, 7), props), ore(MapColor.COLOR_GREEN));
	public static final Block WILL_CRYSTAL_ORE = block("will_crystal_ore", props -> new DropExperienceBlock(UniformInt.of(4, 8), props),
			ore(MapColor.COLOR_GREEN).lightLevel(state -> 7));
	public static final Block OAN_CRYSTAL = block("oan_crystal", props -> new AmethystClusterBlock(7.0F, 3.0F, props), crystal(MapColor.EMERALD, 10));

	/** Moonstone, mined on the Moon. Nine make a glowing block. */
	public static final Item MOONSTONE = item("moonstone", Item::new, new Item.Properties());
	/** A shard of will, mined on Oa: right-click it with a ring on to charge the ring a quarter, whatever its colour. */
	public static final Item WILL_SHARD = item("will_shard", WillShardItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));

	private PlanetBlocks() {
	}

	/** Registers the blocks (loading this class does it). */
	static void init() {
	}

	public static List<Item> items() {
		return ITEMS;
	}

	private static BlockBehaviour.Properties rock(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops().strength(1.5F, 6.0F);
	}

	private static BlockBehaviour.Properties ore(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops().strength(3.0F, 3.0F);
	}

	private static BlockBehaviour.Properties crystal(MapColor color, int light) {
		return BlockBehaviour.Properties.of().mapColor(color).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
				.strength(1.5F).lightLevel(state -> light).pushReaction(PushReaction.POPPED);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Rings.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		item(name, itemProps -> new BlockItem(block, itemProps), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Rings.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ITEMS.add(item);
		return item;
	}
}
