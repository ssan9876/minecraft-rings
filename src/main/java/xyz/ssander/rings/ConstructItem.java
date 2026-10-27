package xyz.ssander.rings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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

	/** The construct shield blocked a hit: an attacker up close is thrown back, in a flash of the ring's light. */
	public static void shieldBlocked(ServerLevel level, LivingEntity blocker, DamageSource source) {
		ItemStack shield = blocker.getItemBlockingWith();
		if (shield == null || !shield.is(Rings.CONSTRUCT_SHIELD)) {
			return;
		}
		RingColor color = RingConstructs.colorOf(shield);
		Vec3 front = blocker.getEyePosition().add(blocker.getViewVector(1.0F).scale(0.7)).subtract(0.0, 0.4, 0.0);
		level.sendParticles(color.dust(0.7F, 1.1F), front.x, front.y, front.z, 14, 0.35, 0.35, 0.35, 0.0);
		if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.distanceTo(blocker) < 4.0F) {
			Vec3 away = attacker.position().subtract(blocker.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? blocker.getViewVector(1.0F).multiply(1.0, 0.0, 1.0) : away;
			attacker.push(away.normalize().scale(1.2).add(0.0, 0.35, 0.0));
			attacker.needsSync = true;
			level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
	}
}
