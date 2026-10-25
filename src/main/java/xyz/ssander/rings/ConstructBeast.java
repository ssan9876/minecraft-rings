package xyz.ssander.rings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A creature made of a ring's light, made with the construct wheel: a wolf, quick and fierce, or a golem, slow and
 * mighty. It fights for its maker like any creature a ring raises (see Thralls), and fades when its time is up.
 */
public class ConstructBeast extends PathfinderMob {
	public ConstructBeast(EntityType<? extends ConstructBeast> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder wolfAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 16.0)
				.add(Attributes.MOVEMENT_SPEED, 0.4)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	public static AttributeSupplier.Builder golemAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	public boolean isGolem() {
		return this.getType() == Rings.CONSTRUCT_GOLEM;
	}

	public RingColor color() {
		RingColor color = this.getAttached(Thralls.THRALL_COLOR);
		return color != null ? color : RingColor.GREEN;
	}

	@Override
	protected void registerGoals() {
		// Who to fight and following its maker come from Thralls
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.isGolem()) {
			// The golem's blows throw things into the air
			target.push(0.0, 0.5, 0.0);
			target.needsSync = true;
		}
		return hit;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	@Override
	public boolean isPushable() {
		return !this.isGolem();
	}
}
