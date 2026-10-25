package xyz.ssander.rings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A tool or weapon made of the ring's light. It lasts while the ring that made it stays on, up to its lifetime, and
 * vanishes if dropped (see ItemEntityMixin). Put away in a chest, it vanishes the next time it's carried.
 */
public class ConstructItem extends Item {
	public ConstructItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		long expires = stack.getOrDefault(RingComponents.CONSTRUCT_EXPIRES, 0L);
		if (!(entity instanceof ServerPlayer player) || !RingAttachments.isActive(player) || level.getGameTime() > expires) {
			level.sendParticles(RingConstructs.colorOf(stack).dust(0.4F, 0.8F), entity.getX(), entity.getY() + 1.0, entity.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
			stack.setCount(0);
		}
	}
}
