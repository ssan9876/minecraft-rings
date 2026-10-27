package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * Sent to a player and everyone who can see them, so their clients play one of the ring animations on them, in the
 * energy of the ring's colour.
 */
public record RingAnimationPayload(int entityId, Kind kind, RingColor color) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RingAnimationPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("animation"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RingAnimationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RingAnimationPayload::entityId,
			ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), RingAnimationPayload::kind,
			ByteBufCodecs.VAR_INT.map(i -> RingColor.values()[i], RingColor::ordinal), RingAnimationPayload::color,
			RingAnimationPayload::new);

	public enum Kind {
		/** Thrusts the ring to the sky. */
		ACTIVATE,
		/** Holds the ring out to a lantern. Sent with every top-up; the pose ends a moment after the last one. */
		CHARGE,
		/** The top-up that filled the ring: the power bursts out of the wearer. */
		CHARGE_FULL,
		/** A construct made: the ring fist thrust out as it takes shape. */
		CONSTRUCT,
		/** A scan sent out: a pulse of the ring's energy, and for the wearer, what it finds (see RingScan). */
		SCAN,
		/** The colour's power let loose: fists gathered at the chest, then flung wide. */
		POWER,
		/** The force field put up: forearms crossed, then thrown apart. */
		FIELD
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
