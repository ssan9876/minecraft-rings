package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

import xyz.ssander.rings.RingConstructs.Construct;
import xyz.ssander.rings.Rings;

/** Sent by the client when the wearer picks a construct from the construct wheel. */
public record CreateConstructPayload(Construct construct) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CreateConstructPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("create_construct"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CreateConstructPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(ByIdMap.continuous(Construct::ordinal, Construct.values(), ByIdMap.OutOfBoundsStrategy.ZERO), Construct::ordinal),
			CreateConstructPayload::construct,
			CreateConstructPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
