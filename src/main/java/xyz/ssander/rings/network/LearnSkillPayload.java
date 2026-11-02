package xyz.ssander.rings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import xyz.ssander.rings.Rings;
import xyz.ssander.rings.Skill;

/** Sent by the client when the player picks a skill to learn on the skill screen. The server checks they can. */
public record LearnSkillPayload(Skill skill) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LearnSkillPayload> TYPE = new CustomPacketPayload.Type<>(Rings.id("learn_skill"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LearnSkillPayload> CODEC = Skill.STREAM_CODEC
			.<RegistryFriendlyByteBuf>cast().map(LearnSkillPayload::new, LearnSkillPayload::skill);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
