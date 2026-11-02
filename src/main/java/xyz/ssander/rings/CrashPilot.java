package xyz.ssander.rings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import xyz.ssander.rings.network.OpenDialoguePayload;

/**
 * A ring-bearer dying beside their crashed ship, the only way a ring comes into the world. They sit slumped against
 * the wreck with their ring in their hand, and call out to whoever comes near; talk to them (right-click) and they tell
 * what happened, and hand the ring on to a player who doesn't wear one already, with their lantern. Then they pass,
 * fading into the ring's light. A green ring's bearer is Abin Sur; every colour's has their own name.
 */
public class CrashPilot extends PathfinderMob {
	/**
	 * The colour of the pilot's ring (synced to every client for their suit), and whether they've handed it on. Entity
	 * data rather than attachments, since pilots are made while the world generates, off the server thread.
	 */
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(CrashPilot.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_TAKEN = SynchedEntityData.defineId(CrashPilot.class, EntityDataSerializers.BOOLEAN);

	/** How close a player has to be to take the ring, and to be greeted. */
	private static final double TALK_RANGE = 8.0;
	private static final double PROXIMITY_RANGE = 48.0;
	private static final double CALL_RANGE = 14.0;
	/** Ticks the pilot lingers after handing the ring on. */
	private static final int PASSING_TICKS = 100;
	/** The share of a full charge the pilot's ring has left after the crash. */
	private static final int RING_CHARGE = RingItem.MAX_CHARGE / 4;
	/** How far from the pilot their lantern can be (the crash site sets it two blocks off). */
	private static final int LANTERN_REACH = 3;

	private final Set<UUID> warned = new HashSet<>();
	private final Set<UUID> called = new HashSet<>();
	private int passing = -1;

	public CrashPilot(EntityType<? extends CrashPilot> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** A new pilot of the colour, ring in hand. */
	public static CrashPilot of(Level level, RingColor color) {
		CrashPilot pilot = new CrashPilot(Rings.CRASH_PILOT, level);
		pilot.entityData.set(DATA_COLOR, color.ordinal());
		pilot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Rings.ring(color)));
		pilot.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		pilot.setCustomName(name(color));
		return pilot;
	}

	public static Component name(RingColor color) {
		return Component.translatable("pilot.rings." + color.id());
	}

	public RingColor color() {
		RingColor[] colors = RingColor.values();
		int index = this.entityData.get(DATA_COLOR);
		return index >= 0 && index < colors.length ? colors[index] : RingColor.GREEN;
	}

	public boolean ringTaken() {
		return this.entityData.get(DATA_TAKEN);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COLOR, RingColor.GREEN.ordinal());
		builder.define(DATA_TAKEN, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("RingColor", this.color().getSerializedName());
		output.putBoolean("RingTaken", this.ringTaken());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String name = input.getStringOr("RingColor", RingColor.GREEN.getSerializedName());
		for (RingColor color : RingColor.values()) {
			if (color.getSerializedName().equals(name)) {
				this.entityData.set(DATA_COLOR, color.ordinal());
			}
		}
		this.entityData.set(DATA_TAKEN, input.getBooleanOr("RingTaken", false));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 12.0F, 1.0F));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(this.level() instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (this.ringTaken()) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.rings.pilot_passing", this.getDisplayName()));
			return InteractionResult.SUCCESS;
		}
		this.getLookControl().setLookAt(player);
		Codex.discover(serverPlayer, "pilot");
		ServerPlayNetworking.send(serverPlayer, new OpenDialoguePayload(Dialogues.Dialogue.PILOT,
				hasRing(player) ? Dialogues.PILOT_HAS_RING : Dialogues.PILOT_START, this.color(), this.getId()));
		this.playSound(SoundEvents.PLAYER_HURT, 0.4F, 0.6F);
		return InteractionResult.SUCCESS;
	}

	private static boolean hasRing(Player player) {
		return RingAttachments.getRing(player).getItem() instanceof RingItem
				|| player.getInventory().contains(stack -> stack.getItem() instanceof RingItem);
	}

	/** The player accepted the ring in the dialogue. */
	public void giveRing(ServerPlayer player) {
		if (this.ringTaken() || !this.isAlive() || player.distanceTo(this) > TALK_RANGE || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (hasRing(player)) {
			player.sendOverlayMessage(Component.translatable("message.rings.pilot_has_ring"));
			return;
		}
		RingColor color = this.color();
		ItemStack ring = new ItemStack(Rings.ring(color));
		ring.set(RingComponents.CHARGE, RING_CHARGE);
		RingCall.bind(ring, player, true);
		Component ringName = ring.getHoverName();
		give(level, player, ring);
		handOverLantern(level, player, color);

		if (!player.hasAttached(Mastery.MASTERY)) {
			player.setAttached(Mastery.MASTERY, Mastery.Data.NEW);
		}
		player.setAttached(Mastery.HEIR, true);
		this.entityData.set(DATA_TAKEN, true);
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		this.passing = PASSING_TICKS;
		Crashes.claimed(level, this.blockPosition());

		// The ring leaps to its new bearer
		level.sendParticles(color.dust(0.5F, 1.4F), this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.3, 0.4, 0.3, 0.0);
		level.sendParticles(color.dust(0.2F, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.rings.ring_chose_you", ringName).withColor(color.ui()));
	}

	/**
	 * The lantern beside the pilot goes to the new bearer: lifted out of the ground and into their hands. If it's gone
	 * already (someone broke it, and has it), there's no other to give.
	 */
	private void handOverLantern(ServerLevel level, ServerPlayer player, RingColor color) {
		PowerLanternBlock lantern = Rings.lantern(color);
		BlockPos centre = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-LANTERN_REACH, -2, -LANTERN_REACH), centre.offset(LANTERN_REACH, 2, LANTERN_REACH))) {
			if (level.getBlockState(pos).is(lantern)) {
				level.removeBlock(pos, false);
				level.sendParticles(color.dust(0.5F, 1.0F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.25, 0.35, 0.25, 0.0);
				give(level, player, new ItemStack(lantern));
				return;
			}
		}
	}

	private static void give(ServerLevel level, ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		RingColor color = this.color();
		if (this.passing >= 0) {
			// Fading into the ring's light
			level.sendParticles(color.dust(0.6F, 0.8F), this.getX(), this.getY() + 0.6, this.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
			if (this.passing-- == 0) {
				level.sendParticles(color.dust(0.4F, 1.4F), this.getX(), this.getY() + 0.8, this.getZ(), 80, 0.4, 0.7, 0.4, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.8, this.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0F, 0.6F);
				for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(PROXIMITY_RANGE))) {
					ship(player, Component.translatable("ship.rings.pilot_lost", this.getDisplayName()));
				}
				this.discard();
			}
			return;
		}
		if (this.ringTaken()) {
			return;
		}
		if (this.tickCount % 10 == 0) {
			// The ring in their hand still glows
			level.sendParticles(color.dust(0.3F, 0.5F), this.getX(), this.getY() + 0.7, this.getZ(), 1, 0.25, 0.15, 0.25, 0.0);
		}
		if (this.tickCount % 40 != 0) {
			return;
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(PROXIMITY_RANGE), p -> !p.isSpectator())) {
			UUID id = player.getUUID();
			if (this.warned.add(id)) {
				// The ship's computer is still working, and has seen them coming
				ship(player, Component.translatable("ship.rings.proximity"));
			}
			if (player.distanceTo(this) < CALL_RANGE && this.called.add(id)) {
				player.sendSystemMessage(Component.translatable("pilot.rings.says", this.getDisplayName(),
						Component.translatable(hasRing(player) ? "pilot.rings.call_ringed" : "pilot.rings.call")).withColor(color.ui()));
				this.playSound(SoundEvents.PLAYER_HURT, 0.5F, 0.6F);
			}
		}
	}

	static void ship(ServerPlayer player, Component words) {
		player.sendSystemMessage(Component.translatable("ship.rings.says", words).withStyle(ChatFormatting.AQUA));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// Past harm now, and the ring still shields them
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
	}

	@Override
	public boolean isPushable() {
		return false;
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
		return 0.7F;
	}
}
