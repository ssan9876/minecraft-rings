package xyz.ssander.rings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * The ring's own particles: motes of its energy that glow in the dark, flare and twinkle out. They take over from
 * vanilla's dust everywhere a ring's energy shows (see {@link RingColor#dust}), spawned the same way, and drawn by
 * the client's EnergyParticle.
 */
public final class RingParticles {
	/** A mote of energy: its colour (RGB) and size, on the same scale as dust's. */
	public record Energy(int color, float size) implements ParticleOptions {
		public static final MapCodec<Energy> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(Energy::color),
				Codec.floatRange(0.01F, 4.0F).fieldOf("size").forGetter(Energy::size)
		).apply(instance, Energy::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Energy> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Energy::color,
				ByteBufCodecs.FLOAT, Energy::size,
				Energy::new);

		@Override
		public ParticleType<Energy> getType() {
			return ENERGY;
		}
	}

	public static final ParticleType<Energy> ENERGY = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Rings.id("energy"),
			FabricParticleTypes.complex(Energy.CODEC, Energy.STREAM_CODEC));

	private RingParticles() {
	}

	/** Registers the particle types (loading this class does it). */
	static void init() {
	}
}
