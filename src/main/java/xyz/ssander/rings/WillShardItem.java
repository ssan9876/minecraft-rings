package xyz.ssander.rings;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import xyz.ssander.rings.network.RingAnimationPayload;

/** A shard of will, from Oa: used, it pours a quarter of a full charge into the ring worn, of any colour. */
public class WillShardItem extends Item {
	public static final int CHARGE = RingItem.MAX_CHARGE / 4;

	public WillShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem ringItem)) {
			player.sendOverlayMessage(Component.translatable("message.rings.shard_no_ring"));
			return InteractionResult.FAIL;
		}
		if (RingItem.charge(ring) >= RingItem.MAX_CHARGE) {
			player.sendOverlayMessage(Component.translatable("message.rings.shard_full", ring.getHoverName()));
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			int charge = RingItem.addCharge(ring, CHARGE);
			player.getItemInHand(hand).consume(1, player);
			RingColor color = ringItem.color();
			serverPlayer.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.2F);
			serverPlayer.level().sendParticles(RingColor.GREEN.dust(0.6F, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.7, 0.4, 0.0);
			serverPlayer.level().sendParticles(color.dust(0.3F, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.0);
			if (charge >= RingItem.MAX_CHARGE) {
				RingPowers.playAnimation(serverPlayer, RingAnimationPayload.Kind.CHARGE_FULL, color);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
