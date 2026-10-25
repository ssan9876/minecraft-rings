package xyz.ssander.rings.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.PowerLanternBlock;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.network.RingAnimationPayload;

/**
 * Keyframed ring animations, played with Player Animation Library, and the energy effects that go with them. The
 * animations themselves are in {@code assets/rings/player_animations/rings.json}. They play on their own layer above
 * vanilla's poses and the flying pose, and only move the bones they key, so the rest of the player carries on as
 * normal.
 * <p>
 * The library is optional: without it the ring works the same, minus these animations (the energy effects still
 * show). Only {@link Library} touches its classes, and only once it's known to be installed.
 */
public final class RingAnimations {
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("player_animation_library");
	private static final Identifier LAYER = Rings.id("ring");
	/** Fist to the sky when the ring switches on. */
	public static final Identifier ACTIVATE = Rings.id("activate");
	/** The lantern oath: braced, ring fist thrust at the lantern, the other hand gripping the wrist. Loops while charging. */
	public static final Identifier CHARGE = Rings.id("charge");
	/** Lowers the arms from the charging pose when charging stops before the ring is full. */
	public static final Identifier CHARGE_END = Rings.id("charge_end");
	/** The ring is full: crouch, then throw the fist to the sky as the power bursts out. */
	public static final Identifier CHARGE_FULL = Rings.id("charge_full");
	/** The ring fist thrust out as a construct takes shape. */
	public static final Identifier CONSTRUCT = Rings.id("construct");
	/** Down on one knee, fist to the ground, after coming down from flight. */
	public static final Identifier LAND = Rings.id("land");
	/**
	 * The charging pose ends this many ticks after the last top-up. Top-ups come every 4 ticks while right-click is
	 * held, but over a real connection they can bunch up with longer gaps between, which mustn't restart the pose.
	 */
	private static final int CHARGE_HOLD_TICKS = 20;
	private static final int LOCAL_HOLD_TICKS = 5;
	/** Ticks to blend from one ring animation into the next. */
	private static final int BLEND_TICKS = 3;
	/** The full-charge shockwave grows for this many ticks; the burst off the raised fist follows. */
	private static final int SHOCKWAVE_TICKS = 7;
	private static final int BURST_TICKS = 16;

	private record Charging(int lastTopUp, int start, RingColor color) {
	}

	private record Burst(Player player, int start, RingColor color) {
	}

	/** Players holding the charging pose. */
	private static final Map<Player, Charging> CHARGING = new WeakHashMap<>();
	private static final List<Burst> BURSTS = new ArrayList<>();

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
					case CHARGE -> charge(player, payload.color());
					case CHARGE_FULL -> chargeFull(player, payload.color());
					case CONSTRUCT -> play(player, CONSTRUCT);
				}
			}
		});
	}

	public static void land(Player player) {
		play(player, LAND);
	}

	private static void charge(Player player, RingColor color) {
		// A top-up that arrives after the local player let go mustn't strike the pose again
		Minecraft minecraft = Minecraft.getInstance();
		if (player == minecraft.player && !minecraft.options.keyUse.isDown()) {
			return;
		}

		// Strike the pose once, then keep it while the top-ups keep coming
		Charging charging = CHARGING.get(player);
		if (charging == null) {
			play(player, CHARGE);
			CHARGING.put(player, new Charging(player.tickCount, player.tickCount, color));
		} else {
			CHARGING.put(player, new Charging(player.tickCount, charging.start(), color));
		}
	}

	private static void chargeFull(Player player, RingColor color) {
		CHARGING.remove(player);
		play(player, CHARGE_FULL);
		BURSTS.add(new Burst(player, player.tickCount, color));
	}

	/** Ends charging poses whose top-ups have stopped, and draws the charging energy. Runs every client tick. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			CHARGING.clear();
			BURSTS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}

		CHARGING.entrySet().removeIf(entry -> {
			Player player = entry.getKey();
			Charging charging = entry.getValue();
			if (player.isRemoved() || player.level() != level) {
				return true;
			}
			// The local player's own pose holds for as long as they hold right-click on a lantern, packets or not
			if (player == minecraft.player && minecraft.options.keyUse.isDown()
					&& minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
					&& level.getBlockState(hit.getBlockPos()).getBlock() instanceof PowerLanternBlock) {
				entry.setValue(new Charging(player.tickCount, charging.start(), charging.color()));
			}
			// Letting go ends the local player's pose straight away; other players' wait out the gaps
			int hold = player == minecraft.player ? LOCAL_HOLD_TICKS : CHARGE_HOLD_TICKS;
			if (player.tickCount - entry.getValue().lastTopUp() > hold) {
				play(player, CHARGE_END);
				return true;
			}
			chargingEnergy(level, player, player.tickCount - charging.start(), charging.color());
			return false;
		});

		BURSTS.removeIf(burst -> {
			int age = burst.player().tickCount - burst.start();
			if (burst.player().isRemoved() || burst.player().level() != level || age > BURST_TICKS) {
				return true;
			}
			fullChargeBurst(level, burst.player(), age, burst.color());
			return false;
		});
	}

	/** A double helix of energy spiralling up around the wearer, and a glow at the ring fist. */
	private static void chargingEnergy(ClientLevel level, Player player, int age, RingColor color) {
		RandomSource random = player.getRandom();
		for (int strand = 0; strand < 2; strand++) {
			float angle = age * 0.45F + strand * Mth.PI;
			float height = (age * 0.09F + strand * 0.9F) % 1.9F;
			float radius = 0.8F - height * 0.2F;
			level.addParticle(color.dust(strand * 0.4F, 0.8F),
					player.getX() + Mth.cos(angle) * radius, player.getY() + height, player.getZ() + Mth.sin(angle) * radius,
					0.0, 0.02, 0.0);
		}

		Vec3 fist = ringFist(player);
		level.addParticle(color.dust(0.6F, 0.7F),
				fist.x + (random.nextDouble() - 0.5) * 0.15, fist.y + (random.nextDouble() - 0.5) * 0.15, fist.z + (random.nextDouble() - 0.5) * 0.15,
				0.0, 0.0, 0.0);
	}

	/** A ring of energy blasting outward along the ground, then a fountain of it off the fist raised to the sky. */
	private static void fullChargeBurst(ClientLevel level, Player player, int age, RingColor color) {
		RandomSource random = player.getRandom();
		if (age <= SHOCKWAVE_TICKS) {
			float radius = 0.4F + age * 0.45F;
			int count = 12 + age * 4;
			for (int i = 0; i < count; i++) {
				float angle = Mth.TWO_PI * i / count + random.nextFloat() * 0.1F;
				level.addParticle(color.dust(age < 2 ? 0.5F : 0.0F, 1.2F),
						player.getX() + Mth.cos(angle) * radius, player.getY() + 0.15 + random.nextDouble() * 0.2, player.getZ() + Mth.sin(angle) * radius,
						0.0, 0.0, 0.0);
			}
		}
		if (age >= 6) {
			// The fist is up by now (see charge_full): spray from above the head
			for (int i = 0; i < 3; i++) {
				level.addParticle(color.dust(random.nextFloat() * 0.6F, 0.9F),
						player.getX() + (random.nextDouble() - 0.5) * 0.3, player.getY() + 2.3 + random.nextDouble() * 0.5, player.getZ() + (random.nextDouble() - 0.5) * 0.3,
						0.0, 0.05, 0.0);
			}
		}
	}

	/** Roughly where the ring hand is in the charging pose: held out in front, at chest height. */
	private static Vec3 ringFist(Player player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		return player.position().add(0.0, 1.05, 0.0).add(forward.scale(0.7)).add(right.scale(0.15));
	}

	public static void play(Player player, Identifier animation) {
		if (ENABLED) {
			Library.play(player, animation);
		}
	}

	/** Cuts off the ring animation playing on the player, if any. */
	public static void stop(Player player) {
		CHARGING.remove(player);
		if (ENABLED) {
			Library.stop(player);
		}
	}

	/** Whether that ring animation is playing on the player. */
	public static boolean isPlaying(Player player, Identifier animation) {
		return ENABLED && Library.isPlaying(player, animation);
	}

	/** Everything that uses Player Animation Library's classes. */
	private static final class Library {
		/** In first person, the charging animations show your own arms (the third-person ones) doing the oath. */
		private static final FirstPersonConfiguration FIRST_PERSON_ARMS = new FirstPersonConfiguration()
				.setShowRightArm(true)
				.setShowLeftArm(true);

		static void registerLayer() {
			// The layer only plays animations triggered from here, so the state handler never starts one itself.
			// 1500 and up is for gameplay animations, above emotes (1000).
			PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500, avatar -> {
				PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, animSetter) -> PlayState.STOP);
				controller.setFirstPersonModeHandler(c -> isCharging((PlayerAnimationController) c)
						? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
				controller.setFirstPersonConfiguration(FIRST_PERSON_ARMS);
				return controller;
			});
		}

		static void play(Player player, Identifier animation) {
			if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller) {
				// Blend from whatever ring animation is playing, or from the vanilla pose
				controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(BLEND_TICKS, EasingType.EASE_IN_OUT_SINE), animation);
			}
		}

		static void stop(Player player) {
			if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller) {
				controller.stopTriggeredAnimation();
			}
		}

		static boolean isPlaying(Player player, Identifier animation) {
			return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller
					&& isPlaying(controller, animation);
		}

		private static boolean isPlaying(PlayerAnimationController controller, Identifier animation) {
			return controller.isActive() && controller.getCurrentAnimationInstance() == PlayerAnimResources.getAnimation(animation);
		}

		private static boolean isCharging(PlayerAnimationController controller) {
			return isPlaying(controller, CHARGE) || isPlaying(controller, CHARGE_END) || isPlaying(controller, CHARGE_FULL);
		}
	}
}
