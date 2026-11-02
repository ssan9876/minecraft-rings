package xyz.ssander.rings;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A relic of the first Lanterns, from the Moon's ancient shrines, Manhunter wrecks on Mars, the Guardians' missions and
 * the Devourer: used, it gives one more skill point, whatever the ring's level.
 */
public class OanRelicItem extends Item {
	public OanRelicItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			player.getItemInHand(hand).consume(1, player);
			Mastery.addBonusPoint(serverPlayer);
			serverPlayer.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
			serverPlayer.level().sendParticles(RingColor.GREEN.dust(0.5F, 1.3F), player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.9, 0.4, 0.0);
			serverPlayer.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
			serverPlayer.sendSystemMessage(Component.translatable("message.rings.relic_used", Mastery.of(serverPlayer).points()).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.rings.oan_relic").withStyle(ChatFormatting.GRAY));
	}
}
