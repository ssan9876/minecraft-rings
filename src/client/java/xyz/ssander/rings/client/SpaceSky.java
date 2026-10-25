package xyz.ssander.rings.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.Rings;
import xyz.ssander.rings.Space;

/**
 * Draws the planets. In space: the Earth curving away below, and the Moon, Mars and Oa where they hang (fly into one
 * to land on it). On a planet: what's in its sky, like the Earth over the Moon. Each is a textured sphere, lit from
 * the sun's side, with a faint glow of atmosphere. Anything further than the fog is drawn closer and smaller in the
 * same proportion, so it looks the same size but isn't lost in the fog or cut off at the far edge of the view.
 */
public final class SpaceSky {
	private static final Vec3 SUN = new Vec3(0.55, 0.45, 0.7).normalize();
	/** The Earth seen from space: a sphere this big, its top at the Earth's edge of space. */
	private static final double EARTH_RADIUS = 1200.0;
	/** How finely spheres are divided: the Earth below, which fills half the view, much more finely. */
	private static final int LATITUDES = 24;
	private static final int EARTH_LATITUDES = 96;

	/**
	 * A sphere to draw: its centre relative to the camera, its size, its map, the colour of its air (0 for none), and
	 * how it's turned: tipped over by {@code tilt} (radians) and spun round by {@code spin} (radians).
	 */
	private record Body(Vec3 offset, double radius, Identifier texture, int glow, float tilt, float spin, int latitudes) {
		Body(Vec3 offset, double radius, Identifier texture, int glow) {
			this(offset, radius, texture, glow, 0.0F, 0.0F, LATITUDES);
		}
	}

	private SpaceSky() {
	}

	private static Identifier map(String name) {
		return Rings.id("textures/environment/planet/" + name + ".png");
	}

	public static void render(LevelRenderContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		List<Body> bodies = bodies(level.dimension(), camera.pos);
		if (bodies.isEmpty()) {
			return;
		}

		// Everything is drawn inside the fog (which would paint it solid fog colour) and the far edge of the view
		double reach = Math.min(camera.depthFar, Math.min(camera.fogData.renderDistanceStart, camera.fogData.environmentalStart)) * 0.8;
		PoseStack poseStack = context.poseStack();
		for (Body body : bodies) {
			double distance = body.offset.length();
			double scale = Math.min(1.0, reach / (distance + body.radius));
			Vec3 centre = body.offset.scale(scale);
			float radius = (float) (body.radius * scale);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entitySolid(body.texture),
					(pose, consumer) -> sphere(pose, consumer, centre, radius, body, true));
			if (body.glow != 0) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(),
						(pose, consumer) -> sphere(pose, consumer, centre, radius * 1.04F, body, false));
			}
		}
	}

	/** What there is to see from the camera in this dimension. */
	private static List<Body> bodies(ResourceKey<Level> dimension, Vec3 camera) {
		List<Body> bodies = new ArrayList<>();
		if (dimension == Space.SPACE) {
			// Tipped over so the part below is the middle of the map, not the ice at the pole, and turning slowly
			float spin = (float) ((Minecraft.getInstance().level.getGameTime() % 72000L) / 72000.0 * Mth.TWO_PI);
			bodies.add(new Body(new Vec3(0.0, Space.EARTH_Y - EARTH_RADIUS - camera.y, 0.0), EARTH_RADIUS, map("earth"), 0x5A8CFF,
					Mth.HALF_PI, spin, EARTH_LATITUDES));
			for (Space.Planet planet : Space.Planet.values()) {
				bodies.add(new Body(planet.centre().subtract(camera), planet.radius(), map(planet.id()), glow(planet)));
			}
		} else if (dimension == Space.Planet.MOON.dimension()) {
			bodies.add(sky(new Vec3(0.35, 0.55, -0.75), 7000.0, 520.0, "earth", 0x5A8CFF));
			bodies.add(sky(new Vec3(-0.6, 0.3, 0.55), 9000.0, 130.0, "mars", 0xD07040));
		} else if (dimension == Space.Planet.MARS.dimension()) {
			bodies.add(sky(new Vec3(0.2, 0.45, 0.85), 9000.0, 220.0, "earth", 0x5A8CFF));
		} else if (dimension == Space.Planet.OA.dimension()) {
			bodies.add(sky(new Vec3(-0.45, 0.5, -0.6), 7000.0, 1100.0, "korugar", 0xB070D0));
		}
		return bodies;
	}

	/** Something in a planet's sky: always the same way off, however far you walk. */
	private static Body sky(Vec3 direction, double distance, double radius, String map, int glow) {
		return new Body(direction.normalize().scale(distance), radius, map(map), glow);
	}

	private static int glow(Space.Planet planet) {
		return switch (planet) {
			case MOON -> 0;
			case MARS -> 0xD07040;
			case OA -> 0x50E070;
		};
	}

	/**
	 * A sphere, its faces wound to face outward. Textured, it's shaded from the sun's side; as glow (an additive
	 * colour), it fades out toward the middle so only a rim of air shows.
	 */
	private static void sphere(PoseStack.Pose pose, VertexConsumer consumer, Vec3 centre, float radius, Body body, boolean textured) {
		for (int lat = 0; lat < body.latitudes; lat++) {
			for (int lon = 0; lon < body.latitudes * 2; lon++) {
				// Corners going round counter-clockwise seen from outside: east, then down, then back west
				vertex(pose, consumer, centre, radius, lat, lon, body, textured);
				vertex(pose, consumer, centre, radius, lat, lon + 1, body, textured);
				vertex(pose, consumer, centre, radius, lat + 1, lon + 1, body, textured);
				vertex(pose, consumer, centre, radius, lat + 1, lon, body, textured);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 centre, float radius, int lat, int lon,
			Body body, boolean textured) {
		float v = (float) lat / body.latitudes;
		float u = (float) lon / (body.latitudes * 2);
		float latitude = (0.5F - v) * Mth.PI;
		float longitude = u * Mth.TWO_PI + body.spin;
		float sx = Mth.cos(latitude) * Mth.cos(longitude);
		float sy = Mth.sin(latitude);
		float sz = Mth.cos(latitude) * Mth.sin(longitude);
		// Tipped over about the x axis
		float nx = sx;
		float ny = sy * Mth.cos(body.tilt) - sz * Mth.sin(body.tilt);
		float nz = sy * Mth.sin(body.tilt) + sz * Mth.cos(body.tilt);
		float x = (float) centre.x + nx * radius;
		float y = (float) centre.y + ny * radius;
		float z = (float) centre.z + nz * radius;

		if (textured) {
			// The sun's side bright and the far side dim but not black. The normal points straight up for every vertex so
			// the entity shader's own lighting is the same all over and this shading is what shows.
			float light = 0.4F + 0.6F * Math.max(0.0F, (float) (nx * SUN.x + ny * SUN.y + nz * SUN.z));
			consumer.addVertex(pose, x, y, z).setColor(light, light, light, 1.0F).setUv(u, v)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
		} else {
			// Brightest at the rim as seen from the camera, which sits at the origin
			Vec3 toCamera = new Vec3(-x, -y, -z).normalize();
			float facing = Math.abs((float) (nx * toCamera.x + ny * toCamera.y + nz * toCamera.z));
			float alpha = 0.35F * (1.0F - facing) * (1.0F - facing);
			int glow = body.glow;
			consumer.addVertex(pose, x, y, z).setColor(ARGB.red(glow) / 255.0F, ARGB.green(glow) / 255.0F, ARGB.blue(glow) / 255.0F, alpha);
		}
	}
}
