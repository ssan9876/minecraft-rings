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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Guardians of the Universe, keepers of the Central Power Battery, who walk the citadel on Oa. They can't be
 * harmed. Talk to one (right-click) to hear what they know of the rings and their enemies; a visitor with no ring is
 * given one, once.
 */
public class OaGuardian extends PathfinderMob {
	/** How many things a Guardian has to say (lang keys {@code guardian.rings.1} on). */
	private static final int SAYINGS = 8;
	/** Set once a player has been given a ring by the Guardians. */
	public static final AttachmentType<Boolean> GIFTED = AttachmentRegistry.create(Rings.id("guardian_gift"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private int saying;

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
		boolean hasRing = !RingAttachments.getRing(player).isEmpty() || player.getInventory().contains(stack -> stack.getItem() instanceof RingItem);
		if (!hasRing && !player.getAttachedOrElse(GIFTED, false)) {
			// The ring has chosen them
			player.setAttached(GIFTED, true);
			ItemStack ring = new ItemStack(Rings.ring(RingColor.GREEN));
			if (!player.getInventory().add(ring)) {
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), ring));
			}
			say(serverPlayer, Component.translatable("guardian.rings.gift"));
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 1.2F);
			return InteractionResult.SUCCESS;
		}
		this.saying = this.saying % SAYINGS + 1;
		say(serverPlayer, Component.translatable("guardian.rings." + this.saying));
		this.playSound(SoundEvents.VILLAGER_AMBIENT, 0.6F, 0.6F);
		return InteractionResult.SUCCESS;
	}

	private void say(ServerPlayer player, Component words) {
		player.sendSystemMessage(Component.translatable("guardian.rings.says", this.getDisplayName(), words).withStyle(ChatFormatting.GREEN));
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
