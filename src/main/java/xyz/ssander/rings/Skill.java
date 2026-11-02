package xyz.ssander.rings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The ring's skill tree. A new ring-bearer can only switch the ring on, hover, fire a weak beam, make the simplest
 * constructs, charge it and call it back; everything else is learned here, with the skill points the ring gives at each
 * level (see {@link Mastery}). Each skill needs the one above it in its branch, and some need the ring to have grown to
 * a level first. On the skill screen a skill sits in column {@code x} (0 to 10) and row {@code y} (0 to 2).
 */
public enum Skill implements StringRepresentable {
	// Flight
	/** Sprint forward while flying for the ring's elytra mode. */
	SOARING("soaring", Branch.FLIGHT, 0, 0, null, 1, 1),
	/** Sneak and forward in elytra mode to blast off, up to 50 blocks a second. */
	SUPER_FLIGHT("super_flight", Branch.FLIGHT, 0, 1, SOARING, 2, 4),
	/** Super flight up to 80 blocks a second, breaking the sound barrier. */
	SONIC("sonic", Branch.FLIGHT, 0, 2, SUPER_FLIGHT, 2, 12),
	/** Fly up out of the sky into space, and breathe there. */
	SPACEFARER("spacefarer", Branch.FLIGHT, 1, 2, SUPER_FLIGHT, 2, 8),

	// Beam and spectrum
	/** The beam hits for 2 hearts instead of 1.25. */
	FOCUSED_BEAM("focused_beam", Branch.BEAM, 2, 0, null, 1, 1),
	/** The beam hits for 3 hearts. */
	LANCE("lance", Branch.BEAM, 2, 1, FOCUSED_BEAM, 1, 6),
	/** The beam hits for 4.5 hearts and sets what it hits alight. */
	ANNIHILATOR("annihilator", Branch.BEAM, 2, 2, LANCE, 2, 15),
	/** The ring colour's own power, on the power key. */
	SPECTRUM("spectrum", Branch.BEAM, 3, 0, null, 1, 2),
	/** The colour's power costs 40% less and reaches half as far again. */
	SPECTRUM_MASTERY("spectrum_mastery", Branch.BEAM, 3, 1, SPECTRUM, 2, 10),

	// Constructs
	/** The dome, cage, hammer and the construct tools. */
	SHAPER("shaper", Branch.CONSTRUCTS, 5, 0, null, 1, 1),
	/** The construct sword and shield. */
	ARMORY("armory", Branch.CONSTRUCTS, 4, 1, SHAPER, 1, 3),
	/** The construct wolf and golem. */
	LIVING_LIGHT("living_light", Branch.CONSTRUCTS, 5, 1, SHAPER, 2, 6),
	/** Walls, bridges, stairs, domes, cages and creatures last twice as long. */
	ENDURING("enduring", Branch.CONSTRUCTS, 6, 1, SHAPER, 1, 5),
	/** The construct jet. */
	STARSHIP("starship", Branch.CONSTRUCTS, 5, 2, LIVING_LIGHT, 1, 10),
	/** Construct any item, from the eye in the middle of the wheel. */
	MATTER("matter", Branch.CONSTRUCTS, 6, 2, ENDURING, 3, 14),

	// Defence
	/** The ring's armor goes from +4 to +8, with +2 toughness. */
	HARD_LIGHT_SKIN("hard_light_skin", Branch.DEFENSE, 7, 0, null, 1, 1),
	/** A hit that would kill you is stopped, for a quarter of the ring's charge. */
	GUARDIAN_ANGEL("guardian_angel", Branch.DEFENSE, 7, 1, HARD_LIGHT_SKIN, 2, 5),
	/** The ring's armor goes to +12, with +4 toughness, and you can't be knocked back as far. */
	FORTRESS("fortress", Branch.DEFENSE, 7, 2, GUARDIAN_ANGEL, 2, 12),
	/** A force field round you, on the force field key. */
	FORCE_FIELD("force_field", Branch.DEFENSE, 8, 0, null, 1, 2),
	/** Carry a creature or player in a bubble. */
	BUBBLES("bubbles", Branch.DEFENSE, 8, 1, FORCE_FIELD, 1, 4),
	/** The force field and bubbles take hits for half the charge. */
	AEGIS("aegis", Branch.DEFENSE, 8, 2, BUBBLES, 1, 9),

	// Will
	/** The ring scan, on the scan key. */
	SCAN("scan", Branch.WILL, 9, 0, null, 1, 1),
	/** The scan reaches 80 blocks for creatures and 28 for ores. */
	DEEP_SCAN("deep_scan", Branch.WILL, 9, 1, SCAN, 1, 6),
	/** Everything the ring does uses a quarter less charge. */
	EFFICIENCY("efficiency", Branch.WILL, 10, 0, null, 1, 3),
	/** Everything the ring does uses half the charge. */
	WILLPOWER("willpower", Branch.WILL, 10, 1, EFFICIENCY, 2, 10),
	/** Lanterns charge the ring twice as fast. */
	LANTERN_BOND("lantern_bond", Branch.WILL, 10, 2, WILLPOWER, 1, 8);

	/** The tree's branches, each over its own columns on the skill screen. */
	public enum Branch {
		FLIGHT(0, 1),
		BEAM(2, 3),
		CONSTRUCTS(4, 6),
		DEFENSE(7, 8),
		WILL(9, 10);

		private final int firstColumn;
		private final int lastColumn;

		Branch(int firstColumn, int lastColumn) {
			this.firstColumn = firstColumn;
			this.lastColumn = lastColumn;
		}

		public int firstColumn() {
			return this.firstColumn;
		}

		public int lastColumn() {
			return this.lastColumn;
		}

		public String translationKey() {
			return "skill_branch.rings." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final StringRepresentable.EnumCodec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);
	public static final StreamCodec<ByteBuf, Skill> STREAM_CODEC = ByteBufCodecs.idMapper(
			ByIdMap.continuous(Skill::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Skill::ordinal);
	/** Columns and rows on the skill screen. */
	public static final int COLUMNS = 11;
	public static final int ROWS = 3;

	private final String id;
	private final Branch branch;
	private final int x;
	private final int y;
	private final @Nullable Skill parent;
	private final int cost;
	private final int minLevel;

	Skill(String id, Branch branch, int x, int y, @Nullable Skill parent, int cost, int minLevel) {
		this.id = id;
		this.branch = branch;
		this.x = x;
		this.y = y;
		this.parent = parent;
		this.cost = cost;
		this.minLevel = minLevel;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public String id() {
		return this.id;
	}

	public Branch branch() {
		return this.branch;
	}

	public int x() {
		return this.x;
	}

	public int y() {
		return this.y;
	}

	/** The skill this one grows out of, which has to be learned first. */
	public @Nullable Skill parent() {
		return this.parent;
	}

	/** Skill points it takes. */
	public int cost() {
		return this.cost;
	}

	/** The ring level it can be learned at. */
	public int minLevel() {
		return this.minLevel;
	}

	public String translationKey() {
		return "skill.rings." + this.id;
	}

	public String descriptionKey() {
		return "skill.rings." + this.id + ".desc";
	}

	/** The total skill points the whole tree takes. */
	public static int totalCost() {
		int total = 0;
		for (Skill skill : values()) {
			total += skill.cost;
		}
		return total;
	}
}
