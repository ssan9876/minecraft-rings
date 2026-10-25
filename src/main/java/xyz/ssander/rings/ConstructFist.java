package xyz.ssander.rings;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A giant fist of the ring's light, thrown where the wearer looks, or a giant hammer brought straight down from
 * above. Either smashes into the first thing it meets: the target takes a heavy hit and everything around is thrown
 * back (the hammer's shockwave reaching further and throwing things up). It breaks no blocks.
 */
public class ConstructFist extends Projectile {
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Rings.id("construct"));
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ConstructFist.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> HAMMER = SynchedEntityData.defineId(ConstructFist.class, EntityDataSerializers.BOOLEAN);
	/** The hammer appears this far above where it comes down, and falls this fast (blocks per tick). */
	private static final double HAMMER_HEIGHT = 7.0;
	private static final double HAMMER_SPEED = 1.1;
	private static final double HAMMER_RADIUS = 5.5;
	/** Blocks per tick, and ticks before it fades out unspent (about 50 blocks). */
	private static final double SPEED = 1.4;
	private static final int LIFETIME = 36;
	private static final float DAMAGE = 8.0F;
	private static final float SPLASH_DAMAGE = 3.0F;
	private static final double SPLASH_RADIUS = 3.5;

	public ConstructFist(EntityType<? extends ConstructFist> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	/** A fist from the wearer's chest, flying along their line of sight. */
	public static ConstructFist throwFrom(LivingEntity owner, RingColor color) {
		ConstructFist fist = new ConstructFist(Rings.CONSTRUCT_FIST, owner.level());
		fist.setOwner(owner);
		fist.entityData.set(COLOR, color.ordinal());
		Vec3 look = owner.getViewVector(1.0F);
		fist.setPos(owner.getEyePosition().add(look.scale(1.2)).subtract(0.0, 0.6, 0.0));
		fist.setDeltaMovement(look.scale(SPEED));
		fist.setYRot(owner.getYRot());
		fist.setXRot(owner.getXRot());
		return fist;
	}

	/** A hammer over the given point, coming straight down onto it. */
	public static ConstructFist hammerOver(LivingEntity owner, Vec3 target, RingColor color) {
		ConstructFist hammer = new ConstructFist(Rings.CONSTRUCT_FIST, owner.level());
		hammer.setOwner(owner);
		hammer.entityData.set(COLOR, color.ordinal());
		hammer.entityData.set(HAMMER, true);
		hammer.setPos(target.add(0.0, HAMMER_HEIGHT, 0.0));
		hammer.setDeltaMovement(0.0, -HAMMER_SPEED, 0.0);
		hammer.setYRot(owner.getYRot());
		return hammer;
	}

	public boolean isHammer() {
		return this.entityData.get(HAMMER);
	}

	public RingColor color() {
		int ordinal = this.entityData.get(COLOR);
		return ordinal >= 0 && ordinal < RingColor.values().length ? RingColor.values()[ordinal] : RingColor.GREEN;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(COLOR, 0);
		builder.define(HAMMER, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if (hit.getType() != HitResult.Type.MISS) {
				this.smash(level, hit.getLocation(), hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
				return;
			}
			if (this.tickCount > LIFETIME) {
				level.sendParticles(this.color().dust(0.4F, 1.2F), this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.4, 0.4, 0.4, 0.0);
				this.discard();
				return;
			}
		}
		this.setPos(this.position().add(this.getDeltaMovement()));
	}

	private void smash(ServerLevel level, Vec3 at, Entity target) {
		Entity owner = this.getOwner();
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), this, owner);
		if (target instanceof LivingEntity living) {
			living.hurtServer(level, source, DAMAGE);
		}
		boolean hammer = this.isHammer();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(hammer ? HAMMER_RADIUS : SPLASH_RADIUS))) {
			if (near == owner || !near.isAlive()) {
				continue;
			}
			if (near != target) {
				near.hurtServer(level, source, SPLASH_DAMAGE);
			}
			Vec3 away = near.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? this.getDeltaMovement().multiply(1.0, 0.0, 1.0) : away;
			near.push(hammer ? away.normalize().scale(0.8).add(0.0, 0.9, 0.0) : away.normalize().scale(1.4).add(0.0, 0.45, 0.0));
			near.needsSync = true;
		}

		RingColor color = this.color();
		level.sendParticles(color.dust(0.0F, 2.0F), at.x, at.y, at.z, 60, 1.2, 0.8, 1.2, 0.0);
		level.sendParticles(color.dust(0.8F, 1.2F), at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		if (hammer) {
			// The shockwave: a ring of the ground thrown up around where it lands
			for (int i = 0; i < 40; i++) {
				double angle = Math.PI * 2 * i / 40;
				level.sendParticles(color.dust(0.3F, 1.6F), at.x + Math.cos(angle) * HAMMER_RADIUS * 0.7, at.y + 0.2, at.z + Math.sin(angle) * HAMMER_RADIUS * 0.7,
						2, 0.3, 0.1, 0.3, 0.0);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.9F);
		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof ConstructFist);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("color", this.entityData.get(COLOR));
		output.putBoolean("hammer", this.isHammer());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(COLOR, input.getIntOr("color", 0));
		this.entityData.set(HAMMER, input.getBooleanOr("hammer", false));
	}
}
