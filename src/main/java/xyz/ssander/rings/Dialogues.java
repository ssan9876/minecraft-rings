package xyz.ssander.rings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import org.jspecify.annotations.Nullable;

/**
 * What the crash sites have to say: the dying ring-bearer at the wreck, and the ship's console with its flight
 * recorder. Each dialogue is a set of nodes; a node is something said ({@code dialogue.rings.<dialogue>.<node>}, with
 * the pilot's name as %1$s and the ring's colour as %2$s) and the answers the player can give
 * ({@code dialogue.rings.<dialogue>.option.<option>}), each leading to another node or ending the talk. The client
 * walks the nodes itself; only taking the ring goes to the server.
 */
public final class Dialogues {
	/** What choosing an answer does, besides going to its next node. */
	public enum Action {
		NONE,
		/** Takes the dying ring-bearer's ring (the server decides whether they can). */
		TAKE_RING,
		/** Asks a Guardian for work (the server answers with the player's task). */
		MISSION,
		/** Tells a Guardian the task is done (the server checks, and answers). */
		TURN_IN,
		/** Asks a Guardian to unmake everything the player has learned, for will shards (the server answers). */
		RESPEC;

		/** Whether the server answers it with what's said next, rather than the answer leading on by itself. */
		public boolean serverAnswers() {
			return this == MISSION || this == TURN_IN || this == RESPEC;
		}
	}

	/** An answer: what it says, the node it leads to (null to end the talk), and what it does. */
	public record Option(String id, @Nullable String next, Action action) {
		static Option to(String id, String next) {
			return new Option(id, next, Action.NONE);
		}

		static Option end(String id) {
			return new Option(id, null, Action.NONE);
		}
	}

	/** Something said, by the pilot or the ship's computer, and the answers to it. */
	public record Node(String id, Speaker speaker, List<Option> options) {
	}

	public enum Speaker {
		PILOT,
		SHIP,
		GUARDIAN
	}

	public enum Dialogue {
		PILOT("pilot"),
		SHIP_LOG("ship_log"),
		GUARDIAN("guardian");

		public static final StreamCodec<ByteBuf, Dialogue> STREAM_CODEC = ByteBufCodecs.idMapper(
				ByIdMap.continuous(Dialogue::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Dialogue::ordinal);

		private final String id;
		private final Map<String, Node> nodes = new HashMap<>();

		Dialogue(String id) {
			this.id = id;
		}

		public String id() {
			return this.id;
		}

		public @Nullable Node node(String id) {
			// The nodes are added by Dialogues as it loads, which using this enum alone doesn't do
			Dialogues.load();
			return this.nodes.get(id);
		}

		public String textKey(Node node) {
			return "dialogue.rings." + this.id + "." + node.id();
		}

		public String optionKey(Option option) {
			return "dialogue.rings." + this.id + ".option." + option.id();
		}

		private void add(String id, Speaker speaker, Option... options) {
			this.nodes.put(id, new Node(id, speaker, List.of(options)));
		}
	}

	/** Where the pilot's talk starts: they're dying, and the ring's search has led someone to them. */
	public static final String PILOT_START = "start";
	/** Where it starts for a player who already wears a ring. */
	public static final String PILOT_HAS_RING = "has_ring";
	/** The pilot's last words, once the ring is taken. */
	public static final String PILOT_TAKEN = "taken";
	/** The console's log, before and after the ring has found its new bearer. */
	public static final String LOG_START = "start";
	public static final String LOG_AFTER = "after";
	/** How many of the Oan lore sayings a Guardian has (nodes lore_1 on). */
	public static final int GUARDIAN_LORE = 8;
	/** Will shards a Guardian asks to unmake what a player has learned. */
	public static final int RESPEC_SHARDS = 8;
	/** Leads back to wherever the talk started. */
	public static final String BACK = "@start";

	static {
		Dialogue pilot = Dialogue.PILOT;
		pilot.add(PILOT_START, Speaker.PILOT,
				Option.to("who", "who"), Option.to("crash", "crash"), Option.to("ring", "ring"), Option.to("help", "help"));
		pilot.add("who", Speaker.PILOT,
				Option.to("crash", "crash"), Option.to("ring", "ring"), Option.to("corps", "corps"));
		pilot.add("corps", Speaker.PILOT,
				Option.to("ring", "ring"), Option.to("crash", "crash"));
		pilot.add("crash", Speaker.PILOT,
				Option.to("enemy", "enemy"), Option.to("ring", "ring"), Option.to("help", "help"));
		pilot.add("enemy", Speaker.PILOT,
				Option.to("ring", "ring"));
		pilot.add("help", Speaker.PILOT,
				Option.to("listen", "ring"));
		pilot.add("ring", Speaker.PILOT,
				Option.to("why", "why"), Option.to("power", "power"), Option.to("unworthy", "unworthy"),
				new Option("accept", PILOT_TAKEN, Action.TAKE_RING));
		pilot.add("why", Speaker.PILOT,
				Option.to("power", "power"), Option.to("unworthy", "unworthy"), new Option("accept", PILOT_TAKEN, Action.TAKE_RING));
		pilot.add("power", Speaker.PILOT,
				Option.to("grow", "grow"), Option.to("why", "why"), new Option("accept", PILOT_TAKEN, Action.TAKE_RING));
		pilot.add("grow", Speaker.PILOT,
				Option.to("lantern", "lantern"), new Option("accept", PILOT_TAKEN, Action.TAKE_RING));
		pilot.add("lantern", Speaker.PILOT,
				new Option("accept", PILOT_TAKEN, Action.TAKE_RING), Option.to("why", "why"));
		pilot.add("unworthy", Speaker.PILOT,
				new Option("accept_brave", PILOT_TAKEN, Action.TAKE_RING), Option.end("leave"));
		pilot.add(PILOT_TAKEN, Speaker.PILOT,
				Option.to("promise", "farewell"));
		pilot.add("farewell", Speaker.PILOT,
				Option.end("goodbye"));
		pilot.add(PILOT_HAS_RING, Speaker.PILOT,
				Option.to("who", "who_ringed"), Option.end("leave"));
		pilot.add("who_ringed", Speaker.PILOT,
				Option.end("leave"));

		Dialogue log = Dialogue.SHIP_LOG;
		log.add(LOG_START, Speaker.SHIP,
				Option.to("entry_1", "entry_1"), Option.to("entry_2", "entry_2"), Option.to("entry_3", "entry_3"),
				Option.to("beacon", "beacon"), Option.end("close"));
		log.add("entry_1", Speaker.SHIP,
				Option.to("next", "entry_2"), Option.to("back", BACK));
		log.add("entry_2", Speaker.SHIP,
				Option.to("next", "entry_3"), Option.to("back", BACK));
		log.add("entry_3", Speaker.SHIP,
				Option.to("beacon", "beacon"), Option.to("back", BACK));
		log.add("beacon", Speaker.SHIP,
				Option.to("back", BACK), Option.end("close"));
		log.add(LOG_AFTER, Speaker.SHIP,
				Option.to("entry_1", "entry_1"), Option.to("final", "final"), Option.end("close"));
		log.add("final", Speaker.SHIP,
				Option.to("back", BACK), Option.end("close"));

		// The Guardians: their lore, their missions, and unmaking what you've learned. What the server answers is
		// sent as its own node, so those have no answers leading to them here.
		Dialogue guardian = Dialogue.GUARDIAN;
		Option work = new Option("work", null, Action.MISSION);
		guardian.add("start", Speaker.GUARDIAN,
				Option.to("lore", "lore_1"), work, Option.to("respec", "respec_ask"), Option.end("farewell"));
		for (int i = 1; i <= GUARDIAN_LORE; i++) {
			guardian.add("lore_" + i, Speaker.GUARDIAN,
					Option.to("more", "lore_" + (i % GUARDIAN_LORE + 1)), Option.to("back", "start"));
		}
		guardian.add("respec_ask", Speaker.GUARDIAN,
				new Option("respec_yes", null, Action.RESPEC), Option.to("respec_no", "start"));
		guardian.add("respec_done", Speaker.GUARDIAN, Option.to("back", "start"), Option.end("farewell"));
		guardian.add("respec_poor", Speaker.GUARDIAN, Option.to("back", "start"), Option.end("farewell"));
		guardian.add("respec_nothing", Speaker.GUARDIAN, Option.to("back", "start"), Option.end("farewell"));
		guardian.add("mission_new", Speaker.GUARDIAN, Option.end("will_do"), Option.to("back", "start"));
		guardian.add("mission_active", Speaker.GUARDIAN,
				new Option("done", null, Action.TURN_IN), Option.end("not_yet"), Option.to("back", "start"));
		guardian.add("mission_not_done", Speaker.GUARDIAN, Option.end("will_do"), Option.to("back", "start"));
		guardian.add("mission_none", Speaker.GUARDIAN, work, Option.to("back", "start"));
		guardian.add("mission_done", Speaker.GUARDIAN, work, Option.to("back", "start"), Option.end("farewell"));
		guardian.add("no_ring", Speaker.GUARDIAN, Option.to("lore", "lore_1"), Option.end("farewell"));
	}

	private Dialogues() {
	}

	/** Loads the class, and with it every dialogue's nodes. */
	static void load() {
	}
}
