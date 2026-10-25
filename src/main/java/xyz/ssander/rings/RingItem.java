package xyz.ssander.rings;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** A ring worn in the ring slot. Its powers switch on and off with the activate key. */
public class RingItem extends Item {
	private final boolean flight;
	private final @Nullable Identifier costume;

	public RingItem(Properties properties, boolean flight, @Nullable Identifier costume) {
		super(properties);
		this.flight = flight;
		this.costume = costume;
	}

	public boolean grantsFlight() {
		return this.flight;
	}

	/** The costume the wearer turns into while active, or null for none. */
	public @Nullable Identifier costume() {
		return this.costume;
	}
}
