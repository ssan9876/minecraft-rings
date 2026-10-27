package xyz.ssander.rings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * The suits a ring can dress its wearer in, each made in every ring colour, chosen by the wearer (the suit key). Their
 * skins are at {@code textures/entity/costume/<colour>_<id>.png}, drawn by tools/gen_textures.py. With no suit the
 * wearer keeps their own skin, still outlined in the ring's colour.
 */
public enum SuitStyle implements StringRepresentable {
	// The classic suit keeps the id it had before there was a choice
	CLASSIC("lantern"),
	SHADOW("shadow"),
	ARMORED("armored"),
	ROBED("robed"),
	NONE("none");

	public static final StringRepresentable.EnumCodec<SuitStyle> CODEC = StringRepresentable.fromEnum(SuitStyle::values);
	public static final StreamCodec<ByteBuf, SuitStyle> STREAM_CODEC = ByteBufCodecs.idMapper(
			ByIdMap.continuous(SuitStyle::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), SuitStyle::ordinal);

	private final String id;

	SuitStyle(String id) {
		this.id = id;
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
