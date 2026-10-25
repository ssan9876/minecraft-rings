package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.RingColor;

/**
 * A sphere of a ring's force field: nearly clear where you look straight through it and bright round its rim, with
 * bands of shimmer drifting over it. Seen from inside, it doesn't show (its faces all turn away).
 */
public final class EnergySphere {
	private static final int LATITUDES = 14;

	private EnergySphere() {
	}

	/**
	 * Submits a sphere centred at {@code centre}, with the camera at {@code camera}, both relative to the pose's
	 * origin. {@code time} drives the shimmer, and {@code strength} (0 to 1) fades the whole thing.
	 */
	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 centre, float radius, Vec3 camera, RingColor color,
			float time, float strength) {
		// Additive light can't make black, so the black ring's field is a dark film instead
		boolean dark = color == RingColor.BLACK;
		collector.submitCustomGeometry(poseStack, dark ? RenderTypes.debugQuads() : RenderTypes.lightning(), (pose, consumer) -> {
			for (int lat = 0; lat < LATITUDES; lat++) {
				for (int lon = 0; lon < LATITUDES * 2; lon++) {
					vertex(pose, consumer, centre, radius, camera, lat, lon, color, time, strength, dark);
					vertex(pose, consumer, centre, radius, camera, lat, lon + 1, color, time, strength, dark);
					vertex(pose, consumer, centre, radius, camera, lat + 1, lon + 1, color, time, strength, dark);
					vertex(pose, consumer, centre, radius, camera, lat + 1, lon, color, time, strength, dark);
				}
			}
		});
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 centre, float radius, Vec3 camera, int lat, int lon,
			RingColor color, float time, float strength, boolean dark) {
		float latitude = (0.5F - (float) lat / LATITUDES) * Mth.PI;
		float longitude = (float) lon / (LATITUDES * 2) * Mth.TWO_PI;
		float nx = Mth.cos(latitude) * Mth.cos(longitude);
		float ny = Mth.sin(latitude);
		float nz = Mth.cos(latitude) * Mth.sin(longitude);
		float x = (float) centre.x + nx * radius;
		float y = (float) centre.y + ny * radius;
		float z = (float) centre.z + nz * radius;

		Vec3 toCamera = camera.subtract(x, y, z);
		double distance = toCamera.length();
		float facing = distance < 1.0E-4 ? 1.0F : Math.abs((float) ((nx * toCamera.x + ny * toCamera.y + nz * toCamera.z) / distance));
		float rim = (1.0F - facing) * (1.0F - facing);
		float shimmer = Math.max(0.0F, Mth.sin(ny * 9.0F - time * 0.15F) * Mth.sin(longitude * 3.0F + time * 0.05F));
		float alpha = strength * (0.04F + 0.55F * rim + 0.1F * shimmer);
		int rgb = dark ? 0x101016 : ARGB.srgbLerp(0.3F * rim, color.energy(), 0xFFFFFF);
		consumer.addVertex(pose, x, y, z).setColor(ARGB.red(rgb) / 255.0F, ARGB.green(rgb) / 255.0F, ARGB.blue(rgb) / 255.0F,
				dark ? Math.min(1.0F, alpha * 1.6F) : alpha);
	}
}
