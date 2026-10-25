package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

import xyz.ssander.rings.Rings;

/** Sent by the client when one of the ring's power keys is pressed. The server decides what it does. */
public record RingActionPayload(Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RingActionPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RingActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(ByIdMap.continuous(Action::ordinal, Action.values(), ByIdMap.OutOfBoundsStrategy.ZERO), Action::ordinal),
			RingActionPayload::action,
			RingActionPayload::new);

	public enum Action {
		/** The force field key: a force field round the wearer, or a bubble round what they look at. */
		FORCE_FIELD,
		/** The scan key. */
		SCAN
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
