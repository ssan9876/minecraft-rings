package xyz.ssander.rings.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.network.FireBeamPayload;

/**
 * Draws ring beams: glowing layers of the ring's energy from the wearer's ring fist to whatever they're looking at,
 * with sparks where it hits and a hum while it fires. Also sends the beam key to the server.
 */
public final class RingBeams {
	/** Widths of the beam's layers, outer glow to white-hot core, in blocks. */
	private static final float GLOW_WIDTH = 0.32F;
	private static final float BEAM_WIDTH = 0.15F;
	private static final float CORE_WIDTH = 0.05F;

	private static boolean keyWasDown;
	/** The hum playing for each firing player. */
	private static final Map<Player, BeamSound> SOUNDS = new WeakHashMap<>();

	private RingBeams() {
	}

	/** Tells the server when the beam key goes down or up, and keeps the sparks and hums going. Runs every client tick. */
	public static void tick(Minecraft minecraft) {
		boolean down = minecraft.player != null && minecraft.gui.screen() == null && RingsClient.beamKey.isDown();
		if (down != keyWasDown && minecraft.player != null) {
			ClientPlayNetworking.send(new FireBeamPayload(down));
		}
		keyWasDown = down;

		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (Player player : level.players()) {
			RingColor color = firingColor(player);
			if (color == null) {
				SOUNDS.remove(player);
				continue;
			}
			sparks(level, player, color);
			BeamSound sound = SOUNDS.get(player);
			if (sound == null) {
				// Just started firing: the ring arm takes aim, so it drops whatever ring animation it was in
				RingAnimations.stop(player);
			}
			if (sound == null || sound.isStopped()) {
				sound = new BeamSound(player);
				SOUNDS.put(player, sound);
				minecraft.getSoundManager().play(sound);
			}
		}
	}

	/** The colour of the beam the player is firing, or null if they aren't. */
	private static RingColor firingColor(Player player) {
		if (!RingBeam.isFiring(player)) {
			return null;
		}
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		return costume != null ? costume.color() : RingColor.GREEN;
	}

	private static void sparks(ClientLevel level, Player player, RingColor color) {
		RandomSource random = player.getRandom();
		RingBeam.Hit hit = RingBeam.trace(player, 1.0F);
		Vec3 back = player.getViewVector(1.0F).scale(-0.15);
		for (int i = 0; i < (hit.entity() != null ? 4 : 2); i++) {
			level.addParticle(color.dust(random.nextFloat() * 0.7F, 0.6F + random.nextFloat() * 0.6F),
					hit.end().x + back.x + (random.nextDouble() - 0.5) * 0.4,
					hit.end().y + back.y + (random.nextDouble() - 0.5) * 0.4,
					hit.end().z + back.z + (random.nextDouble() - 0.5) * 0.4,
					0.0, 0.0, 0.0);
		}
	}

	/** Submits every beam being fired this frame. Runs as the level is drawn. */
	public static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = (level.getGameTime() % 24000L) + partialTick;
		for (Player player : level.players()) {
			RingColor color = firingColor(player);
			if (color == null) {
				continue;
			}

			boolean ownFirstPerson = player == minecraft.getCameraEntity() && camera.isFirstPerson;
			Vec3 from = fist(player, partialTick, ownFirstPerson).subtract(camera.pos);
			Vec3 to = RingBeam.trace(player, partialTick).end().subtract(camera.pos);
			float pulse = 1.0F + 0.15F * Mth.sin(time * 0.9F + player.getId());

			// Additive glow can't make black, so the black ring's beam is drawn as dark translucent layers instead
			boolean dark = color == RingColor.BLACK;
			RenderType type = dark ? RenderTypes.debugQuads() : RenderTypes.lightning();
			int energy = color.energy();
			int core = dark ? 0x101014 : ARGB.srgbLerp(0.75F, energy, 0xFFFFFF);
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), type, (pose, consumer) -> {
				Matrix4f matrix = pose.pose();
				ribbon(matrix, consumer, from, to, GLOW_WIDTH * pulse, energy, dark ? 0.35F : 0.3F);
				ribbon(matrix, consumer, from, to, BEAM_WIDTH * pulse, energy, dark ? 0.6F : 0.7F);
				ribbon(matrix, consumer, from, to, CORE_WIDTH, core, 1.0F);
				// A flare where it hits
				flare(matrix, consumer, to, 0.35F * pulse, energy, dark ? 0.5F : 0.6F);
				flare(matrix, consumer, to, 0.15F, core, 1.0F);
			});
		}
	}

	/**
	 * Where the beam leaves the ring: the fist of the arm held out along the line of sight. In first person it starts
	 * low and to the right of the view, where the hand is.
	 */
	private static Vec3 fist(Player player, float partialTick, boolean ownFirstPerson) {
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		if (ownFirstPerson) {
			Vec3 up = right.cross(look);
			return player.getEyePosition(partialTick).add(look.scale(0.55)).add(right.scale(0.3)).add(up.scale(-0.22));
		}

		float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		Vec3 shoulder = player.getPosition(partialTick).add(0.0, player.isCrouching() ? 1.05 : 1.29, 0.0).add(bodyRight.scale(0.31));
		return shoulder.add(look.scale(0.62));
	}

	/**
	 * A flat strip from one point to another, turned to face the camera (which sits at the origin). It starts narrow at
	 * the fist, which in first person is right in front of the camera.
	 */
	private static void ribbon(Matrix4f matrix, VertexConsumer consumer, Vec3 from, Vec3 to, float width, int rgb, float alpha) {
		Vec3 along = to.subtract(from);
		Vec3 toCamera = from.add(to).scale(-0.5);
		Vec3 side = along.cross(toCamera);
		if (side.lengthSqr() < 1.0E-8) {
			return;
		}
		side = side.normalize().scale(width / 2.0F);
		Vec3 start = side.scale(0.3);
		quad(matrix, consumer, from.add(start), from.subtract(start), to.subtract(side), to.add(side), rgb, alpha);
	}

	/** A square facing the camera, centred on a point. */
	private static void flare(Matrix4f matrix, VertexConsumer consumer, Vec3 at, float size, int rgb, float alpha) {
		Vec3 toCamera = at.scale(-1.0);
		if (toCamera.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 right = toCamera.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(toCamera).normalize();
		right = right.scale(size / 2.0F);
		up = up.scale(size / 2.0F);
		quad(matrix, consumer, at.add(right).add(up), at.subtract(right).add(up), at.subtract(right).subtract(up), at.add(right).subtract(up), rgb, alpha);
	}

	/**
	 * Corners a to d wind clockwise as seen from the camera for both callers above, so they go in reverse: the
	 * lightning pipeline culls faces turned away.
	 */
	private static void quad(Matrix4f matrix, VertexConsumer consumer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
		float red = ARGB.red(rgb) / 255.0F;
		float green = ARGB.green(rgb) / 255.0F;
		float blue = ARGB.blue(rgb) / 255.0F;
		for (Vec3 corner : new Vec3[] {d, c, b, a}) {
			consumer.addVertex(matrix, (float) corner.x, (float) corner.y, (float) corner.z).setColor(red, green, blue, alpha);
		}
	}

	/** The beam's hum, following the player who fires it until they stop. */
	private static final class BeamSound extends AbstractTickableSoundInstance {
		private final Player player;

		BeamSound(Player player) {
			super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, player.getRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.9F;
			this.pitch = 1.8F;
			this.x = player.getX();
			this.y = player.getY();
			this.z = player.getZ();
		}

		@Override
		public void tick() {
			if (this.player.isRemoved() || !RingBeam.isFiring(this.player)) {
				this.stop();
				return;
			}
			this.x = this.player.getX();
			this.y = this.player.getY() + 1.2;
			this.z = this.player.getZ();
		}
	}
}
