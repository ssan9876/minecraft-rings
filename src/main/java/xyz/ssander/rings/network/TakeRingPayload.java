package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;

/** Sent by the client when the player accepts a dying ring-bearer's ring in their dialogue. */
public record TakeRingPayload(int pilotId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TakeRingPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("take_ring"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TakeRingPayload> CODEC = ByteBufCodecs.VAR_INT
			.<RegistryFriendlyByteBuf>cast().map(TakeRingPayload::new, TakeRingPayload::pilotId);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
