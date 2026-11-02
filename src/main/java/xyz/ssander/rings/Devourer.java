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
 * The Devourer: the thing out of the dark between the stars that brought the crashed ring-bearers' ships down. It
 * hunts ring-bearers who have grown strong (ring level 20 and up) through space: a towering shadow that feeds on the
 * light of every ring near it, healing itself on what it eats, hurls bolts of death and lunges at its prey. It drops
 * Oan relics.
 */
public class Devourer extends SpaceFlyer {
	private static final int VOLLEY_TICKS = 45;
	private static final int LUNGE_TICKS = 140;
	private static final float LUNGE_DAMAGE = 14.0F;
	/** How far its hunger reaches, how much it eats from each ring there every second, and what it heals by it. */
	private static final double HUNGER_RANGE = 24.0;
	private static final int HUNGER_DRAIN = 12;
	private static final float HUNGER_HEAL = 3.0F;

	private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.translatable("entity.rings.devourer"),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

	public Devourer(EntityType<? extends Devourer> type, Level level) {
		super(type, level);
		this.xpReward = 500;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 450.0)
				.add(Attributes.FLYING_SPEED, 0.8)
				.add(Attributes.MOVEMENT_SPEED, 0.4)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new AttackGoal());
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.tickCount % 20 == 0) {
			// It eats the light of every ring near it, and grows stronger on it
			for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(HUNGER_RANGE), p -> !p.isCreative() && !p.isSpectator())) {
				if (RingAttachments.isActive(player) && RingItem.charge(RingAttachments.getRing(player)) > 0) {
					RingItem.addCharge(RingAttachments.getRing(player), -HUNGER_DRAIN);
					this.heal(HUNGER_HEAL);
					Vec3 from = player.position().add(0.0, 1.0, 0.0);
					Vec3 to = this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
					for (int i = 0; i < 8; i++) {
						Vec3 point = from.lerp(to, i / 8.0);
						level.sendParticles(RingColor.PINK.dust(0.1F, 0.8F), point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0.0);
					}
				}
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
			}
			level.sendParticles(RingColor.BLACK.dust(0.0F, 2.0F), this.getX(), this.getY() + 2.0, this.getZ(), 16, 1.6, 1.8, 1.6, 0.0);
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
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.rings.devourer_defeated"), false);
		}
	}

	/** Circles its prey, hurls death at it, and every so often lunges. */
	private final class AttackGoal extends Goal {
		private int volley = VOLLEY_TICKS;
		private int lunge = LUNGE_TICKS;
		private boolean lunging;

		AttackGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Devourer.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Devourer.this.getTarget();
			if (target == null || !(Devourer.this.level() instanceof ServerLevel level)) {
				return;
			}
			Devourer.this.face(target);
			boolean starving = Devourer.this.getHealth() < Devourer.this.getMaxHealth() / 2.0F;
			if (this.lunging) {
				Devourer.this.flyTo(target.position().add(0.0, 1.0, 0.0), starving ? 2.4 : 1.8);
				if (Devourer.this.getBoundingBox().inflate(1.0).intersects(target.getBoundingBox())) {
					target.hurtServer(level, Devourer.this.damageSources().mobAttack(Devourer.this), LUNGE_DAMAGE);
					Vec3 away = target.position().subtract(Devourer.this.position()).normalize();
					target.push(away.x * 1.8, 0.6, away.z * 1.8);
					target.needsSync = true;
					this.lunging = false;
				} else if (--this.lunge < -60) {
					this.lunging = false;
				}
				if (!this.lunging) {
					this.lunge = starving ? LUNGE_TICKS / 2 : LUNGE_TICKS;
				}
				return;
			}

			Devourer.this.flyTo(Devourer.this.orbit(target, 12.0, 4.0, starving ? 0.035F : 0.02F), 1.1);
			if (--this.volley <= 0) {
				this.volley = starving ? VOLLEY_TICKS / 2 : VOLLEY_TICKS;
				for (int i = -2; i <= 2; i++) {
					Vec3 aim = target.getEyePosition().add(i * 1.2, -0.4, -i * 1.2);
					level.addFreshEntity(EnergyBolt.fire(Devourer.this, EnergyBolt.Kind.DEATH, aim));
				}
				level.playSound(null, Devourer.this.getX(), Devourer.this.getY(), Devourer.this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.7F);
			}
			if (--this.lunge <= 0) {
				this.lunging = true;
				this.lunge = 0;
				level.playSound(null, Devourer.this.getX(), Devourer.this.getY(), Devourer.this.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 0.8F);
			}
		}
	}
}
