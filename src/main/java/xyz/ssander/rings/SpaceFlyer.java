package xyz.ssander.rings;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A hostile creature that flies freely, through the void of space as well as through air, steering straight for
 * where it wants to be rather than following paths. Manhunters and Parallax are both of them.
 */
public abstract class SpaceFlyer extends Monster {
	protected SpaceFlyer(EntityType<? extends SpaceFlyer> type, Level level) {
		super(type, level);
		this.moveControl = new HoverMoveControl(this);
	}

	/** Flies toward a point at the given multiple of the creature's speed. */
	protected void flyTo(Vec3 point, double speed) {
		this.moveControl.setWantedPosition(point.x, point.y, point.z, speed);
	}

	/** A point circling the target at the given distance and height, a little further round each tick. */
	protected Vec3 orbit(LivingEntity target, double distance, double height, float speed) {
		float angle = this.tickCount * speed + this.getId();
		return target.position().add(Mth.cos(angle) * distance, height, Mth.sin(angle) * distance);
	}

	/** Turns to face the target. */
	protected void face(LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		this.setYRot(-((float) Mth.atan2(dx, dz)) * Mth.RAD_TO_DEG);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	@Override
	public boolean isNoGravity() {
		// Always, even when made by a command (which sets gravity from its data)
		return true;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return distanceSqr > 128.0 * 128.0;
	}

	/** Steers straight for the wanted point, picking up speed toward it and easing off when there. */
	private static final class HoverMoveControl extends MoveControl<Mob> {
		HoverMoveControl(Mob mob) {
			super(mob);
		}

		@Override
		public void tick() {
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				// Hangs where it is, slowing to a stop
				this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.8));
				return;
			}
			Vec3 delta = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
			double distance = delta.length();
			if (distance < this.mob.getBbWidth()) {
				this.operation = MoveControl.Operation.WAIT;
				this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.5));
				return;
			}
			double speed = this.speedModifier * this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
			this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(delta.scale(speed * 0.12 / distance)));
			if (this.mob.getTarget() == null) {
				Vec3 movement = this.mob.getDeltaMovement();
				this.mob.setYRot(-((float) Mth.atan2(movement.x, movement.z)) * Mth.RAD_TO_DEG);
				this.mob.yBodyRot = this.mob.getYRot();
			}
		}
	}
}
