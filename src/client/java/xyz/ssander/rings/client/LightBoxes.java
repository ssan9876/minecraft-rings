package xyz.ssander.rings.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;

/** Boxes of glowing light, for things made of a ring's energy. */
public final class LightBoxes {
	private LightBoxes() {
	}

	/** A box {x0, y0, z0, x1, y1, z1} grown by {@code grow} on every side, each face wound to face outward. */
	public static void box(Matrix4f m, VertexConsumer c, float[] b, float grow, int rgb, float alpha) {
		float x0 = b[0] - grow, y0 = b[1] - grow, z0 = b[2] - grow;
		float x1 = b[3] + grow, y1 = b[4] + grow, z1 = b[5] + grow;
		float r = ARGB.red(rgb) / 255.0F, g = ARGB.green(rgb) / 255.0F, bl = ARGB.blue(rgb) / 255.0F;
		float[][] faces = {
				{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, // +Z
				{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, // -Z
				{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, // +X
				{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, // -X
				{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, // +Y
				{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, // -Y
		};
		for (float[] f : faces) {
			for (int i = 0; i < 12; i += 3) {
				c.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, alpha);
			}
		}
	}
}
