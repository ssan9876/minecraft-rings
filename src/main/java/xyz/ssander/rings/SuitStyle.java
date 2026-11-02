package xyz.ssander.rings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;

/**
 * The suits a ring can dress its wearer in, each made in every ring colour, chosen by the wearer (the suit key). Their
 * skins are at {@code textures/entity/costume/<colour>_<id>.png}, drawn by tools/gen_textures.py. With no suit the
 * wearer keeps their own skin, still outlined in the ring's colour.
 */
public enum SuitStyle implements StringRepresentable {
	// The classic suit keeps the id it had before there was a choice
	CLASSIC("lantern", 1, false),
	SHADOW("shadow", 1, false),
	ARMORED("armored", 1, false),
	ROBED("robed", 1, false),
	NONE("none", 1, false),
	// Earned: by the ring's level, or (the pilot's) by taking a ring from a dying ring-bearer
	VETERAN("veteran", 5, false),
	HONOR_GUARD("honor_guard", 10, true),
	STARFIELD("starfield", 15, false),
	SENTINEL("sentinel", 20, false),
	PARAGON("paragon", 30, true),
	PILOT("pilot", 1, false);

	public static final StringRepresentable.EnumCodec<SuitStyle> CODEC = StringRepresentable.fromEnum(SuitStyle::values);
	public static final StreamCodec<ByteBuf, SuitStyle> STREAM_CODEC = ByteBufCodecs.idMapper(
			ByIdMap.continuous(SuitStyle::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), SuitStyle::ordinal);

	private final String id;
	private final int level;
	private final boolean cape;

	SuitStyle(String id, int level, boolean cape) {
		this.id = id;
		this.level = level;
		this.cape = cape;
	}

	/** The ring level that unlocks it. */
	public int level() {
		return this.level;
	}

	/** Whether it has a cape ({@code <colour>_<id>_cape.png}). */
	public boolean hasCape() {
		return this.cape;
	}

	/** Whether the player has earned it. */
	public boolean unlocked(Player player) {
		if (this == PILOT) {
			return Mastery.isHeir(player);
		}
		return Mastery.level(player) >= this.level;
	}

	/** Why it's locked, for the suit screen. */
	public Component lockedReason() {
		return this == PILOT ? Component.translatable("suit.rings.locked_pilot") : Component.translatable("suit.rings.locked_level", this.level);
	}

	public String id() {
		return this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	/** Whether it swaps in a suit at all, rather than leaving the wearer in their own skin. */
	public boolean hasSuit() {
		return this != NONE;
	}

	public String translationKey() {
		return "suit.rings." + this.id;
	}
}
