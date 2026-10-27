package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;

import xyz.ssander.rings.Rings;
import xyz.ssander.rings.SuitStyle;

/** Sent by the client when the player picks which suit their rings dress them in. */
public record ChooseSuitPayload(SuitStyle style) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ChooseSuitPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("choose_suit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChooseSuitPayload> CODEC = SuitStyle.STREAM_CODEC
			.<RegistryFriendlyByteBuf>cast().map(ChooseSuitPayload::new, ChooseSuitPayload::style);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
