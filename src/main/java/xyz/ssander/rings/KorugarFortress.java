package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;

import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * Korugar, the violet world: home of the yellow light. At its middle stands the Fortress of Fear, built the first time
 * anyone lands: a black plaza banded in gold, four jagged spires round it, and at its heart the Fear Battery, a lantern
 * of yellow light that fills a yellow ring at once and turns on any other that touches it. Chests of the fortress's
 * treasure stand at the plaza's edge.
 */
public final class KorugarFortress {
	public static final AttachmentType<Boolean> BUILT = AttachmentRegistry.create(Rings.id("fortress_built"),
			builder -> builder.persistent(Codec.BOOL));
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Rings.id("chests/korugar_fortress"));

	private static final int PLAZA_RADIUS = 18;
	private static final int CLEAR_HEIGHT = 28;
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private KorugarFortress() {
	}

	/** Registers the built marker at startup, so it's kept when Korugar's data loads. */
	static void init() {
	}

	public static void ensureBuilt(ServerLevel korugar) {
		if (korugar.getAttachedOrElse(BUILT, false)) {
			return;
		}
		korugar.setAttached(BUILT, true);
		int ground = groundY(korugar);
		plaza(korugar, ground);
		battery(korugar, ground);
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i + Math.PI / 4;
			spire(korugar, (int) Math.round(Math.cos(angle) * 26), (int) Math.round(Math.sin(angle) * 26), ground, 26 + i * 5);
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos pos = new BlockPos(side.getStepX() * (PLAZA_RADIUS - 3), ground + 1, side.getStepZ() * (PLAZA_RADIUS - 3));
			korugar.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, side.getOpposite()), FLAGS);
			RandomizableContainer.setBlockEntityLootTable(korugar, korugar.getRandom(), pos, LOOT);
		}
	}

	public static int groundY(ServerLevel korugar) {
		korugar.getChunk(0, 0);
		return Mth.clamp(korugar.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0), korugar.getMinY() + 20, 200);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, FLAGS);
	}

	private static void plaza(ServerLevel level, int ground) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -PLAZA_RADIUS - 1; x <= PLAZA_RADIUS + 1; x++) {
			for (int z = -PLAZA_RADIUS - 1; z <= PLAZA_RADIUS + 1; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > PLAZA_RADIUS + 0.5) {
					continue;
				}
				for (int y = ground + 1; y <= ground + CLEAR_HEIGHT; y++) {
					set(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
				}
				BlockState floor = r > PLAZA_RADIUS - 2.5 ? Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState()
						: Math.round(r) % 5 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState()
						: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				set(level, pos.set(x, ground, z), floor);
				for (int y = ground - 1; y >= ground - 8 && !level.getBlockState(pos.set(x, y, z)).isSolid(); y--) {
					set(level, pos, Blocks.BLACKSTONE.defaultBlockState());
				}
			}
		}
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12;
			int x = (int) Math.round(Math.cos(angle) * (PLAZA_RADIUS - 1));
			int z = (int) Math.round(Math.sin(angle) * (PLAZA_RADIUS - 1));
			set(level, pos.set(x, ground + 1, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			set(level, pos.set(x, ground + 2, z), Blocks.SHROOMLIGHT.defaultBlockState());
		}
	}

	/** The Fear Battery: a black and gold lantern with a core of yellow light, open to the plaza. */
	private static void battery(ServerLevel level, int ground) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				set(level, pos.set(x, ground + 1, z), edge == 4 ? Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
				for (int y = ground + 2; y <= ground + 14; y++) {
					boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
					if (corner) {
						set(level, pos.set(x, y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					} else if (edge == 3 && y >= ground + 8) {
						set(level, pos.set(x, y, z), y == ground + 8 || y == ground + 14 ? Blocks.GOLD_BLOCK.defaultBlockState()
								: Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState());
					}
				}
				if (edge <= 3) {
					set(level, pos.set(x, ground + 15, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				}
				if (edge <= 1) {
					set(level, pos.set(x, ground + 16, z), Blocks.SHROOMLIGHT.defaultBlockState());
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = ground + 2; y <= ground + 13; y++) {
					set(level, pos.set(x, y, z), Rings.FEAR_BATTERY.defaultBlockState());
				}
			}
		}
	}

	/** A jagged black spire, banded in gold, a glow at its tip. */
	private static void spire(ServerLevel level, int cx, int cz, int ground, int height) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y <= height; y++) {
			double radius = 3.0 * (1.0 - (double) y / (height + 2)) + 0.6;
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (x * x + z * z <= radius * radius) {
						set(level, pos.set(cx + x, ground + y, cz + z), y % 7 == 3 ? Blocks.GOLD_BLOCK.defaultBlockState()
								: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					}
				}
			}
		}
		set(level, pos.set(cx, ground + height + 1, cz), Blocks.SHROOMLIGHT.defaultBlockState());
	}

	/** The Fear Battery's core: yellow light that fills a yellow ring at once, and turns on any other ring. */
	public static class FearBatteryBlock extends Block {
		public FearBatteryBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack worn = RingAttachments.getRing(serverPlayer);
			ItemStack stack = worn.getItem() instanceof RingItem ? worn : serverPlayer.getMainHandItem();
			if (!(stack.getItem() instanceof RingItem ring)) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.rings.charge_no_ring"));
				return InteractionResult.SUCCESS;
			}
			if (ring.color() != RingColor.YELLOW) {
				// Fear turns on the rings of every other light
				RingItem.addCharge(stack, -RingItem.MAX_CHARGE / 10);
				serverPlayer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
				serverPlayer.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
				serverLevel.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.BLOCKS, 0.8F, 1.4F);
				serverPlayer.sendOverlayMessage(Component.translatable("message.rings.fear_battery_rejects", stack.getHoverName()));
				return InteractionResult.SUCCESS;
			}
			RingItem.addCharge(stack, RingItem.MAX_CHARGE);
			RingPowers.playAnimation(serverPlayer, RingAnimationPayload.Kind.CHARGE_FULL, RingColor.YELLOW);
			serverLevel.sendParticles(RingColor.YELLOW.dust(0.5F, 1.6F), serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(), 80, 0.6, 1.0, 0.6, 0.0);
			serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.8F);
			serverPlayer.sendOverlayMessage(Component.translatable("message.rings.battery_charged", stack.getHoverName()));
			Codex.discover(serverPlayer, "fear_battery");
			return InteractionResult.SUCCESS;
		}
	}
}
