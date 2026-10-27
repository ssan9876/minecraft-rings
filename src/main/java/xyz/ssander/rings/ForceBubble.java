package xyz.ssander.rings;

import java.util.List;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A bubble of the ring's force field carrying someone (or something) along beside the wearer: it follows them
 * wherever they fly, into space and onto other planets too, and keeps whoever's inside safe and breathing. Whoever's
 * carried can sneak to climb out; the bubble pops when they do, when the wearer lets go, or when the ring goes off.
 */
public class ForceBubble extends Entity {
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ForceBubble.class, EntityDataSerializers.INT);
	/** Where the bubble keeps to: off the wearer's right shoulder, a little behind. */
	private static final double SIDE = 1.9;
	private static final double BEHIND = 1.2;
	/** How much of the way to that spot it closes each tick, so it trails smoothly. */
	private static final double FOLLOW = 0.35;
	/** Further than this from the wearer, it jumps straight to them. */
	private static final double CATCH_UP = 24.0;

	private @Nullable UUID owner;

	public ForceBubble(EntityType<? extends ForceBubble> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A bubble made by the wearer around something, which has yet to climb in (see {@link #carry}). */
	public static ForceBubble around(Player owner, LivingEntity target, RingColor color) {
		ForceBubble bubble = new ForceBubble(Rings.FORCE_BUBBLE, owner.level());
		bubble.owner = owner.getUUID();
		bubble.entityData.set(COLOR, color.ordinal());
		bubble.setPos(target.position());
		return bubble;
	}

	/** Puts the target in the bubble. Call once the bubble is in the level. */
	public boolean carry(LivingEntity target) {
		return target.startRiding(this, true, true);
	}

	public RingColor color() {
		int ordinal = this.entityData.get(COLOR);
		return ordinal >= 0 && ordinal < RingColor.values().length ? RingColor.values()[ordinal] : RingColor.GREEN;
	}

	public boolean isOwnedBy(Player player) {
		return player.getUUID().equals(this.owner);
	}

	public @Nullable Player owner() {
		return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
	}

	/** The bubbles a wearer is carrying, wherever they are in the wearer's level. */
	public static List<ForceBubble> carriedBy(Player player) {
		if (player.level() instanceof ServerLevel level) {
			return List.copyOf(level.getEntities(Rings.FORCE_BUBBLE, bubble -> bubble.isOwnedBy(player)));
		}
		return player.level().getEntitiesOfClass(ForceBubble.class, player.getBoundingBox().inflate(CATCH_UP + 8.0), bubble -> bubble.isOwnedBy(player));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(COLOR, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}
		Player owner = this.owner();
		Entity passenger = this.getFirstPassenger();
		// A moment's grace after it's made, before whatever it carries has climbed in
		if (owner == null || !owner.isAlive() || !RingAttachments.isActive(owner) || passenger == null && this.tickCount > 2) {
			this.pop();
			return;
		}

		float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		Vec3 spot = owner.position().add(right.scale(SIDE)).add(back.scale(BEHIND)).add(0.0, 0.2, 0.0);
		Vec3 next = this.position().distanceTo(spot) > CATCH_UP ? spot : this.position().lerp(spot, FOLLOW);
		this.setDeltaMovement(next.subtract(this.position()));
		this.setPos(next);
	}

	/** Bursts the bubble, letting down gently whatever was inside. */
	public void pop() {
		if (this.level() instanceof ServerLevel level) {
			for (Entity passenger : this.getPassengers()) {
				if (passenger instanceof LivingEntity living && !(living instanceof Player player && player.getAbilities().flying)) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
				}
			}
			level.sendParticles(this.color().dust(0.5F, 1.0F), this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.7, 0.7, 0.7, 0.0);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.5F, 0.7F);
		}
		this.ejectPassengers();
		this.discard();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		// Whatever's carried floats in the middle of the bubble rather than sitting on top of it. Players sit lower
		// than creatures do (their vehicle attachment), which would leave their legs hanging out of the bottom.
		return passenger.getVehicleAttachmentPoint(this);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
		output.putInt("color", this.entityData.get(COLOR));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		this.entityData.set(COLOR, input.getIntOr("color", 0));
	}

	/** Whether the wearer is carrying anything in a bubble. */
	public static boolean isCarrying(ServerPlayer player) {
		return !carriedBy(player).isEmpty();
	}
}
