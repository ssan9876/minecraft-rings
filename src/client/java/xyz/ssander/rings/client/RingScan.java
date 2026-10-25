package xyz.ssander.rings.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.RingColor;

/**
 * The ring's scan, as the wearer sees it. For a while after scanning, creatures round about are outlined through
 * walls (hostile ones red, players white, the rest green), ores in the rock nearby glow through it in their own
 * colours, and looking at a creature reads out its health and armor. Everyone near by sees the pulse go out.
 */
public final class RingScan implements HudElement {
	/** How long a scan lasts, in ticks, and how far it reaches for creatures and for ores. */
	private static final int DURATION = 15 * 20;
	private static final double CREATURE_RANGE = 48.0;
	private static final int ORE_RADIUS = 16;
	/** The nearest ores are drawn, up to this many. */
	private static final int MAX_ORES = 400;

	/** Additive light drawn over everything, walls and all, so ores show through the rock. */
	private static final RenderPipeline XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
			.withLocation(xyz.ssander.rings.Rings.id("pipeline/scan_xray"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(Optional.empty())
			.build();
	private static final RenderType XRAY = RenderType.create("rings_scan_xray", RenderSetup.builder(XRAY_PIPELINE).sortOnUpload().createRenderSetup());

	private record Ore(BlockPos pos, int color) {
	}

	private static long scanUntil = Long.MIN_VALUE;
	private static long scanStart;
	private static List<Ore> ores = List.of();

	/** A scan went out from the player. For the local player's own scan, it starts showing what's round about. */
	public static void start(Player scanner, RingColor color) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		// The pulse: a ring of the ring's energy racing outward along the ground, and one up round the scanner
		for (int i = 0; i < 48; i++) {
			float angle = Mth.TWO_PI * i / 48;
			level.addParticle(color.dust(0.5F, 1.2F), scanner.getX(), scanner.getY() + 0.2, scanner.getZ(),
					Mth.cos(angle) * 0.9, 0.0, Mth.sin(angle) * 0.9);
			level.addParticle(color.dust(0.0F, 0.8F), scanner.getX() + Mth.cos(angle) * 0.6, scanner.getY() + 1.0, scanner.getZ() + Mth.sin(angle) * 0.6,
					Mth.cos(angle) * 0.3, 0.15, Mth.sin(angle) * 0.3);
		}
		if (scanner == minecraft.player) {
			scanStart = level.getGameTime();
			scanUntil = scanStart + DURATION;
			findOres(level, scanner.blockPosition());
		}
	}

	/** How many ores the scan is showing. */
	public static int oreCount() {
		return ores.size();
	}

	public static boolean isScanning() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.getGameTime() < scanUntil;
	}

	/** Keeps the ores up to date as the wearer moves. Runs every client tick. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || !isScanning()) {
			ores = List.of();
			return;
		}
		if ((minecraft.level.getGameTime() - scanStart) % 20 == 0) {
			findOres(minecraft.level, minecraft.player.blockPosition());
		}
	}

	private static void findOres(ClientLevel level, BlockPos centre) {
		List<Ore> found = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -ORE_RADIUS; x <= ORE_RADIUS; x++) {
			for (int y = -ORE_RADIUS; y <= ORE_RADIUS; y++) {
				for (int z = -ORE_RADIUS; z <= ORE_RADIUS; z++) {
					pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
					int color = oreColor(level.getBlockState(pos));
					if (color != 0) {
						found.add(new Ore(pos.immutable(), color));
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(ore -> ore.pos().distSqr(centre)));
		ores = found.size() > MAX_ORES ? List.copyOf(found.subList(0, MAX_ORES)) : found;
	}

	/** The colour an ore glows in the scan, or 0 for anything that isn't one. */
	private static int oreColor(BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return 0xB0603A;
		}
		if (!state.is(BlockTags.ORES)) {
			return 0;
		}
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (name.contains("diamond")) {
			return 0x50FFF0;
		} else if (name.contains("emerald")) {
			return 0x30FF60;
		} else if (name.contains("gold")) {
			return 0xFFD030;
		} else if (name.contains("iron")) {
			return 0xE8B898;
		} else if (name.contains("copper")) {
			return 0xFF8040;
		} else if (name.contains("redstone")) {
			return 0xFF2020;
		} else if (name.contains("lapis")) {
			return 0x3050FF;
		} else if (name.contains("coal")) {
			return 0x707070;
		} else if (name.contains("quartz")) {
			return 0xF0F0F0;
		}
		return 0xC0C0FF;
	}

	/**
	 * The outline colour a creature shows during the local player's scan, or 0 for none: hostile red, players
	 * white, anything else green.
	 */
	public static int outline(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isScanning() || !(entity instanceof LivingEntity) || entity == minecraft.player || minecraft.player == null
				|| entity.distanceTo(minecraft.player) > CREATURE_RANGE) {
			return 0;
		}
		return entity instanceof Enemy ? 0xFF4040 : entity instanceof Player ? 0xFFFFFF : 0x60FF70;
	}

	/** Draws the glowing ores. Runs as the level is drawn. */
	public static void render(LevelRenderContext context) {
		List<Ore> shown = ores;
		if (shown.isEmpty() || !isScanning()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float age = minecraft.level.getGameTime() - scanStart + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// Fades in over the first second, pulses gently, and fades over the last two
		float strength = Mth.clamp(age / 20.0F, 0.0F, 1.0F) * Mth.clamp((DURATION - age) / 40.0F, 0.0F, 1.0F) * (0.85F + 0.15F * Mth.sin(age * 0.25F));
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, XRAY, (pose, consumer) -> {
			Matrix4f matrix = pose.pose();
			for (Ore ore : shown) {
				float x = (float) (ore.pos().getX() - camera.x);
				float y = (float) (ore.pos().getY() - camera.y);
				float z = (float) (ore.pos().getZ() - camera.z);
				LightBoxes.box(matrix, consumer, new float[] {x + 0.2F, y + 0.2F, z + 0.2F, x + 0.8F, y + 0.8F, z + 0.8F}, 0.0F, ore.color(), 0.45F * strength);
				LightBoxes.box(matrix, consumer, new float[] {x, y, z, x + 1.0F, y + 1.0F, z + 1.0F}, 0.0F, ore.color(), 0.12F * strength);
			}
		});
	}

	/** Reads out the creature the wearer looks at while scanning: its name, health and armor. */
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || !isScanning() || minecraft.gui.screen() != null) {
			return;
		}
		RingBeam.Hit hit = RingBeam.trace(player, deltaTracker.getGameTimeDeltaPartialTick(false));
		if (!(hit.entity() instanceof LivingEntity target)) {
			return;
		}

		Font font = minecraft.font;
		Costumes.Costume costume = Costumes.get(player.getAttached(RingAttachments.COSTUME));
		int accent = ARGB.opaque(costume != null ? costume.color().ui() : RingColor.GREEN.ui());
		List<Component> lines = new ArrayList<>();
		lines.add(target.getDisplayName());
		lines.add(Component.translatable("hud.rings.scan_health", String.format("%.1f", target.getHealth()), String.format("%.0f", target.getMaxHealth())));
		if (target.getArmorValue() > 0) {
			lines.add(Component.translatable("hud.rings.scan_armor", target.getArmorValue()));
		}
		RingColor ring = target.getAttached(RingAttachments.WORN_RING);
		if (ring != null) {
			lines.add(Component.translatable("hud.rings.scan_ring", Component.translatable("item.rings." + ring.ringId())));
		}
		lines.add(Component.translatable("hud.rings.scan_distance", String.format("%.0f", player.distanceTo(target))));

		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int x = graphics.guiWidth() / 2 + 14;
		int y = graphics.guiHeight() / 2 + 8;
		graphics.fill(x - 3, y - 3, x + width + 3, y + lines.size() * 10 + 1, 0x90000000);
		graphics.fill(x - 3, y - 3, x - 2, y + lines.size() * 10 + 1, accent);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x, y + i * 10, i == 0 ? accent : 0xFFE0E0E0, true);
		}
	}
}
