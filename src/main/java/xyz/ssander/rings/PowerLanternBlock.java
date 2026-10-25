package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	private static final VoxelShape SHAPE = Shapes.or(
			Block.column(10.0, 0.0, 2.0),
			Block.column(8.0, 2.0, 11.0),
			Block.column(10.0, 11.0, 13.0),
			Block.column(4.0, 13.0, 15.0));

	private final RingColor color;

	public PowerLanternBlock(Properties properties, RingColor color) {
		super(properties);
		this.color = color;
	}

	public RingColor color() {
		return this.color;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			RingPowers.chargeAt(serverPlayer, serverLevel, pos, this);
		}

		return InteractionResult.SUCCESS;
	}
}
