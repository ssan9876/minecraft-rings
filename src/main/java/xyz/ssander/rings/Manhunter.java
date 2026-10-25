package xyz.ssander.rings;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
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
 * The Manhunters: androids the Guardians built long ago to keep the peace, who now hunt ring-bearers through
 * space and on the dead worlds. They fly, keep their distance, and shoot bolts of energy.
 */
public class Manhunter extends SpaceFlyer {
	/** Ticks between shots, and how far off it keeps from its target. */
	private static final int SHOT_TICKS = 30;
	private static final double KEEP_DISTANCE = 9.0;

	public Manhunter(EntityType<? extends Manhunter> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new HuntGoal());
		this.goalSelector.addGoal(5, new DriftGoal());
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BEACON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.6F;
	}

	/** Circles its target at a distance, shooting whenever it has a clear line. */
	private final class HuntGoal extends Goal {
		private int cooldown;

		HuntGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Manhunter.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Manhunter.this.getTarget();
			if (target == null) {
				return;
			}
			Manhunter.this.flyTo(Manhunter.this.orbit(target, KEEP_DISTANCE, 3.0, 0.03F), 1.0);
			Manhunter.this.face(target);
			if (--this.cooldown <= 0 && Manhunter.this.hasLineOfSight(target) && Manhunter.this.level() instanceof ServerLevel level) {
				this.cooldown = SHOT_TICKS;
				level.addFreshEntity(EnergyBolt.fire(Manhunter.this, EnergyBolt.Kind.MANHUNTER, target.getEyePosition().subtract(0.0, 0.4, 0.0)));
				Manhunter.this.playSound(SoundEvents.BLAZE_SHOOT, 0.8F, 1.6F);
			}
		}
	}

	/** With nothing to hunt, drifts about. */
	private final class DriftGoal extends Goal {
		DriftGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return Manhunter.this.getTarget() == null && !Manhunter.this.getMoveControl().hasWanted() && Manhunter.this.random.nextInt(40) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			Vec3 to = Manhunter.this.position().add(Manhunter.this.random.nextInt(17) - 8, Manhunter.this.random.nextInt(7) - 3, Manhunter.this.random.nextInt(17) - 8);
			Manhunter.this.flyTo(to, 0.5);
		}
	}
}
