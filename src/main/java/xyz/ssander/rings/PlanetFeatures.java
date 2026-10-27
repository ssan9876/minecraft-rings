package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The planets' own landmarks, generated with their terrain: craters on the Moon, rock spires on Mars, and patches of
 * glowing crystal. Where and how often each appears is in the planets' biome files (see tools/gen_worlds.py).
 */
public final class PlanetFeatures {
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** A bowl blasted into the Moon's dust, with a raised rim, bare rock at the bottom and moonstone glinting in it. */
	public record Crater() implements Feature {
		public static final MapCodec<Crater> CODEC = MapCodec.unit(new Crater());

		@Override
		public MapCodec<Crater> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int radius = 4 + random.nextInt(7);
			double depth = radius * 0.45 + random.nextDouble();
			double rimHeight = 1.0 + radius / 5.0;
			BlockState dust = PlanetBlocks.MOON_DUST.defaultBlockState();
			BlockState rock = PlanetBlocks.MOON_ROCK.defaultBlockState();
			int reach = radius + 2;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > reach) {
						continue;
					}
					int x = origin.getX() + dx;
					int z = origin.getZ() + dz;
					int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
					if (d < radius) {
						// The bowl, its floor measured down from the crater's middle
						double t = d / radius;
						int floor = Mth.floor(origin.getY() - 1 - depth * (1.0 - t * t));
						for (int y = surface; y > floor; y--) {
							level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
						}
						BlockState bottom = t < 0.6 ? rock : dust;
						if (t < 0.45 && random.nextFloat() < 0.08F) {
							bottom = PlanetBlocks.MOONSTONE_ORE.defaultBlockState();
						}
						level.setBlock(pos.set(x, floor, z), bottom, FLAGS);
						level.setBlock(pos.set(x, floor - 1, z), rock, FLAGS);
					} else {
						// The rim, thrown up round the edge and sloping away outside it
						double rise = rimHeight * (1.0 - Math.abs(d - radius - 0.5) / 1.6);
						for (int up = 1; up <= Math.round(rise); up++) {
							level.setBlock(pos.set(x, surface + up, z), dust, FLAGS);
						}
					}
				}
			}
			return true;
		}
	}

	/** A tall spire of Martian rock, worn by the wind, banded with sandstone and sometimes capped wider at the top. */
	public record Spire() implements Feature {
		public static final MapCodec<Spire> CODEC = MapCodec.unit(new Spire());

		@Override
		public MapCodec<Spire> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin.below()).isFaceSturdy(level, origin.below(), Direction.UP)) {
				return false;
			}
			int height = 5 + random.nextInt(10);
			double base = 1.4 + random.nextDouble();
			boolean capped = random.nextFloat() < 0.4F;
			BlockState rock = PlanetBlocks.MARS_ROCK.defaultBlockState();
			BlockState band = Blocks.RED_SANDSTONE.defaultBlockState();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int reach = Mth.ceil(base) + 1;
			for (int up = -2; up < height; up++) {
				// Narrowing as it rises, then flaring out under a cap
				double radius = base * (1.0 - 0.55 * Math.max(0, up) / height);
				if (capped && up >= height - 2) {
					radius = base * 0.9;
				}
				BlockState block = up > 0 && up % 3 == 0 ? band : rock;
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						if (dx * dx + dz * dz <= radius * radius + 0.3) {
							level.setBlock(pos.set(origin.getX() + dx, origin.getY() + up, origin.getZ() + dz), block, FLAGS);
						}
					}
				}
			}
			return true;
		}
	}

	/** Crystals growing up off the ground, scattered round about, on the surface or on cave floors. */
	public record CrystalPatch(BlockState crystal, int tries, int spread) implements Feature {
		public static final MapCodec<CrystalPatch> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				BlockState.CODEC.fieldOf("crystal").forGetter(CrystalPatch::crystal),
				Codec.intRange(1, 128).fieldOf("tries").forGetter(CrystalPatch::tries),
				Codec.intRange(0, 8).fieldOf("spread").forGetter(CrystalPatch::spread)
		).apply(instance, CrystalPatch::new));

		@Override
		public MapCodec<CrystalPatch> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockState upright = this.crystal.hasProperty(AmethystClusterBlock.FACING)
					? this.crystal.setValue(AmethystClusterBlock.FACING, Direction.UP) : this.crystal;
			int placed = 0;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int i = 0; i < this.tries; i++) {
				pos.set(origin.getX() + random.nextInt(this.spread * 2 + 1) - this.spread,
						origin.getY() + random.nextInt(5) - 2,
						origin.getZ() + random.nextInt(this.spread * 2 + 1) - this.spread);
				// Down to the floor, if there's one close below
				for (int fall = 0; fall < 8 && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.below()); fall++) {
					pos.move(Direction.DOWN);
				}
				BlockPos below = pos.below();
				if (level.isEmptyBlock(pos) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					level.setBlock(pos, upright, FLAGS);
					placed++;
				}
			}
			return placed > 0;
		}
	}

	private PlanetFeatures() {
	}

	static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("crater"), Crater.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("spire"), Spire.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("crystal_patch"), CrystalPatch.CODEC);
	}
}
