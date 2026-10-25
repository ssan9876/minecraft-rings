package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Guardians' citadel on Oa, built the first time anyone lands there: a great round plaza at the middle of the
 * world with the Central Power Battery at its heart (a lantern the size of a building, its core of living light open
 * to the plaza between its pillars), towers standing round it, and the Guardians walking the plaza.
 */
public final class OaCitadel {
	/** Set on Oa once the citadel is built, so it's only built once. */
	public static final AttachmentType<Boolean> BUILT = AttachmentRegistry.create(Rings.id("citadel_built"),
			builder -> builder.persistent(Codec.BOOL));

	private static final int PLAZA_RADIUS = 24;
	private static final int CLEAR_HEIGHT = 32;
	private static final int TOWERS = 6;
	private static final int TOWER_DISTANCE = 34;
	private static final int TOWER_RADIUS = 3;
	private static final int GUARDIANS = 3;
	/** Updates neighbours' shapes and tells clients, but doesn't set off block updates all round as it builds. */
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private OaCitadel() {
	}

	/** Builds the citadel on Oa if it isn't there yet. */
	public static void ensureBuilt(ServerLevel oa) {
		if (oa.getAttachedOrElse(BUILT, false)) {
			return;
		}
		oa.setAttached(BUILT, true);
		build(oa);
	}

	/** Where the plaza's floor is: the ground at the middle of the world. */
	public static int groundY(ServerLevel oa) {
		oa.getChunk(0, 0);
		return Mth.clamp(oa.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0), oa.getMinY() + 20, 200);
	}

	private static void build(ServerLevel oa) {
		int ground = groundY(oa);
		plaza(oa, ground);
		battery(oa, ground);
		for (int i = 0; i < TOWERS; i++) {
			double angle = Math.PI * 2 * i / TOWERS + Math.PI / TOWERS;
			tower(oa, (int) Math.round(Math.cos(angle) * TOWER_DISTANCE), (int) Math.round(Math.sin(angle) * TOWER_DISTANCE), ground, 30 + (i % 3) * 8);
		}
		for (int i = 0; i < GUARDIANS; i++) {
			double angle = Math.PI * 2 * i / GUARDIANS;
			OaGuardian guardian = Rings.OA_GUARDIAN.create(oa, EntitySpawnReason.STRUCTURE);
			if (guardian != null) {
				guardian.snapTo(Math.cos(angle) * 10.0 + 0.5, ground + 1, Math.sin(angle) * 10.0 + 0.5, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
				guardian.setHomeTo(new BlockPos(0, ground + 1, 0), PLAZA_RADIUS - 4);
				oa.addFreshEntity(guardian);
			}
		}
	}

	/** A round floor at the ground's height, cleared above and built up below, ringed in green and lit round its edge. */
	private static void plaza(ServerLevel oa, int ground) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -PLAZA_RADIUS - 1; x <= PLAZA_RADIUS + 1; x++) {
			for (int z = -PLAZA_RADIUS - 1; z <= PLAZA_RADIUS + 1; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > PLAZA_RADIUS + 0.5) {
					continue;
				}
				for (int y = ground + 1; y <= ground + CLEAR_HEIGHT; y++) {
					set(oa, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
				}
				BlockState floor = r > PLAZA_RADIUS - 2.5 ? Blocks.CONCRETE.pick(DyeColor.GREEN).defaultBlockState()
						: Math.round(r) % 6 == 0 ? Blocks.EMERALD_BLOCK.defaultBlockState()
						: Blocks.POLISHED_DEEPSLATE.defaultBlockState();
				set(oa, pos.set(x, ground, z), floor);
				for (int y = ground - 1; y >= ground - 8 && !oa.getBlockState(pos.set(x, y, z)).isSolid(); y--) {
					set(oa, pos, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				}
			}
		}
		// Lights round the edge
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			int x = (int) Math.round(Math.cos(angle) * (PLAZA_RADIUS - 1));
			int z = (int) Math.round(Math.sin(angle) * (PLAZA_RADIUS - 1));
			set(oa, pos.set(x, ground + 1, z), Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
			set(oa, pos.set(x, ground + 2, z), Blocks.SEA_LANTERN.defaultBlockState());
		}
	}

	/**
	 * The Central Power Battery: a lantern twenty blocks high on a stepped base, its pillars at the corners and
	 * glass round its upper half, a cap and a handle on top, and its core of living light (the battery blocks) down
	 * the middle, open to the plaza below the glass.
	 */
	private static void battery(ServerLevel oa, int ground) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState core = Rings.POWER_BATTERY.defaultBlockState();
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				// Stepped base
				set(oa, pos.set(x, ground + 1, z), edge == 5 ? Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState());
				if (edge <= 3) {
					set(oa, pos.set(x, ground + 2, z), Blocks.GOLD_BLOCK.defaultBlockState());
				}
				// Pillars at the corners, glass round the upper half
				for (int y = ground + 2; y <= ground + 17; y++) {
					boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;
					if (corner) {
						set(oa, pos.set(x, y, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
					} else if (edge == 4 && y >= ground + 10) {
						set(oa, pos.set(x, y, z), y == ground + 10 || y == ground + 17 ? Blocks.GOLD_BLOCK.defaultBlockState()
								: Blocks.STAINED_GLASS.pick(DyeColor.LIME).defaultBlockState());
					}
				}
				// The cap, narrowing up to the handle
				if (edge <= 4) {
					set(oa, pos.set(x, ground + 18, z), edge == 4 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState());
				}
				if (edge <= 3) {
					set(oa, pos.set(x, ground + 19, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				}
				if (edge <= 1) {
					set(oa, pos.set(x, ground + 20, z), Blocks.SEA_LANTERN.defaultBlockState());
				}
			}
		}
		// The core of living light
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = ground + 3; y <= ground + 16; y++) {
					set(oa, pos.set(x, y, z), core);
				}
			}
		}
		// The handle: an arch over the top
		for (int x = -3; x <= 3; x++) {
			int height = Math.abs(x) == 3 ? 3 : Math.abs(x) == 2 ? 5 : 6;
			for (int y = 1; y <= height; y++) {
				if (Math.abs(x) == 3 || y == height) {
					set(oa, pos.set(x, ground + 20 + y, 0), Blocks.DEEPSLATE_TILES.defaultBlockState());
				}
			}
		}
	}

	/** A round tower of deepslate tiles, banded with windows of green light, with a glowing spire. */
	private static void tower(ServerLevel oa, int cx, int cz, int plaza, int height) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		oa.getChunk(cx >> 4, cz >> 4);
		int ground = Math.min(plaza, oa.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz)) - 6;
		int top = plaza + height;
		for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
			for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > TOWER_RADIUS + 0.4) {
					continue;
				}
				boolean wall = r > TOWER_RADIUS - 0.6;
				for (int y = ground; y <= top; y++) {
					BlockState state;
					if (!wall && y > plaza) {
						state = Blocks.AIR.defaultBlockState();
					} else if (wall && y > plaza && (y - plaza) % 6 == 3) {
						state = Blocks.STAINED_GLASS.pick(DyeColor.LIME).defaultBlockState();
					} else {
						state = Blocks.DEEPSLATE_TILES.defaultBlockState();
					}
					set(oa, pos.set(cx + dx, y, cz + dz), state);
				}
				set(oa, pos.set(cx + dx, top + 1, cz + dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			}
		}
		// The spire
		for (int y = 2; y <= 7; y++) {
			int width = y < 4 ? 1 : 0;
			for (int dx = -width; dx <= width; dx++) {
				for (int dz = -width; dz <= width; dz++) {
					set(oa, pos.set(cx + dx, top + y, cz + dz), Blocks.STAINED_GLASS.pick(DyeColor.LIME).defaultBlockState());
				}
			}
		}
		set(oa, pos.set(cx, top + 8, cz), Blocks.SEA_LANTERN.defaultBlockState());
		set(oa, pos.set(cx, top + 2, cz), Blocks.SEA_LANTERN.defaultBlockState());
	}

	private static void set(ServerLevel oa, BlockPos pos, BlockState state) {
		oa.setBlock(pos, state, FLAGS);
	}
}
