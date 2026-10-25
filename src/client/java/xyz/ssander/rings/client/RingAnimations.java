package xyz.ssander.rings.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * Keyframed ring animations, played with Player Animation Library. The animations themselves are in
 * {@code assets/rings/player_animations/rings.json}. They play on their own layer above vanilla's poses and the
 * flying pose, and only move the bones they key, so the rest of the player carries on as normal.
 * <p>
 * The library is optional: without it the ring works the same, minus these animations. Only {@link Library}
 * touches its classes, and only once it's known to be installed.
 */
public final class RingAnimations {
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("player_animation_library");
	private static final Identifier LAYER = Rings.id("ring");
	/** Fist to the sky when the ring switches on. */
	public static final Identifier ACTIVATE = Rings.id("activate");
	/** Ring held out to a lantern, kept while charging. */
	public static final Identifier CHARGE = Rings.id("charge");
	/** Lowers the arm from the charging pose. */
	public static final Identifier CHARGE_END = Rings.id("charge_end");
	/** Down on one knee, fist to the ground, after coming down from flight. */
	public static final Identifier LAND = Rings.id("land");
	/** The charging pose ends this many ticks after the last top-up (they come every 4 ticks while right-click is held). */
	private static final int CHARGE_HOLD_TICKS = 8;

	/** Players holding the charging pose, and when their last top-up came. */
	private static final Map<Player, Integer> CHARGING = new WeakHashMap<>();

	private RingAnimations() {
	}

	public static void init() {
		if (ENABLED) {
			Library.registerLayer();
		}

		ClientPlayNetworking.registerGlobalReceiver(RingAnimationPayload.TYPE, (payload, context) -> {
			ClientLevel level = context.client().level;
			if (level != null && level.getEntity(payload.entityId()) instanceof Player player) {
				switch (payload.kind()) {
					case ACTIVATE -> play(player, ACTIVATE);
					case CHARGE -> charge(player);
				}
			}
		});
	}

	public static void land(Player player) {
		play(player, LAND);
	}

	private static void charge(Player player) {
		// Raise the ring once, then keep it there while the top-ups keep coming
		if (CHARGING.put(player, player.tickCount) == null) {
			play(player, CHARGE);
		}
	}

	/** Ends charging poses whose top-ups have stopped. Runs every client tick. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}

		CHARGING.entrySet().removeIf(entry -> {
			Player player = entry.getKey();
			if (player.isRemoved()) {
				return true;
			}
			if (player.tickCount - entry.getValue() > CHARGE_HOLD_TICKS) {
				play(player, CHARGE_END);
				return true;
			}
			return false;
		});
	}

	public static void play(Player player, Identifier animation) {
		if (ENABLED) {
			Library.play(player, animation);
		}
	}

	/** Whether that ring animation is playing on the player. */
	public static boolean isPlaying(Player player, Identifier animation) {
		return ENABLED && Library.isPlaying(player, animation);
	}

	/** Everything that uses Player Animation Library's classes. */
	private static final class Library {
		static void registerLayer() {
			// The layer only plays animations triggered from here, so the state handler never starts one itself.
			// 1500 and up is for gameplay animations, above emotes (1000).
			PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500,
					avatar -> new PlayerAnimationController(avatar, (controller, state, animSetter) -> PlayState.STOP));
		}

		static void play(Player player, Identifier animation) {
			if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller) {
				controller.triggerAnimation(animation);
			}
		}

		static boolean isPlaying(Player player, Identifier animation) {
			return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller
					&& controller.isActive() && controller.getCurrentAnimationInstance() == PlayerAnimResources.getAnimation(animation);
		}
	}
}
