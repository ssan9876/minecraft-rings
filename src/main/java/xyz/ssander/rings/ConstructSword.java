package xyz.ssander.rings;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The construct sword: a blade of the ring's light that, swung with right-click, sends a slash flying (see SwordWave).
 * With a shield in the other hand, right-click blocks as usual, and sneak and right-click slashes.
 */
public class ConstructSword extends ConstructItem {
	public ConstructSword(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack sword = player.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack ring = RingAttachments.getRing(serverPlayer);
		if (!RingAttachments.isActive(serverPlayer) || RingItem.charge(ring) < SwordWave.COST) {
			return InteractionResult.FAIL;
		}
		RingItem.addCharge(ring, -SwordWave.COST);
		player.getCooldowns().addCooldown(sword, SwordWave.COOLDOWN);
		SwordWave.slash(serverPlayer, RingConstructs.colorOf(sword));
		return InteractionResult.SUCCESS_SERVER;
	}
}
