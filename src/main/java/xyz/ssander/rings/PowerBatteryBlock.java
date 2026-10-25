package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * The Central Power Battery, at the heart of the Guardians' citadel on Oa: the source of every lantern's light. Lay a
 * hand on it (right-click) and any ring, of any colour, is charged full at once. It can't be broken. But Parallax is
 * imprisoned inside it, and a yellow ring's touch sets it free.
 */
public class PowerBatteryBlock extends Block {
	public PowerBatteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			charge(serverLevel, pos, serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	private static void charge(ServerLevel level, BlockPos pos, ServerPlayer player) {
		ItemStack worn = RingAttachments.getRing(player);
		ItemStack stack = worn.getItem() instanceof RingItem ? worn : player.getMainHandItem();
		if (!(stack.getItem() instanceof RingItem ring)) {
			player.sendOverlayMessage(Component.translatable("message.rings.charge_no_ring"));
			return;
		}
		if (stack == worn && RingAttachments.activeColor(player) == RingColor.YELLOW) {
			Bosses.freeParallax(level, pos, player);
		}
		if (RingItem.charge(stack) >= RingItem.MAX_CHARGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.already_charged", stack.getHoverName()));
			return;
		}
		RingItem.addCharge(stack, RingItem.MAX_CHARGE);
		RingPowers.playAnimation(player, RingAnimationPayload.Kind.CHARGE_FULL, ring.color());
		level.sendParticles(RingColor.GREEN.dust(0.6F, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(ring.color().dust(0.3F, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.rings.battery_charged", stack.getHoverName()));
	}
}
