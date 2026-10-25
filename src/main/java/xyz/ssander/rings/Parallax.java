package xyz.ssander.rings;

import java.util.EnumSet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Parallax, fear itself: a vast yellow serpent of fear, long imprisoned in the Central Power Battery on Oa, until a
 * yellow ring set it free. It circles its prey, swoops down on them, spits bolts of fear, and its very presence
 * slows the brave and saps their rings. It feeds on fear, so a yellow ring can't hurt it.
 */
public class Parallax extends SpaceFlyer {
	/** Ticks between volleys of fear, and between swoops. */
	private static final int VOLLEY_TICKS = 50;
	private static final int SWOOP_TICKS = 120;
	private static final float SWOOP_DAMAGE = 10.0F;
	/** How far its fear reaches, and how much it saps from each ring there every second. */
	private static final double FEAR_RANGE = 16.0;
	private static final int FEAR_DRAIN = 6;

	private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.translatable("entity.rings.parallax"),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

	public Parallax(EntityType<? extends Parallax> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 300.0)
				.add(Attributes.FLYING_SPEED, 0.9)
				.add(Attributes.MOVEMENT_SPEED, 0.4)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new AttackGoal());
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// Fear can't harm fear
		if (source.getEntity() instanceof Player player && RingAttachments.activeColor(player) == RingColor.YELLOW) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.rings.parallax_feeds"));
			}
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.tickCount % 20 == 0) {
			// Its fear slows everyone near, and eats away at the will that powers their rings
			for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(FEAR_RANGE), p -> !p.isCreative() && !p.isSpectator())) {
				if (RingAttachments.activeColor(player) == RingColor.YELLOW) {
					continue;
				}
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
				if (RingAttachments.isActive(player)) {
					RingItem.addCharge(RingAttachments.getRing(player), -FEAR_DRAIN);
				}
			}
			level.sendParticles(RingColor.YELLOW.dust(0.2F, 1.6F), this.getX(), this.getY() + 1.2, this.getZ(), 12, 1.5, 1.0, 1.5, 0.0);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENDER_DRAGON_GROWL;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENDER_DRAGON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.rings.parallax_defeated"), false);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	/** Circles the target high up, spits fear at it, and every so often swoops down on it. */
	private final class AttackGoal extends Goal {
		private int volley = VOLLEY_TICKS;
		private int swoop = SWOOP_TICKS;
		private boolean swooping;

		AttackGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Parallax.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Parallax.this.getTarget();
			if (target == null || !(Parallax.this.level() instanceof ServerLevel level)) {
				return;
			}
			Parallax.this.face(target);
			// Angrier as it weakens: faster all round below half health
			boolean enraged = Parallax.this.getHealth() < Parallax.this.getMaxHealth() / 2.0F;
			if (this.swooping) {
				Parallax.this.flyTo(target.position().add(0.0, 1.0, 0.0), enraged ? 2.6 : 2.0);
				if (Parallax.this.getBoundingBox().inflate(1.0).intersects(target.getBoundingBox())) {
					target.hurtServer(level, Parallax.this.damageSources().mobAttack(Parallax.this), SWOOP_DAMAGE);
					Vec3 away = target.position().subtract(Parallax.this.position()).normalize();
					target.push(away.x * 1.5, 0.8, away.z * 1.5);
					target.needsSync = true;
					this.swooping = false;
				} else if (--this.swoop < -60) {
					this.swooping = false;
				}
				if (!this.swooping) {
					this.swoop = enraged ? SWOOP_TICKS / 2 : SWOOP_TICKS;
				}
				return;
			}

			Parallax.this.flyTo(Parallax.this.orbit(target, 14.0, 7.0, enraged ? 0.04F : 0.025F), 1.2);
			if (--this.volley <= 0) {
				this.volley = enraged ? VOLLEY_TICKS / 2 : VOLLEY_TICKS;
				for (int i = -1; i <= 1; i++) {
					Vec3 aim = target.getEyePosition().add(i * 1.5, -0.4, i * 1.5);
					level.addFreshEntity(EnergyBolt.fire(Parallax.this, EnergyBolt.Kind.FEAR, aim));
				}
				level.playSound(null, Parallax.this.getX(), Parallax.this.getY(), Parallax.this.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 2.0F, 1.2F);
			}
			if (--this.swoop <= 0) {
				this.swooping = true;
				this.swoop = 0;
				level.playSound(null, Parallax.this.getX(), Parallax.this.getY(), Parallax.this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.4F);
			}
		}
	}
}
