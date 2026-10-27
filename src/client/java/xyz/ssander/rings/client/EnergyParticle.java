package xyz.ssander.rings.client;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import xyz.ssander.rings.RingParticles;

/**
 * A mote of a ring's energy: it glows at full brightness whatever the light, flares up as it appears, then shrinks
 * from a soft orb to a twinkling star and winks out. Spawned like vanilla's dust, so it drifts the same way.
 */
public class EnergyParticle extends SingleQuadParticle {
	/** Full brightness, block and sky light both. */
	private static final int FULL_BRIGHT = 0xF000F0;
	/** The first part of its life, as a fraction, it spends flaring up to full size. */
	private static final float FLARE = 0.15F;

	private final SpriteSet sprites;
	private final float spin;

	EnergyParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RingParticles.Energy options, SpriteSet sprites) {
		super(level, x, y, z, xd, yd, zd, sprites.first());
		this.sprites = sprites;
		this.friction = 0.96F;
		this.speedUpWhenYMotionIsBlocked = true;
		this.hasPhysics = false;
		this.xd *= 0.1;
		this.yd *= 0.1;
		this.zd *= 0.1;
		this.quadSize *= 0.8F * options.size();
		this.lifetime = Mth.clamp((int) (11.0 / (this.random.nextDouble() * 0.8 + 0.2) * Math.min(options.size(), 1.3F)), 4, 50);
		// A little lighter or darker than the energy's colour, so a cloud of them shimmers
		float shade = 0.85F + this.random.nextFloat() * 0.15F;
		int color = options.color();
		this.setColor(ARGB.redFloat(color) * shade, ARGB.greenFloat(color) * shade, ARGB.blueFloat(color) * shade);
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.spin = (this.random.nextFloat() - 0.5F) * 0.3F;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.oRoll = this.roll;
		this.roll += this.spin;
		this.setSpriteFromAge(this.sprites);
		float life = (float) this.age / this.lifetime;
		this.setAlpha(1.0F - life * life);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
		float scale = life < FLARE ? 0.6F + 0.5F * life / FLARE : 1.1F - 0.75F * (life - FLARE) / (1.0F - FLARE);
		return this.quadSize * scale;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(RingParticles.ENERGY, sprites ->
				(options, level, x, y, z, xd, yd, zd, random) -> new EnergyParticle(level, x, y, z, xd, yd, zd, options, sprites));
	}
}
