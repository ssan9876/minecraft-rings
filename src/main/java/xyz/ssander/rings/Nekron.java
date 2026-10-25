package xyz.ssander.rings;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Nekron, lord of the dead, who sleeps on the Moon until a black lantern's light is gathered there. A towering
 * skeletal figure with a scythe: he raises the dead to fight for him, hurls bolts of death, and steps through the
 * shadows to whoever runs. Death can't kill death, so a black ring can't hurt him; the white light of life burns
 * him three times over.
 */
public class Nekron extends Monster {
	private static final int RAISE_TICKS = 140;
	private static final int BOLT_TICKS = 60;
	private static final int SHADOW_STEP_TICKS = 160;
	private static final int MINION_LIFETIME = 60 * 20;
	private static final List<EntityType<?>> MINIONS = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.HUSK);

	private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.translatable("entity.rings.nekron"),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

	public Nekron(EntityType<? extends Nekron> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		this.setPersistenceRequired();
		// His scythe
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_HOE));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 300.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player player) {
			RingColor color = RingAttachments.activeColor(player);
			if (color == RingColor.BLACK) {
				// Death can't kill death
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.rings.nekron_deathless"));
				}
				return false;
			}
			if (color == RingColor.WHITE) {
				damage *= 3.0F;
			}
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		// The dead rise round him to fight for him
		if (this.tickCount % RAISE_TICKS == 0) {
			for (int i = 0; i < 3; i++) {
				float angle = Mth.TWO_PI * i / 3 + this.random.nextFloat();
				Vec3 pos = this.position().add(Mth.cos(angle) * 3.0, 0.0, Mth.sin(angle) * 3.0);
				Thralls.raise(level, MINIONS.get(this.random.nextInt(MINIONS.size())), pos, null, RingColor.BLACK, MINION_LIFETIME);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.6F);
		}
		// A bolt of death at whoever he can see
		if (this.tickCount % BOLT_TICKS == 0 && this.hasLineOfSight(target) && this.distanceTo(target) < 24.0) {
			level.addFreshEntity(EnergyBolt.fire(this, EnergyBolt.Kind.DEATH, target.getEyePosition().subtract(0.0, 0.4, 0.0)));
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 0.8F);
		}
		// Whoever runs, he steps through the shadows to
		if (this.tickCount % SHADOW_STEP_TICKS == 0 && this.distanceTo(target) > 14.0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2.0, this.getZ(), 40, 0.6, 1.5, 0.6, 0.02);
			Vec3 behind = target.position().subtract(target.getViewVector(1.0F).multiply(3.0, 0.0, 3.0));
			this.teleportTo(behind.x, target.getY(), behind.z);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2.0, this.getZ(), 40, 0.6, 1.5, 0.6, 0.02);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.4F);
		}
		if (this.tickCount % 5 == 0) {
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.5, this.getZ(), 2, 0.5, 1.2, 0.5, 0.01);
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
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.rings.nekron_defeated"), false);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}
}
