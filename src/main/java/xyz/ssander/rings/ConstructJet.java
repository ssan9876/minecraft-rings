package xyz.ssander.rings;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A jet made of a ring's light, from the construct wheel. Its maker pilots it from the front seat: it flies where
 * they look, holding forward (faster sprinting), and slows to a hover without. Three friends can ride behind
 * (right-click it to climb in, sneak to climb out). It flies up into space and on to the planets like a ring does,
 * keeping everyone aboard breathing, and fades once its maker's ring goes off or they leave it for a while.
 */
public class ConstructJet extends Entity {
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ConstructJet.class, EntityDataSerializers.INT);
	/** Its maker, synced so their client knows to fly it. */
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> OWNER =
			SynchedEntityData.defineId(ConstructJet.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
	private static final int SEATS = 4;
	/** Where each seat is along the jet, front to back, in blocks. */
	private static final double[] SEAT_Z = {1.0, 0.0, -1.0, -2.0};
	/** Cruising and sprinting speeds, in blocks per tick, and how quickly it gets up to them. */
	private static final double CRUISE_SPEED = 1.4;
	private static final double BOOST_SPEED = 2.6;
	private static final double RESPONSE = 0.12;
	/** Charge it costs its maker each second, on top of the ring's own use. */
	static final int DRAIN = 3;
	/** It fades this many ticks after its maker gets out (and isn't back). */
	private static final int UNMANNED_TICKS = 10 * 20;

	private int unmanned;

	public ConstructJet(EntityType<? extends ConstructJet> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	/** A jet made by the wearer, with them already in the pilot's seat. */
	public static ConstructJet madeBy(Player owner, RingColor color) {
		ConstructJet jet = new ConstructJet(Rings.CONSTRUCT_JET, owner.level());
		jet.entityData.set(OWNER, Optional.of(EntityReference.of(owner.getUUID())));
		jet.entityData.set(COLOR, color.ordinal());
		jet.snapTo(owner.getX(), owner.getY() + 0.2, owner.getZ(), owner.getYRot(), 0.0F);
		return jet;
	}

	public RingColor color() {
		int ordinal = this.entityData.get(COLOR);
		return ordinal >= 0 && ordinal < RingColor.values().length ? RingColor.values()[ordinal] : RingColor.GREEN;
	}

	private @Nullable UUID ownerId() {
		return this.entityData.get(OWNER).map(EntityReference::getUUID).orElse(null);
	}

	public @Nullable Player owner() {
		UUID owner = this.ownerId();
		return owner == null ? null : this.level().getPlayerByUUID(owner);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(COLOR, 0);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isLocalInstanceAuthoritative()) {
			this.fly();
			this.move(MoverType.SELF, this.getDeltaMovement());
		}
		if (this.level() instanceof ServerLevel level) {
			this.serverTick(level);
		} else if (this.tickCount % 2 == 0) {
			// Exhaust from the engines
			Vec3 back = this.getViewVector(1.0F).scale(-3.2);
			this.level().addParticle(this.color().dust(0.5F, 0.9F), this.getX() + back.x, this.getY() + 0.35 + back.y, this.getZ() + back.z,
					0.0, 0.0, 0.0);
		}
	}

	/** Steers the jet for its pilot: where they look, at the speed their keys ask for. */
	private void fly() {
		Vec3 velocity = this.getDeltaMovement();
		if (!(this.getControllingPassenger() instanceof Player pilot)) {
			// Nobody at the controls: it slows to a stop and hangs there
			this.setDeltaMovement(velocity.scale(0.9));
			return;
		}
		this.setYRot(pilot.getYRot());
		this.setXRot(pilot.getXRot());
		Vec3 look = pilot.getViewVector(1.0F);
		double speed = pilot.zza > 0.0F ? (pilot.isSprinting() ? BOOST_SPEED : CRUISE_SPEED) : pilot.zza < 0.0F ? -0.4 : 0.0;
		Vec3 side = new Vec3(look.z, 0.0, -look.x).normalize().scale(pilot.xxa * 0.4);
		this.setDeltaMovement(velocity.lerp(look.scale(speed).add(side), RESPONSE));
	}

	private void serverTick(ServerLevel level) {
		Player owner = this.owner();
		boolean manned = owner != null && owner.getVehicle() == this;
		this.unmanned = manned ? 0 : this.unmanned + 1;
		if (owner == null || !RingAttachments.isActive(owner) || this.unmanned > UNMANNED_TICKS) {
			this.fade(level);
			return;
		}
		// Up into space and on to the planets, like the ring itself
		if (manned && owner instanceof ServerPlayer pilot) {
			Space.Trip trip = Space.trip(level.dimension(), this.position(), Space.home(pilot), this.random);
			if (trip != null) {
				Space.go(this, trip);
			}
		}
	}

	/** Fades away, letting everyone aboard down gently. */
	public void fade(ServerLevel level) {
		for (Entity passenger : this.getPassengers()) {
			if (passenger instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
			}
		}
		this.ejectPassengers();
		level.sendParticles(this.color().dust(0.4F, 1.4F), this.getX(), this.getY() + 0.5, this.getZ(), 60, 1.5, 0.4, 1.5, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
		this.discard();
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		// Only its maker can fly it, from the front seat
		return this.getFirstPassenger() instanceof Player pilot && pilot.getUUID().equals(this.ownerId()) ? pilot : null;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || this.getPassengers().contains(player)) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide() && !player.startRiding(this)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (this.getPassengers().size() >= SEATS) {
			return false;
		}
		// The front seat is kept for its maker
		return !this.getPassengers().isEmpty() || passenger.getUUID().equals(this.ownerId());
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int seat = Math.max(0, this.getPassengers().indexOf(passenger));
		return new Vec3(0.0, 0.3, SEAT_Z[Math.min(seat, SEATS - 1)]).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("owner", UUIDUtil.CODEC, this.ownerId());
		output.putInt("color", this.entityData.get(COLOR));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(OWNER, input.read("owner", UUIDUtil.CODEC).map(EntityReference::<LivingEntity>of));
		this.entityData.set(COLOR, input.getIntOr("color", 0));
	}

	/** Whether the player is piloting one of these. */
	public static boolean isPiloting(Player player) {
		return player.getVehicle() instanceof ConstructJet jet && jet.getControllingPassenger() == player;
	}
}
