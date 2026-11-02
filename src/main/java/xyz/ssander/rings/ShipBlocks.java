package xyz.ssander.rings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.OpenDialoguePayload;

/**
 * What a crashed ring-bearer's ship is made of: its alien hull and plating, its glowing running lights, and its
 * console, whose flight recorder tells what happened (right-click it).
 */
public final class ShipBlocks {
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Block SHIP_HULL = register("ship_hull", Block::new, metal(MapColor.COLOR_PURPLE));
	public static final Block SHIP_PLATING = register("ship_plating", Block::new, metal(MapColor.COLOR_GRAY));
	public static final Block SHIP_LIGHT = register("ship_light", Block::new, metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 12).sound(SoundType.GLASS));
	public static final ConsoleBlock SHIP_CONSOLE = register("ship_console", ConsoleBlock::new, metal(MapColor.COLOR_PURPLE).lightLevel(state -> 9).noOcclusion());

	private ShipBlocks() {
	}

	static void init() {
		// Loads the class so the blocks register during mod init
	}

	public static List<Item> items() {
		return ITEMS;
	}

	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(5.0F, 9.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
	}

	private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Rings.id(name));
		T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Rings.id(name));
		ITEMS.add(Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey))));
		return block;
	}

	/**
	 * The ship's console. It faces where it was placed from, keeps the colour of the ring-bearer who flew the ship, and
	 * opens the flight recorder when used: the entries before the crash, and once the pilot's ring has found its new
	 * bearer, the last one.
	 */
	public static class ConsoleBlock extends Block {
		public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
		public static final EnumProperty<RingColor> COLOR = EnumProperty.create("color", RingColor.class);

		public ConsoleBlock(Properties properties) {
			super(properties);
			this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, RingColor.GREEN));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, COLOR);
		}

		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}

		@Override
		protected BlockState mirror(BlockState state, Mirror mirror) {
			return state.rotate(mirror.getRotation(state.getValue(FACING)));
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				List<CrashPilot> pilots = serverLevel.getEntitiesOfClass(CrashPilot.class, new AABB(pos).inflate(32.0), CrashPilot::isAlive);
				boolean waiting = pilots.stream().anyMatch(pilot -> !pilot.ringTaken());
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, new OpenDialoguePayload(Dialogues.Dialogue.SHIP_LOG,
						waiting ? Dialogues.LOG_START : Dialogues.LOG_AFTER, state.getValue(COLOR), -1));
				serverLevel.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.6F);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			// Its screen flickers
			if (random.nextInt(4) == 0) {
				Direction facing = state.getValue(FACING);
				level.addParticle(state.getValue(COLOR).dust(0.6F, 0.4F),
						pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.6,
						pos.getY() + 0.4 + random.nextDouble() * 0.5,
						pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.6,
						0.0, 0.01, 0.0);
			}
		}
	}
}
