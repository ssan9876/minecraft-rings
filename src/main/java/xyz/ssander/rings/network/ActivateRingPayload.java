package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;

/** Sent by the client when the activate key is pressed. The server decides what it does. */
public record ActivateRingPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ActivateRingPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("activate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ActivateRingPayload> CODEC = StreamCodec.unit(new ActivateRingPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
