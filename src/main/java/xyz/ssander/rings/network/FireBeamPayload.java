package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;

/** Sent by the client when the beam key is pressed or let go. */
public record FireBeamPayload(boolean firing) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FireBeamPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("fire_beam"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireBeamPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FireBeamPayload::firing,
			FireBeamPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
