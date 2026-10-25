package xyz.ssander.rings;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.mixin.MobAccessor;

/**
 * What rings do to creatures' minds. Fear (the yellow ring's) sends a creature running from where it was scared,
 * too afraid to fight. Love (the pink ring's) charms it: it won't go for any player while it lasts.
 */
public final class MobMoods {
	/** How far a frightened creature runs, and how fast. */
	private static final int FLEE_DISTANCE = 16;
	private static final double FLEE_SPEED = 1.5;

	private record Fear(Vec3 from, long until) {
	}

	private static final Map<Mob, Fear> AFRAID = new WeakHashMap<>();
	/** Creatures that have been given the flee goal. */
	private static final Set<Mob> CAN_FLEE = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Mob, Long> CHARMED = new WeakHashMap<>();

	private MobMoods() {
	}

	/** Frightens the creature for the given ticks, sending it running away from the point. */
	public static void scare(Mob mob, Vec3 from, int ticks) {
		if (Thralls.of(mob) != null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		long until = level.getGameTime() + ticks;
		Fear fear = AFRAID.get(mob);
		if (fear == null || fear.until() < until) {
			AFRAID.put(mob, new Fear(from, until));
		}
		// Running away comes before anything else it wants to do
		if (mob instanceof PathfinderMob runner && CAN_FLEE.add(mob)) {
			((MobAccessor) mob).rings$goalSelector().addGoal(0, new FleeGoal(runner));
		}
	}

	public static boolean isAfraid(Mob mob) {
		Fear fear = AFRAID.get(mob);
		return fear != null && fear.until() > mob.level().getGameTime();
	}

	/** Charms the creature for the given ticks: it leaves every player alone. */
	public static void charm(Mob mob, int ticks) {
		CHARMED.merge(mob, mob.level().getGameTime() + ticks, Math::max);
	}

	public static boolean isCharmed(Mob mob) {
		return CHARMED.getOrDefault(mob, Long.MIN_VALUE) > mob.level().getGameTime();
	}

	/** Runs from what frightened the creature, for as long as it's afraid. */
	private static final class FleeGoal extends Goal {
		private final PathfinderMob mob;

		FleeGoal(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return isAfraid(this.mob);
		}

		@Override
		public void start() {
			this.run();
		}

		@Override
		public void tick() {
			if (this.mob.getNavigation().isDone() || this.mob.tickCount % 20 == 0) {
				this.run();
			}
		}

		private void run() {
			Fear fear = AFRAID.get(this.mob);
			Vec3 away = fear == null ? null : DefaultRandomPos.getPosAway(this.mob, FLEE_DISTANCE, 7, fear.from());
			if (away != null) {
				this.mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
			}
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}
	}

	/** Keeps frightened creatures from fighting and charmed ones calm. Runs every server tick. */
	static void tick() {
		for (Iterator<Map.Entry<Mob, Fear>> it = AFRAID.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Fear> entry = it.next();
			Mob mob = entry.getKey();
			Fear fear = entry.getValue();
			if (mob.isRemoved() || mob.level().getGameTime() > fear.until()) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			if (mob instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
			if (mob.tickCount % 15 == 0 && mob.level() instanceof ServerLevel level) {
				level.sendParticles(RingColor.YELLOW.dust(0.3F, 0.8F), mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
			}
		}

		for (Iterator<Map.Entry<Mob, Long>> it = CHARMED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Long> entry = it.next();
			Mob mob = entry.getKey();
			if (mob.isRemoved() || mob.level().getGameTime() > entry.getValue()) {
				it.remove();
				continue;
			}
			if (mob.getTarget() instanceof Player) {
				mob.setTarget(null);
			}
			if (mob instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
			if (mob.tickCount % 20 == 0 && mob.level() instanceof ServerLevel level) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}
}
