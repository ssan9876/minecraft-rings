package xyz.ssander.rings.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;

/** Sent by the client when a pink ring's wearer picks a friend to go to. */
public record TeleportToPayload(UUID friend) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TeleportToPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("teleport_to"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TeleportToPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, TeleportToPayload::friend,
			TeleportToPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
