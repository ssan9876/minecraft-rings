package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * Sent to a player who talks to a crash site's pilot or console: which dialogue, where it starts, the colour of the
 * crashed ring-bearer, and the pilot's entity (to show them, and to take the ring from), or -1 for the console.
 */
public record OpenDialoguePayload(Dialogues.Dialogue dialogue, String node, RingColor color, int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenDialoguePayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("open_dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> CODEC = StreamCodec.composite(
			Dialogues.Dialogue.STREAM_CODEC, OpenDialoguePayload::dialogue,
			ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::node,
			RingColor.STREAM_CODEC, OpenDialoguePayload::color,
			ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
			OpenDialoguePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
