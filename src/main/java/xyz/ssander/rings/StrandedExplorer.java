package xyz.ssander.rings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An explorer stranded on Mars, in a spacesuit with a gold visor, for a Guardian's rescue mission: they wave anyone
 * who comes near over, and wait to be carried home to the Earth in a bubble. Tagged {@link Missions#STRANDED} until
 * they're home. Their suit keeps them breathing.
 */
public class StrandedExplorer extends PathfinderMob {
	private static final double CALL_RANGE = 40.0;
	private final Set<UUID> called = new HashSet<>();

	public StrandedExplorer(EntityType<? extends StrandedExplorer> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24.0F, 1.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.tickCount % 40 != 0 || !(this.level() instanceof ServerLevel level) || !this.entityTags().contains(Missions.STRANDED)) {
			return;
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(CALL_RANGE), p -> !p.isSpectator())) {
			if (this.called.add(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("explorer.rings.says", this.getDisplayName(),
						Component.translatable("explorer.rings.call")).withStyle(ChatFormatting.WHITE));
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}
}
