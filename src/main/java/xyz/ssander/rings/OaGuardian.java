package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Guardians of the Universe, keepers of the Central Power Battery, who walk the citadel on Oa. They can't be
 * harmed. Talk to one (right-click) to hear what they know of the rings and their enemies, to take on a mission for the
 * Corps (see Missions), or, for will shards, to unmake what you've learned and choose again. They give no rings: a
 * ring only passes from a dying bearer (see CrashPilot).
 */
public class OaGuardian extends PathfinderMob {
	/** Set once a player was given a ring by the Guardians (before 1.13.0, when they still gave them). Kept so old saves load cleanly. */
	public static final AttachmentType<Boolean> GIFTED = AttachmentRegistry.create(Rings.id("guardian_gift"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());


	public OaGuardian(EntityType<? extends OaGuardian> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.6));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F, 0.6F));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		this.getLookControl().setLookAt(player);
		this.listening = player;
		this.listenTicks = LISTEN_TICKS;
		this.getNavigation().stop();
		boolean hasRing = !RingAttachments.getRing(player).isEmpty() || player.getInventory().contains(stack -> stack.getItem() instanceof RingItem);
		// Rings aren't given out: they come from the fallen. Ring-bearers can ask for lore, work, or to start over.
		Missions.reply(serverPlayer, this, hasRing ? "start" : "no_ring");
		this.playSound(SoundEvents.VILLAGER_AMBIENT, 0.6F, 0.6F);
		return InteractionResult.SUCCESS;
	}

	/** The player asked to unmake everything they've learned, for will shards: their skill points come back. */
	void respec(ServerPlayer player) {
		int shards = player.getInventory().countItem(PlanetBlocks.WILL_SHARD);
		if (Mastery.of(player).skills().isEmpty()) {
			Missions.reply(player, this, "respec_nothing");
			return;
		}
		if (shards < Dialogues.RESPEC_SHARDS) {
			Missions.reply(player, this, "respec_poor", Component.literal(String.valueOf(Dialogues.RESPEC_SHARDS)));
			return;
		}
		Missions.take(player, PlanetBlocks.WILL_SHARD, Dialogues.RESPEC_SHARDS);
		Mastery.unlearnAll(player);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(RingColor.GREEN.dust(0.5F, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.4, 0.8, 0.4, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		Missions.reply(player, this, "respec_done");
	}

	/** Who the Guardian is talking with, and for how much longer they stand still for them. */
	private static final int LISTEN_TICKS = 30 * 20;
	private @org.jspecify.annotations.Nullable Player listening;
	private int listenTicks;

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.listenTicks > 0 && this.listening != null) {
			// Stand and face whoever they're talking with
			this.listenTicks--;
			this.getNavigation().stop();
			this.getLookControl().setLookAt(this.listening);
			if (!this.listening.isAlive() || this.listening.distanceTo(this) > 12.0F) {
				this.listenTicks = 0;
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// Nothing harms a Guardian, short of the world itself
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}
}
