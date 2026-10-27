package xyz.ssander.rings.network;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import xyz.ssander.rings.Rings;

/** Sent by the client when the wearer picks an item to construct: one of it, or a whole stack. */
public record ConstructItemPayload(Holder<Item> item, boolean stack) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ConstructItemPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("construct_item"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConstructItemPayload> CODEC = StreamCodec.composite(
			Item.STREAM_CODEC, ConstructItemPayload::item,
			ByteBufCodecs.BOOL, ConstructItemPayload::stack,
			ConstructItemPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
