package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.Rings;

/**
 * Sent by the client when the player gives an answer that needs the server: taking a dying ring-bearer's ring, or
 * asking a Guardian for work, turning it in, or to unlearn their skills. The server checks and answers.
 */
public record DialogueActionPayload(int entityId, Dialogues.Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DialogueActionPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("dialogue_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueActionPayload::entityId,
			ByteBufCodecs.idMapper(ByIdMap.continuous(Dialogues.Action::ordinal, Dialogues.Action.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
					Dialogues.Action::ordinal), DialogueActionPayload::action,
			DialogueActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
