package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Charges rings of its colour. Holding right-click on it repeats the interaction every few ticks, and each one tops the
 * ring up a little (see {@link RingPowers#chargeAt}).
 */
public class PowerLanternBlock extends Block {
	/** The plinth, the glass body with its ribs, the cap, and the handle on top. */
	private static final VoxelShape SHAPE = Shapes.or(
			Block.column(12.0, 0.0, 2.5),
			Block.column(9.0, 2.5, 12.5),
			Block.column(7.0, 12.5, 14.5),
			Block.box(5.0, 14.5, 7.5, 11.0, 16.0, 8.5));

	/** Attuned with a Mars crystal: it charges rings of every colour (others at half speed). */
	public static final BooleanProperty ATTUNED = BooleanProperty.create("attuned");

	private final RingColor color;

	public PowerLanternBlock(Properties properties, RingColor color) {
		super(properties);
		this.color = color;
		this.registerDefaultState(this.stateDefinition.any().setValue(ATTUNED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ATTUNED);
	}

	/** A Mars crystal held to the lantern attunes it to the whole spectrum. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(PlanetBlocks.MARS_CRYSTAL.asItem())) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(ATTUNED)) {
			player.sendOverlayMessage(Component.translatable("message.rings.lantern_attuned_already"));
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			level.setBlock(pos, state.setValue(ATTUNED, true), Block.UPDATE_ALL);
			stack.consume(1, player);
			for (RingColor each : RingColor.values()) {
				serverLevel.sendParticles(each.dust(0.3F, 0.9F), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.0);
			}
			serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.rings.lantern_attuned", this.getName()));
		}
		return InteractionResult.SUCCESS;
	}

	public RingColor color() {
		return this.color;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Motes of the lantern's energy rise off it, and now and then one drifts out through the glass. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(this.color.dust(0.5F, 0.35F + random.nextFloat() * 0.25F),
					pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.9, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
					0.0, 0.25, 0.0);
		}
		if (state.getValue(ATTUNED) && random.nextInt(3) == 0) {
			// Attuned, every colour of the spectrum drifts up off it
			RingColor[] colors = RingColor.values();
			level.addParticle(colors[random.nextInt(colors.length)].dust(0.3F, 0.3F),
					pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.5, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
					0.0, 0.2, 0.0);
		}
		if (random.nextInt(8) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			level.addParticle(this.color.dust(0.2F, 0.3F),
					pos.getX() + 0.5 + Math.cos(angle) * 0.3, pos.getY() + 0.3 + random.nextDouble() * 0.45, pos.getZ() + 0.5 + Math.sin(angle) * 0.3,
					Math.cos(angle) * 0.2, 0.05, Math.sin(angle) * 0.2);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			RingPowers.chargeAt(serverPlayer, serverLevel, pos, this, state.getValue(ATTUNED));
		}

		return InteractionResult.SUCCESS;
	}
}
