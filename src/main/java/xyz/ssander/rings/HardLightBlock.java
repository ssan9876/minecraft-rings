package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Solid light in a ring's colour, the stuff walls, bridges and cages are made of. Each block fades away on its own
 * after its construct's lifetime (a scheduled tick, so it survives the world being saved and loaded) and drops
 * nothing.
 */
public class HardLightBlock extends Block {
	public static final EnumProperty<RingColor> COLOR = EnumProperty.create("color", RingColor.class);

	public HardLightBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, RingColor.GREEN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COLOR);
	}

	/** Puts a block of hard light at the position if it's free, set to fade after the given ticks. */
	public static boolean place(ServerLevel level, BlockPos pos, RingColor color, int lifetime) {
		BlockState there = level.getBlockState(pos);
		if (!there.isAir() && !(there.canBeReplaced() && there.getFluidState().isEmpty())) {
			return false;
		}
		Block block = Rings.HARD_LIGHT;
		level.setBlock(pos, block.defaultBlockState().setValue(COLOR, color), Block.UPDATE_ALL);
		// Staggered a little so a construct crumbles away rather than vanishing all at once
		level.scheduleTick(pos, block, lifetime + level.getRandom().nextInt(10));
		return true;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.removeBlock(pos, false);
		level.sendParticles(state.getValue(COLOR).dust(0.4F, 1.0F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
		if (random.nextInt(6) == 0) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.5F, 1.6F);
		}
	}
}
