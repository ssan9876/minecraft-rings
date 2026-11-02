package xyz.ssander.rings.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * Sent to a player who talks to a crash site's pilot or console: which dialogue, where it starts, the colour of the
 * crashed ring-bearer, the one speaking (to show them, and to answer to), or -1 for the console, and anything else
 * the words need filled in (from %3$s on).
 */
public record OpenDialoguePayload(Dialogues.Dialogue dialogue, String node, RingColor color, int entityId, List<Component> args)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenDialoguePayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("open_dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> CODEC = StreamCodec.composite(
			Dialogues.Dialogue.STREAM_CODEC, OpenDialoguePayload::dialogue,
			ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::node,
			RingColor.STREAM_CODEC, OpenDialoguePayload::color,
			ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenDialoguePayload::args,
			OpenDialoguePayload::new);

	public OpenDialoguePayload(Dialogues.Dialogue dialogue, String node, RingColor color, int entityId) {
		this(dialogue, node, color, entityId, List.of());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
