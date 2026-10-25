package xyz.ssander.rings.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals, for rings that raise creatures to fight for their wearer (see Thralls). */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector rings$goalSelector();

	@Accessor("targetSelector")
	GoalSelector rings$targetSelector();
}
