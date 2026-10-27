package xyz.ssander.rings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.MapColor;

/**
 * The ring colours. Each has its own ring, suit and lantern, and a ring only charges at a lantern of its colour.
 * Resource files for each colour come from tools/gen_resources.py, which keeps the same ids.
 */
public enum RingColor implements StringRepresentable {
	// Green keeps its 1.0 ids so rings and lanterns already in worlds stay what they are
	GREEN("green", "hero_ring", "power_lantern", 0x3CFF5A, 0x3CFF5A, MapColor.EMERALD),
	BLUE("blue", "blue_ring", "blue_power_lantern", 0x3C9BFF, 0x5AAAFF, MapColor.LAPIS),
	RED("red", "red_ring", "red_power_lantern", 0xFF3030, 0xFF4A4A, MapColor.COLOR_RED),
	PINK("pink", "pink_ring", "pink_power_lantern", 0xFF5FC8, 0xFF78D2, MapColor.COLOR_PINK),
	ORANGE("orange", "orange_ring", "orange_power_lantern", 0xFF9A1E, 0xFFA43C, MapColor.COLOR_ORANGE),
	WHITE("white", "white_ring", "white_power_lantern", 0xF2F6FF, 0xF2F6FF, MapColor.SNOW),
	// Black energy is near black in the world, but needs a lighter shade to read on the HUD
	BLACK("black", "black_ring", "black_power_lantern", 0x24242C, 0x9C9CB4, MapColor.COLOR_BLACK),
	// Added after the first seven, so their numbers (saved in blocks and sent over the network) stay the same
	YELLOW("yellow", "yellow_ring", "yellow_power_lantern", 0xFFD21E, 0xFFDC46, MapColor.GOLD);

	public static final StreamCodec<ByteBuf, RingColor> STREAM_CODEC = ByteBufCodecs.idMapper(
			ByIdMap.continuous(RingColor::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), RingColor::ordinal);

	private final String name;
	private final String ringId;
	private final String lanternId;
	private final int energy;
	private final int ui;
	private final MapColor mapColor;

	RingColor(String name, String ringId, String lanternId, int energy, int ui, MapColor mapColor) {
		this.name = name;
		this.ringId = ringId;
		this.lanternId = lanternId;
		this.energy = energy;
		this.ui = ui;
		this.mapColor = mapColor;
	}

	public String id() {
		return this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String ringId() {
		return this.ringId;
	}

	public String lanternId() {
		return this.lanternId;
	}

	/** The energy's colour (RGB) in the world: particles and the wearer's outline. */
	public int energy() {
		return this.energy;
	}

	/** The colour (RGB) of the power meter and the ring's charge bar. */
	public int ui() {
		return this.ui;
	}

	public MapColor mapColor() {
		return this.mapColor;
	}

	/** The classic suit a wearer of this colour's ring turns into. */
	public Identifier costume() {
		return this.costume(SuitStyle.CLASSIC);
	}

	/** This colour's suit of that style (or, for no suit, the costume that only outlines the wearer in the colour). */
	public Identifier costume(SuitStyle style) {
		return Rings.id(this.name + "_" + style.id());
	}

	/** A glowing mote of this energy (see RingParticles), mixed toward white by {@code light} (0 to 1). */
	public RingParticles.Energy dust(float light, float size) {
		return new RingParticles.Energy(ARGB.srgbLerp(light, this.energy, 0xFFFFFF), size);
	}
}
