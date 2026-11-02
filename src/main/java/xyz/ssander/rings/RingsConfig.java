package xyz.ssander.rings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Settings for server owners, in {@code config/rings.json} (written with the defaults the first time the server starts,
 * and read each time it starts). Anything left out keeps its default.
 */
public final class RingsConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The settings, with their defaults. Field names are the keys in the file. */
	public static final class Values {
		/** Ring experience earned for everything, as a multiple. */
		public double xpMultiplier = 1.0;
		/** Ring experience from the Guardians' missions, as a multiple (on top of xpMultiplier). */
		public double missionXpMultiplier = 1.0;
		/** The ring level the Devourer starts hunting at, and the odds of it coming each check (one in this many). */
		public int devourerLevel = 20;
		public int devourerChance = 60;
		/** Will shards a Guardian asks to unmake what a player has learned. */
		public int respecShards = 8;
		/** Ring experience for winning and losing a duel. */
		public int duelWinXp = 60;
		public int duelLoseXp = 15;
		/** How often a ship falls, in minutes, until an operator sets it with /rings crashes. */
		public int crashEveryMinutes = 150;
		/** The level players who already had a ring when levels came in start at. */
		public int veteranLevel = 10;
	}

	private static Values values = new Values();

	private RingsConfig() {
	}

	public static Values get() {
		return values;
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
	}

	/** Reads config/rings.json, writing it with the defaults if it isn't there. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("rings.json");
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					Values read = GSON.fromJson(reader, Values.class);
					values = read != null ? read : new Values();
				}
			} else {
				values = new Values();
			}
			// Rewritten, so settings added since appear with their defaults
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(values, writer);
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Couldn't read {}, using the defaults: {}", path, e.getMessage());
			values = new Values();
		}
	}
}
