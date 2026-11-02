package xyz.ssander.rings;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * What's left behind on the dead worlds, for ring-bearers to find: on the Moon, the shrines of the first Lanterns (a
 * ruined platform of pillars round an old lantern, with a chest of moonstone, will shards and, sometimes, an Oan
 * relic), and on Mars, the wrecks of Manhunter scouts (a buried pod with salvage in it). Where they appear is in the
 * planets' biome files (see tools/gen_worlds.py).
 */
public final class PlanetRuins {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	public static final ResourceKey<LootTable> SHRINE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Rings.id("chests/moon_shrine"));
	public static final ResourceKey<LootTable> WRECK_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Rings.id("chests/mars_wreck"));

	/** A shrine of the first Lanterns, on the Moon. */
	public record MoonShrine() implements Feature {
		public static final MapCodec<MoonShrine> CODEC = MapCodec.unit(new MoonShrine());

		@Override
		public MapCodec<MoonShrine> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos base = ground(level, origin);
			BlockState bricks = PlanetBlocks.MOON_ROCK_BRICKS.defaultBlockState();
			BlockState rock = PlanetBlocks.MOON_ROCK.defaultBlockState();
			BlockState moonstone = PlanetBlocks.MOONSTONE_BLOCK.defaultBlockState();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					// The platform, worn away in places, with moonstone set in a ring round the middle
					boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2 && (dx == 0 || dz == 0);
					level.setBlock(base.offset(dx, 0, dz), ring ? moonstone : random.nextInt(8) == 0 ? rock : bricks, FLAGS);
					level.setBlock(base.offset(dx, -1, dz), rock, FLAGS);
					for (int dy = 1; dy <= 5; dy++) {
						level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), FLAGS);
					}
				}
			}
			// Pillars at the corners, some broken off, the whole ones capped with glowing moonstone
			for (int sx = -3; sx <= 3; sx += 6) {
				for (int sz = -3; sz <= 3; sz += 6) {
					int height = 1 + random.nextInt(4);
					for (int dy = 1; dy <= height; dy++) {
						level.setBlock(base.offset(sx, dy, sz), bricks, FLAGS);
					}
					if (height == 4) {
						level.setBlock(base.offset(sx, 5, sz), moonstone, FLAGS);
					}
				}
			}
			// An old lantern on a pedestal in the middle, and the shrine's chest before it
			level.setBlock(base.above(), moonstone, FLAGS);
			RingColor[] colors = RingColor.values();
			level.setBlock(base.above(2), Rings.lantern(colors[random.nextInt(colors.length)]).defaultBlockState(), FLAGS);
			chest(level, random, base.offset(0, 1, 2), Direction.SOUTH, SHRINE_LOOT);
			return true;
		}
	}

	/** A Manhunter scout, crashed and half buried on Mars. */
	public record MarsWreck() implements Feature {
		public static final MapCodec<MarsWreck> CODEC = MapCodec.unit(new MarsWreck());

		@Override
		public MapCodec<MarsWreck> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos base = ground(level, origin);
			boolean alongX = random.nextBoolean();
			// A tilted pod, nose in the sand
			for (int a = -4; a <= 4; a++) {
				for (int b = -3; b <= 3; b++) {
					for (int dy = -3; dy <= 3; dy++) {
						double v = dy + 0.3 * a;
						double outer = (a / 4.2) * (a / 4.2) + (b / 2.2) * (b / 2.2) + (v / 1.9) * (v / 1.9);
						if (outer > 1.0) {
							continue;
						}
						double inner = (a / 3.1) * (a / 3.1) + (b / 1.2) * (b / 1.2) + (v / 0.95) * (v / 0.95);
						BlockPos pos = alongX ? base.offset(a, dy, b) : base.offset(b, dy, a);
						boolean torn = b > 0 && a > -2 && a < 2 && v > -0.5;
						BlockState state = inner <= 1.0 || torn ? Blocks.AIR.defaultBlockState()
								: Math.abs(v) < 0.4 && Math.abs(b) >= 2 ? ShipBlocks.SHIP_LIGHT.defaultBlockState()
								: random.nextInt(4) == 0 ? ShipBlocks.SHIP_PLATING.defaultBlockState() : ShipBlocks.SHIP_HULL.defaultBlockState();
						level.setBlock(pos, state, FLAGS);
					}
				}
			}
			// Sand blown in round it
			for (int i = 0; i < 12; i++) {
				BlockPos pos = base.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
				if (level.getBlockState(pos).isAir()) {
					level.setBlock(pos, Blocks.RED_SAND.defaultBlockState(), FLAGS);
				}
			}
			// The salvage, on the floor inside
			BlockPos inside = base;
			while (inside.getY() > base.getY() - 3 && level.getBlockState(inside.below()).isAir()) {
				inside = inside.below();
			}
			chest(level, random, inside, alongX ? Direction.SOUTH : Direction.EAST, WRECK_LOOT);
			return true;
		}
	}

	private PlanetRuins() {
	}

	static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("moon_shrine"), MoonShrine.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("mars_wreck"), MarsWreck.CODEC);
	}

	/** The top of the ground at a spot. */
	private static BlockPos ground(WorldGenLevel level, BlockPos origin) {
		return new BlockPos(origin.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1, origin.getZ());
	}

	private static void chest(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing, ResourceKey<LootTable> loot) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
	}
}
