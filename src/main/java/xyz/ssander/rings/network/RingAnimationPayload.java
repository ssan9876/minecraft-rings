package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;

/** Sent to a player and everyone who can see them, so their clients play one of the ring animations on them. */
public record RingAnimationPayload(int entityId, Kind kind) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RingAnimationPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("animation"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RingAnimationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RingAnimationPayload::entityId,
			ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), RingAnimationPayload::kind,
			RingAnimationPayload::new);

	public enum Kind {
		/** Thrusts the ring to the sky. */
		ACTIVATE,
		/** Holds the ring out to a lantern. Sent with every top-up; the pose ends a moment after the last one. */
		CHARGE
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
