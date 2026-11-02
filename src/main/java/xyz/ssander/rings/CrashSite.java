package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where a ring comes from: a ring-bearer's ship, crashed. A scorched crater with a furrow ploughed into the ground
 * behind it, the wrecked ship nose-down in the middle (hull split open on one side, the canopy cracked, a wing torn
 * off and lying in the furrow, the engine still glowing), smoke rising from its fires, and beside the breach the
 * dying pilot with their ring, and their lantern. The ship's console still holds its flight recorder.
 *
 * <p>The same site is generated with the world, very rarely ({@link Feature}, placed by
 * {@code worldgen/placed_feature/crash_site.json}), and built on the spot when a ship falls out of the sky (Crashes).
 */
public final class CrashSite {
	/** The crater's radius and depth, and how far the scorched ground reaches. */
	private static final int RADIUS = 8;
	private static final int DEPTH = 3;
	private static final int SCORCH = 13;
	/** The ship: its half length, half width and half height, and how far its middle sits ahead of the crater's. */
	private static final double HULL_LENGTH = 6.5;
	private static final double HULL_WIDTH = 2.8;
	private static final double HULL_HEIGHT = 2.0;
	private static final int HULL_AHEAD = 2;
	/** How steeply it's nosed into the ground: the drop for each block along it. */
	private static final double TILT = 0.22;
	/** The steepest ground the world puts a crash on: the most the ground round the middle may differ from it. */
	private static final int MAX_SLOPE = 6;

	/** The crash sites the world generates. */
	public record Feature() implements net.minecraft.world.level.levelgen.feature.Feature {
		public static final MapCodec<Feature> CODEC = MapCodec.unit(new Feature());

		@Override
		public MapCodec<Feature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			return build(level, origin, random, pickColor(random, level.getBiome(origin)), Heightmap.Types.WORLD_SURFACE_WG, Block.UPDATE_CLIENTS, true) != null;
		}
	}

	private CrashSite() {
	}

	static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Rings.id("crash_site"), Feature.CODEC);
	}

	/**
	 * Which colour of ring-bearer crashed, by where: green most often, blue, red, pink and orange anywhere, and the
	 * rarer three only in their own lands: white in the snow, yellow in deserts and badlands, black in dark forests and
	 * swamps (where each is as likely as green).
	 */
	public static RingColor pickColor(RandomSource random, Holder<Biome> biome) {
		List<RingColor> pool = new ArrayList<>(List.of(RingColor.GREEN, RingColor.GREEN, RingColor.GREEN, RingColor.GREEN,
				RingColor.BLUE, RingColor.RED, RingColor.PINK, RingColor.ORANGE));
		RingColor special = special(biome);
		if (special != null) {
			for (int i = 0; i < 4; i++) {
				pool.add(special);
			}
		}
		return pool.get(random.nextInt(pool.size()));
	}

	/** The rare colour whose bearers fall in the biome, if any. */
	public static @Nullable RingColor special(Holder<Biome> biome) {
		if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return RingColor.BLACK;
		}
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			return RingColor.YELLOW;
		}
		if (biome.value().coldEnoughToSnow(BlockPos.ZERO.atY(64), 64)) {
			return RingColor.WHITE;
		}
		return null;
	}

	/**
	 * Builds a crash site around a spot (only its x and z count: the site sits on the ground there). Returns where the
	 * pilot sits, or null when the spot won't do: water, or (when {@code picky}, as the world generates) too steep.
	 */
	public static @Nullable BlockPos build(WorldGenLevel level, BlockPos spot, RandomSource random, RingColor color,
			Heightmap.Types surface, int flags, boolean picky) {
		int baseY = ground(level, surface, spot.getX(), spot.getZ());
		BlockPos centre = new BlockPos(spot.getX(), baseY, spot.getZ());
		if (!level.getFluidState(centre).isEmpty() || !level.getFluidState(centre.above()).isEmpty() || baseY <= level.getMinY() + 8) {
			return null;
		}
		if (picky) {
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI * i / 4.0;
				int x = spot.getX() + (int) Math.round(Math.cos(angle) * RADIUS);
				int z = spot.getZ() + (int) Math.round(Math.sin(angle) * RADIUS);
				int y = ground(level, surface, x, z);
				if (Math.abs(y - baseY) > MAX_SLOPE || !level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
					return null;
				}
			}
		}

		Site site = new Site(level, centre, Direction.Plane.HORIZONTAL.getRandomDirection(random), random, flags);
		site.crater(surface);
		site.hull();
		site.wings();
		site.engine();
		site.debris(surface);
		site.console(color);
		return site.pilot(color);
	}

	/** The height of the ground at a column: under any trees, plants, snow and water. */
	private static int ground(WorldGenLevel level, Heightmap.Types surface, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(surface, x, z), z);
		while (pos.getY() > level.getMinY() && !isGround(level.getBlockState(pos))) {
			pos.move(Direction.DOWN);
		}
		return pos.getY();
	}

	private static boolean isGround(BlockState state) {
		return !state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()
				&& state.getFluidState().isEmpty() && !state.is(Blocks.BAMBOO) && !state.is(Blocks.CACTUS);
	}

	/** One site being built: positions along the ship ({@code a}, toward where it was flying) and across it. */
	private static final class Site {
		private final WorldGenLevel level;
		private final BlockPos centre;
		private final Direction forward;
		private final Direction side;
		private final RandomSource random;
		private final int flags;
		/** The ship's middle, in height. */
		private final int hullY;

		Site(WorldGenLevel level, BlockPos centre, Direction forward, RandomSource random, int flags) {
			this.level = level;
			this.centre = centre;
			this.forward = forward;
			this.side = forward.getClockWise();
			this.random = random;
			this.flags = flags;
			this.hullY = centre.getY() - DEPTH + 2;
		}

		BlockPos at(int along, int across, int y) {
			return new BlockPos(
					this.centre.getX() + this.forward.getStepX() * along + this.side.getStepX() * across,
					y,
					this.centre.getZ() + this.forward.getStepZ() * along + this.side.getStepZ() * across);
		}

		void set(BlockPos pos, BlockState state) {
			this.level.setBlock(pos, state, this.flags);
		}

		/** The crater, its raised rim, the scorched ground round it and the furrow behind. */
		void crater(Heightmap.Types surface) {
			int baseY = this.centre.getY();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -SCORCH; dx <= SCORCH; dx++) {
				for (int dz = -SCORCH; dz <= SCORCH; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					int along = dx * this.forward.getStepX() + dz * this.forward.getStepZ();
					int across = dx * this.side.getStepX() + dz * this.side.getStepZ();
					boolean furrow = along < -RADIUS + 2 && Math.abs(across) <= 1;
					if (d > SCORCH && !furrow) {
						continue;
					}
					int x = this.centre.getX() + dx;
					int z = this.centre.getZ() + dz;
					int top = this.level.getHeight(surface, x, z);
					int ground = ground(this.level, surface, x, z);
					if (d < RADIUS) {
						// The bowl: everything above its floor blasted away, trees and all
						double t = d / RADIUS;
						int floor = baseY - Mth.floor(DEPTH * (1.0 - t * t));
						for (int y = Math.max(top, baseY + 4); y > floor; y--) {
							this.set(pos.set(x, y, z), Blocks.AIR.defaultBlockState());
						}
						this.set(pos.set(x, floor, z), this.scorched(t));
						// Solid under it, so nothing falls into a cave
						for (int y = floor - 1; y >= floor - 3; y--) {
							if (!isGround(this.level.getBlockState(pos.set(x, y, z)))) {
								this.set(pos, Blocks.COARSE_DIRT.defaultBlockState());
							}
						}
					} else if (furrow && along >= -SCORCH - 2) {
						// Ploughed in behind the crater
						this.clearPlants(x, ground + 1, z);
						this.set(pos.set(x, ground, z), Blocks.AIR.defaultBlockState());
						this.set(pos.set(x, ground - 1, z), this.scorched(0.6));
					} else if (d < RADIUS + 2.5 && Math.abs(ground - baseY) <= 4) {
						// The rim, thrown up round the bowl
						this.clearPlants(x, ground + 1, z);
						this.set(pos.set(x, ground, z), this.scorched(0.9));
						if (d < RADIUS + 1.5) {
							this.set(pos.set(x, ground + 1, z), this.random.nextInt(3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
						}
					} else if (this.random.nextDouble() < 1.0 - (d - RADIUS) / (SCORCH - RADIUS + 1)) {
						// Scorched ground, thinning out
						BlockState was = this.level.getBlockState(pos.set(x, ground, z));
						if (was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(Blocks.SNOW_BLOCK)) {
							this.clearPlants(x, ground + 1, z);
							this.set(pos, this.scorched(1.0));
						}
					}
				}
			}
			// Molten spots in the floor
			for (int i = 0; i < 6; i++) {
				int dx = this.random.nextInt(RADIUS * 2 - 3) - RADIUS + 2;
				int dz = this.random.nextInt(RADIUS * 2 - 3) - RADIUS + 2;
				double t = Math.sqrt(dx * dx + dz * dz) / RADIUS;
				if (t < 0.85) {
					this.set(new BlockPos(this.centre.getX() + dx, baseY - Mth.floor(DEPTH * (1.0 - t * t)), this.centre.getZ() + dz),
							Blocks.MAGMA_BLOCK.defaultBlockState());
				}
			}
		}

		/** Burnt ground: darkest in the middle ({@code t} 0), patchier out at the edge (1). */
		BlockState scorched(double t) {
			double roll = this.random.nextDouble();
			if (roll < 0.25 * (1.0 - t)) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (roll < 0.4) {
				return Blocks.GRAVEL.defaultBlockState();
			}
			if (roll < 0.47) {
				return Blocks.TUFF.defaultBlockState();
			}
			return Blocks.COARSE_DIRT.defaultBlockState();
		}

		void clearPlants(int x, int y, int z) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
			for (int i = 0; i < 3; i++) {
				BlockState state = this.level.getBlockState(pos);
				if (state.isAir() || !state.canBeReplaced() && !state.is(BlockTags.FLOWERS)) {
					break;
				}
				this.set(pos, Blocks.AIR.defaultBlockState());
				pos.move(Direction.UP);
			}
		}

		/** How far into the hull a point is: 1 at its skin, less inside. {@code a} is measured from the ship's middle. */
		static double hullShape(double a, double across, double y, double length, double width, double height) {
			double v = y + TILT * a;
			return (a / length) * (a / length) + (across / width) * (across / width) + (v / height) * (v / height);
		}

		boolean breach(double a, double across, double v) {
			return across > 0.5 && a > -2.5 && a < 1.5 && v > -0.9 && v < 1.6;
		}

		/** The hull: a shell nosed into the ground, split open on one side, its canopy cracked. */
		void hull() {
			for (int along = -8; along <= 10; along++) {
				for (int across = -4; across <= 4; across++) {
					for (int dy = -4; dy <= 4; dy++) {
						double a = along - HULL_AHEAD;
						double outer = hullShape(a, across, dy, HULL_LENGTH, HULL_WIDTH, HULL_HEIGHT);
						if (outer > 1.0) {
							continue;
						}
						double inner = hullShape(a, across, dy, HULL_LENGTH - 1.1, HULL_WIDTH - 1.0, HULL_HEIGHT - 0.95);
						double v = dy + TILT * a;
						BlockPos pos = this.at(along, across, this.hullY + dy);
						if (inner <= 1.0 || this.breach(a, across, v)) {
							this.set(pos, Blocks.AIR.defaultBlockState());
							continue;
						}
						BlockState state;
						if (a > 2.2 && v > 0.3) {
							// The canopy, cracked through in places
							state = this.random.nextInt(6) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState();
						} else if (Math.abs(across) >= 2 && Math.abs(v) < 0.5 && Math.floorMod(along, 2) == 0) {
							state = ShipBlocks.SHIP_LIGHT.defaultBlockState();
						} else if (v > 1.2 && Math.abs(across) <= 0) {
							state = ShipBlocks.SHIP_PLATING.defaultBlockState();
						} else {
							state = this.random.nextInt(9) == 0 ? ShipBlocks.SHIP_PLATING.defaultBlockState() : ShipBlocks.SHIP_HULL.defaultBlockState();
						}
						this.set(pos, state);
					}
				}
			}
		}

		/** Swept-back wings: the one on the breached side torn off near the root. */
		void wings() {
			for (int sign = -1; sign <= 1; sign += 2) {
				int reach = sign > 0 ? 4 : 6;
				for (int out = 3; out <= reach; out++) {
					int sweep = out - 3;
					for (int along = -3 - sweep; along <= -1 - sweep / 2; along++) {
						double a = along - HULL_AHEAD;
						int y = this.hullY + (int) Math.round(-0.4 - TILT * a);
						boolean tip = out == reach && sign < 0;
						this.set(this.at(along, sign * out, y), tip ? ShipBlocks.SHIP_LIGHT.defaultBlockState() : ShipBlocks.SHIP_PLATING.defaultBlockState());
					}
				}
			}
		}

		/** The engine at the back, still glowing, and smoking. */
		void engine() {
			int along = (int) -Math.ceil(HULL_LENGTH) + HULL_AHEAD - 1;
			double a = along - HULL_AHEAD;
			int y = this.hullY + (int) Math.round(-TILT * a);
			for (int across = -1; across <= 1; across++) {
				for (int dy = -1; dy <= 1; dy++) {
					boolean middle = across == 0 && dy == 0;
					this.set(this.at(along, across, y + dy), middle ? Blocks.MAGMA_BLOCK.defaultBlockState() : ShipBlocks.SHIP_LIGHT.defaultBlockState());
				}
			}
			this.smoke(this.at(along, 0, y + 2));
		}

		/** A fire that sends up a tall column of smoke, to be seen from far off. */
		void smoke(BlockPos pos) {
			this.set(pos.below(), Blocks.HAY_BLOCK.defaultBlockState());
			this.set(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
		}

		/** Pieces of the ship strewn along the furrow, the torn-off wing among them, and another fire. */
		void debris(Heightmap.Types surface) {
			for (int i = 0; i < 9; i++) {
				int along = -RADIUS - this.random.nextInt(5);
				int across = this.random.nextInt(9) - 4;
				BlockPos column = this.at(along, across, 0);
				int y = ground(this.level, surface, column.getX(), column.getZ()) + 1;
				if (this.level.getBlockState(column.atY(y)).canBeReplaced()) {
					this.set(column.atY(y), this.random.nextBoolean() ? ShipBlocks.SHIP_HULL.defaultBlockState() : ShipBlocks.SHIP_PLATING.defaultBlockState());
				}
			}
			// The wing, flat on the ground just inside the crater
			for (int along = -RADIUS + 2; along <= -RADIUS + 4; along++) {
				for (int across = 3; across <= 4; across++) {
					BlockPos column = this.at(along, across, 0);
					int y = ground(this.level, surface, column.getX(), column.getZ()) + 1;
					this.set(column.atY(y), along == -RADIUS + 4 && across == 4 ? ShipBlocks.SHIP_LIGHT.defaultBlockState() : ShipBlocks.SHIP_PLATING.defaultBlockState());
				}
			}
			BlockPos fire = this.at(-RADIUS + 1, -3, 0);
			this.smoke(fire.atY(ground(this.level, surface, fire.getX(), fire.getZ()) + 1));
		}

		/** The console, inside the ship against the far wall, facing the breach. */
		void console(RingColor color) {
			int along = HULL_AHEAD;
			int across = -1;
			BlockPos pos = this.floorInside(along, across);
			this.set(pos, ShipBlocks.SHIP_CONSOLE.defaultBlockState()
					.setValue(ShipBlocks.ConsoleBlock.FACING, this.side)
					.setValue(ShipBlocks.ConsoleBlock.COLOR, color));
		}

		/** The lowest empty spot inside the hull at a place along and across it. */
		BlockPos floorInside(int along, int across) {
			for (int dy = -4; dy <= 3; dy++) {
				BlockPos pos = this.at(along, across, this.hullY + dy);
				if (this.level.getBlockState(pos).isAir() && !this.level.getBlockState(pos.below()).isAir()) {
					return pos;
				}
			}
			return this.at(along, across, this.hullY);
		}

		/** The pilot, slumped against the hull outside the breach, with their lantern beside them. */
		BlockPos pilot(RingColor color) {
			int along = HULL_AHEAD - 1;
			int across = (int) Math.ceil(HULL_WIDTH) + 1;
			BlockPos seat = this.standing(along, across);
			// Level ground round them, out from the hull, so the bowl's slope doesn't hide them
			for (int a = along - 1; a <= along + 2; a++) {
				for (int c = across; c <= across + 3; c++) {
					BlockPos pos = this.at(a, c, seat.getY());
					this.set(pos.below(), this.scorched(0.5));
					this.set(pos, Blocks.AIR.defaultBlockState());
					this.set(pos.above(), Blocks.AIR.defaultBlockState());
				}
			}
			BlockPos lantern = this.at(along + 2, across, seat.getY());
			this.set(lantern, Rings.lantern(color).defaultBlockState());

			CrashPilot pilot = CrashPilot.of(this.level.getLevel(), color);
			float yaw = this.side.toYRot();
			pilot.snapTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, yaw, 0.0F);
			pilot.setYHeadRot(yaw);
			pilot.setYBodyRot(yaw);
			this.level.addFreshEntity(pilot);
			return seat;
		}

		/** Where something can stand at a place: on the first solid block down from above the ship. */
		BlockPos standing(int along, int across) {
			BlockPos.MutableBlockPos pos = this.at(along, across, this.hullY + 5).mutable();
			while (pos.getY() > this.level.getMinY() && this.level.getBlockState(pos.below()).isAir()) {
				pos.move(Direction.DOWN);
			}
			// Clear room to sit
			this.set(pos, Blocks.AIR.defaultBlockState());
			this.set(pos.above(), Blocks.AIR.defaultBlockState());
			return pos.immutable();
		}
	}
}
