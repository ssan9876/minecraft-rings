package xyz.ssander.rings;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

/** Data stored on ring item stacks. */
public final class RingComponents {
	/** Power left in the ring, up to {@link RingItem#MAX_CHARGE}. Drains while the ring is on. */
	public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Rings.id("charge"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	private RingComponents() {
	}
}
