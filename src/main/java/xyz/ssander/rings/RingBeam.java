package xyz.ssander.rings;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ring's energy beam: held down with the beam key while the ring is on, it fires where the wearer looks, hurting
 * and knocking back what it hits. The server decides what's hit; every client traces the same line to draw it.
 */
public final class RingBeam {
	/** How far the beam reaches, in blocks. */
	public static final double RANGE = 40.0;
	/** Damage per hit, and the ticks between hits (vanilla's hurt cooldown lets a hit land every 10 ticks). */
	private static final float DAMAGE = 4.0F;
	private static final int HIT_TICKS = 10;
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Rings.id("ring_beam"));

	/** Where the beam ends: the first block or entity along the wearer's line of sight, or its full reach. */
	public record Hit(Vec3 end, @Nullable Entity entity) {
	}

	private RingBeam() {
	}

	public static Hit trace(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 end = player.level().clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
				.getLocation();

		AABB along = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, along,
				e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
		return entity != null ? new Hit(entity.getLocation(), entity.getEntity()) : new Hit(end, null);
	}

	public static boolean isFiring(Player player) {
		return player.getAttachedOrElse(RingAttachments.FIRING, false);
	}

	/** The wearer pressed or let go of the beam key. */
	public static void setFiring(ServerPlayer player, boolean firing) {
		if (firing == isFiring(player)) {
			return;
		}
		if (!firing) {
			player.removeAttached(RingAttachments.FIRING);
			return;
		}

		ItemStack ring = RingAttachments.getRing(player);
		if (!(ring.getItem() instanceof RingItem) || !RingAttachments.isActive(player)) {
			player.sendOverlayMessage(Component.translatable("message.rings.beam_inactive"));
			return;
		}
		player.setAttached(RingAttachments.FIRING, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2.0F);
	}

	/** Lands the beam's hits. Runs every server tick for players firing it. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % HIT_TICKS != 0) {
			return;
		}

		ServerLevel level = player.level();
		Hit hit = trace(player, 1.0F);
		if (hit.entity() instanceof LivingEntity target && target.isAlive()) {
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), player);
			if (target.hurtServer(level, source, DAMAGE)) {
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6F, 1.6F);
			}
		}
	}
}
