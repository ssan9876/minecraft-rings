package xyz.ssander.rings.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import xyz.ssander.rings.Rings;

/**
 * Tells players when a newer Rings has come out than the one they're running, so after the server updates they
 * know to close the game and run join again (which installs it). Checks the published mod list, at most every few
 * minutes, whenever the title, multiplayer or disconnected screen opens.
 */
public final class UpdateNotice {
	/** The mod list the join scripts install from. */
	private static final URI MOD_LIST = URI.create("https://raw.githubusercontent.com/ssan9876/minecraft-rings/main/mods.txt");
	private static final Pattern RINGS_JAR = Pattern.compile("/rings-([0-9][0-9A-Za-z.+-]*)\\.jar\\s*$", Pattern.MULTILINE);
	private static final long CHECK_EVERY_MILLIS = 5 * 60 * 1000;

	private static long lastCheck;
	private static volatile boolean checking;

	private UpdateNotice() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof DisconnectedScreen) {
				check(client);
			}
		});
	}

	private static void check(Minecraft client) {
		long now = System.currentTimeMillis();
		if (checking || now - lastCheck < CHECK_EVERY_MILLIS) {
			return;
		}
		lastCheck = now;
		checking = true;

		HttpRequest request = HttpRequest.newBuilder(MOD_LIST).timeout(Duration.ofSeconds(10)).GET().build();
		HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					if (response.statusCode() == 200) {
						String latest = latestVersion(response.body());
						if (latest != null && isNewer(latest)) {
							client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
									Component.translatable("toast.rings.update", latest),
									Component.translatable("toast.rings.update_how")));
						}
					}
				})
				.whenComplete((result, error) -> checking = false);
	}

	private static String latestVersion(String modList) {
		Matcher matcher = RINGS_JAR.matcher(modList);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static boolean isNewer(String latest) {
		Version running = FabricLoader.getInstance().getModContainer(Rings.MOD_ID).orElseThrow().getMetadata().getVersion();
		try {
			return SemanticVersion.parse(latest).compareTo(running) > 0;
		} catch (VersionParsingException e) {
			return false;
		}
	}
}
