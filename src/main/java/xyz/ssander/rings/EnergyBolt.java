package xyz.ssander.rings;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * A bolt of energy flying straight until it hits something: the red ring's plasma, which sets what it hits alight,
 * and the shots of the creatures of space.
 */
public class EnergyBolt extends Projectile {
	public static final ResourceKey<DamageType> PLASMA_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Rings.id("plasma"));
	public static final ResourceKey<DamageType> BOLT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Rings.id("energy_bolt"));
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.INT);
	/** Ticks before it fades out unspent. */
	private static final int LIFETIME = 50;

	public enum Kind {
		/** The red ring's: burning plasma. */
		PLASMA(RingColor.RED, 6.0F, 1.6),
		/** A Manhunter's shot. */
		MANHUNTER(RingColor.RED, 5.0F, 1.2),
		/** Parallax's: it fills whoever it hits with fear, weakening them. */
		FEAR(RingColor.YELLOW, 7.0F, 1.3),
		/** Nekron's: it withers whoever it hits. */
		DEATH(RingColor.BLACK, 7.0F, 1.2);

		private final RingColor color;
		private final float damage;
		private final double speed;

		Kind(RingColor color, float damage, double speed) {
			this.color = color;
			this.damage = damage;
			this.speed = speed;
		}

		public RingColor color() {
			return this.color;
		}
	}

	public EnergyBolt(EntityType<? extends EnergyBolt> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	/** A bolt fired from the shooter's eyes toward the point. */
	public static EnergyBolt fire(LivingEntity shooter, Kind kind, Vec3 at) {
		EnergyBolt bolt = new EnergyBolt(Rings.ENERGY_BOLT, shooter.level());
		bolt.setOwner(shooter);
		bolt.entityData.set(KIND, kind.ordinal());
		Vec3 from = shooter.getEyePosition().subtract(0.0, 0.3, 0.0);
		Vec3 direction = at.subtract(from).normalize();
		bolt.setPos(from.add(direction.scale(Math.max(0.8, shooter.getBbWidth()))));
		bolt.setDeltaMovement(direction.scale(kind.speed));
		bolt.lookAlong(direction);
		return bolt;
	}

	private void lookAlong(Vec3 direction) {
		this.setYRot((float) (Math.atan2(direction.x, direction.z) * (180.0 / Math.PI)));
		this.setXRot((float) (Math.atan2(direction.y, direction.horizontalDistance()) * (180.0 / Math.PI)));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	public Kind kind() {
		int ordinal = this.entityData.get(KIND);
		return ordinal >= 0 && ordinal < Kind.values().length ? Kind.values()[ordinal] : Kind.PLASMA;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if (hit.getType() != HitResult.Type.MISS) {
				this.burst(level, hit.getLocation(), hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
				return;
			}
			if (this.tickCount > LIFETIME) {
				this.discard();
				return;
			}
		} else {
			// A trail of its energy behind it
			this.level().addParticle(this.kind().color().dust(0.3F, 0.7F), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		}
		this.setPos(this.position().add(this.getDeltaMovement()));
	}

	private void burst(ServerLevel level, Vec3 at, Entity target) {
		Kind kind = this.kind();
		Entity owner = this.getOwner();
		if (target instanceof LivingEntity living) {
			ResourceKey<DamageType> type = kind == Kind.PLASMA ? PLASMA_DAMAGE : BOLT_DAMAGE;
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), this, owner);
			living.hurtServer(level, source, kind.damage);
			switch (kind) {
				case PLASMA -> living.igniteForSeconds(5.0F);
				case FEAR -> {
					living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
				}
				case DEATH -> living.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
				default -> {
				}
			}
		}
		level.sendParticles(kind.color().dust(0.2F, 1.4F), at.x, at.y, at.z, 16, 0.25, 0.25, 0.25, 0.0);
		level.sendParticles(kind.color().dust(0.8F, 0.8F), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
		level.playSound(null, at.x, at.y, at.z, kind == Kind.PLASMA ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIREWORK_ROCKET_BLAST,
				SoundSource.HOSTILE, 0.7F, 1.4F);
		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof EnergyBolt) && !(entity instanceof ForceBubble);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("kind", this.entityData.get(KIND));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(KIND, input.getIntOr("kind", 0));
	}
}
