package xyz.ssander.rings;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * The ring looking after its wearer: the force field and bubbles that take hits in place of whoever's inside (paid
 * for out of the ring's charge), and the last-ditch save from a hit that would have killed them.
 */
public final class RingProtection {
	/** Charge the force field uses per point of damage it stops. */
	private static final float SHIELD_COST_PER_DAMAGE = 3.0F;
	/** Charge spent saving the wearer from a fatal hit (a quarter of a full ring). A ring with less can't. */
	public static final int SAVE_COST = RingItem.MAX_CHARGE / 4;
	/** Health the wearer is left with after a save. */
	private static final float SAVED_HEALTH = 6.0F;

	private RingProtection() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RingProtection::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(RingProtection::allowDeath);
	}

	public static boolean isShielded(Player player) {
		return player.getAttachedOrElse(RingAttachments.SHIELD, false) && RingAttachments.isActive(player);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// Flying into a wall in elytra mode doesn't hurt: the ring's energy takes the knock
		if (source.is(DamageTypes.FLY_INTO_WALL) && entity instanceof Player player && RingAttachments.isActive(player)) {
			return false;
		}

		Player guard = guardOf(entity);
		if (guard == null) {
			return true;
		}
		ItemStack ring = RingAttachments.getRing(guard);
		int cost = Math.max(1, Mth.ceil(amount * SHIELD_COST_PER_DAMAGE));
		if (RingItem.charge(ring) < cost) {
			return true;
		}
		RingItem.addCharge(ring, -cost);
		RingColor color = RingAttachments.activeColor(guard);
		if (entity.level() instanceof ServerLevel level && color != null) {
			// A flash on the field where it was struck
			Vec3 at = source.getSourcePosition() != null
					? entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0).lerp(source.getSourcePosition(), 0.3)
					: entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
			level.sendParticles(color.dust(0.6F, 1.2F), at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.0);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
		}
		return false;
	}

	/** Whose ring is shielding the entity, if anyone's: its own force field, or the bubble it's carried in. */
	private static @Nullable Player guardOf(LivingEntity entity) {
		if (entity instanceof Player player && isShielded(player)) {
			return player;
		}
		if (entity.getVehicle() instanceof ForceBubble bubble) {
			return bubble.owner();
		}
		return null;
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !RingAttachments.isActive(player)) {
			return true;
		}
		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem ringItem) || RingItem.charge(ring) < SAVE_COST) {
			return true;
		}

		RingItem.addCharge(ring, -SAVE_COST);
		player.setHealth(SAVED_HEALTH);
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));

		ServerLevel level = player.level();
		RingColor color = ringItem.color();
		level.sendParticles(color.dust(0.3F, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(color.dust(0.9F, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.3, 0.6, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
		RingPowers.playAnimation(player, RingAnimationPayload.Kind.CHARGE_FULL, color);
		player.sendOverlayMessage(Component.translatable("message.rings.saved", ring.getHoverName()));
		return false;
	}
}
