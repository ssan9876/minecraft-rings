package xyz.ssander.rings.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.Mastery;
import xyz.ssander.rings.PlanetBlocks;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.Skill;
import xyz.ssander.rings.network.LearnSkillPayload;

/**
 * The ring's skill tree (the skills key, K): the player's level, their progress to the next and their unspent points
 * along the top, then the five branches, each skill a box joined to the one it grows out of. Learned skills glow in
 * the ring's colour, ones that can be learned now pulse, and the rest are dimmed; hover one to read what it does and
 * what it needs, and click to learn it.
 */
public class SkillTreeScreen extends Screen {
	private static final int CELL_WIDTH = 36;
	private static final int CELL_HEIGHT = 42;
	private static final int NODE = 22;
	private static final int HEADER = 50;
	private static final int FOOTER = 26;

	private final Map<Skill, ItemStack> icons = new EnumMap<>(Skill.class);
	private int left;
	private int top;

	public SkillTreeScreen() {
		super(Component.translatable("screen.rings.skills"));
	}

	@Override
	protected void init() {
		this.left = (this.width - Skill.COLUMNS * CELL_WIDTH) / 2;
		this.top = Math.max(4, (this.height - HEADER - Skill.ROWS * CELL_HEIGHT - FOOTER) / 2);
		RingColor color = this.color();
		for (Skill skill : Skill.values()) {
			this.icons.put(skill, switch (skill) {
				case SOARING -> new ItemStack(Items.FEATHER);
				case SUPER_FLIGHT -> new ItemStack(Items.FIREWORK_ROCKET);
				case SONIC -> new ItemStack(Items.WIND_CHARGE);
				case SPACEFARER -> new ItemStack(PlanetBlocks.MOONSTONE);
				case FOCUSED_BEAM -> new ItemStack(Items.BLAZE_ROD);
				case LANCE -> new ItemStack(Items.TRIDENT);
				case ANNIHILATOR -> new ItemStack(Items.FIRE_CHARGE);
				case SPECTRUM -> new ItemStack(Rings.ring(color));
				case SPECTRUM_MASTERY -> new ItemStack(Items.BEACON);
				case SHAPER -> new ItemStack(Items.IRON_BARS);
				case ARMORY -> new ItemStack(Items.DIAMOND_SWORD);
				case LIVING_LIGHT -> new ItemStack(Items.BONE);
				case ENDURING -> new ItemStack(Items.CLOCK);
				case STARSHIP -> new ItemStack(Items.ELYTRA);
				case MATTER -> new ItemStack(Items.NETHER_STAR);
				case HARD_LIGHT_SKIN -> new ItemStack(Items.IRON_CHESTPLATE);
				case GUARDIAN_ANGEL -> new ItemStack(Items.TOTEM_OF_UNDYING);
				case FORTRESS -> new ItemStack(Items.NETHERITE_CHESTPLATE);
				case FORCE_FIELD -> new ItemStack(Items.SHIELD);
				case BUBBLES -> new ItemStack(Items.HEART_OF_THE_SEA);
				case AEGIS -> new ItemStack(Items.TURTLE_HELMET);
				case SCAN -> new ItemStack(Items.SPYGLASS);
				case DEEP_SCAN -> new ItemStack(Items.RECOVERY_COMPASS);
				case EFFICIENCY -> new ItemStack(Items.REDSTONE);
				case WILLPOWER -> new ItemStack(PlanetBlocks.WILL_SHARD);
				case LANTERN_BOND -> new ItemStack(Rings.lantern(color));
			});
		}
	}

	private RingColor color() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && RingAttachments.getRing(minecraft.player).getItem() instanceof RingItem ring ? ring.color() : RingColor.GREEN;
	}

	private Mastery.Data data() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null ? Mastery.of(minecraft.player) : Mastery.Data.NEW;
	}

	private int nodeX(Skill skill) {
		return this.left + skill.x() * CELL_WIDTH + CELL_WIDTH / 2;
	}

	private int nodeY(Skill skill) {
		return this.top + HEADER + skill.y() * CELL_HEIGHT + NODE / 2;
	}

	private @Nullable Skill at(double mouseX, double mouseY) {
		for (Skill skill : Skill.values()) {
			if (Math.abs(mouseX - this.nodeX(skill)) <= NODE / 2.0 && Math.abs(mouseY - this.nodeY(skill)) <= NODE / 2.0) {
				return skill;
			}
		}
		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Mastery.Data data = this.data();
		int energy = ARGB.opaque(this.color().ui());
		int bright = ARGB.srgbLerp(0.5F, energy, 0xFFFFFFFF);
		int dim = ARGB.scaleRGB(energy, 0.45F);
		int width = Skill.COLUMNS * CELL_WIDTH;
		int height = HEADER + Skill.ROWS * CELL_HEIGHT + FOOTER;

		// The panel
		graphics.fill(this.left - 6, this.top - 4, this.left + width + 6, this.top + height, 0xD0080810);
		graphics.outline(this.left - 6, this.top - 4, width + 12, height + 4, dim);

		// Level, progress and points
		int level = data.level();
		graphics.text(this.font, this.title, this.left, this.top + 2, bright, true);
		Component levelText = Component.translatable("screen.rings.skills_level", level);
		graphics.text(this.font, levelText, this.left + width - this.font.width(levelText), this.top + 2, bright, true);
		int barY = this.top + 14;
		graphics.fill(this.left, barY, this.left + width, barY + 5, 0xFF000000);
		graphics.fill(this.left + 1, barY + 1, this.left + width - 1, barY + 4, 0xFF282828);
		float progress = level >= Mastery.MAX_LEVEL ? 1.0F
				: (float) (data.xp() - Mastery.xpFor(level)) / (Mastery.xpFor(level + 1) - Mastery.xpFor(level));
		graphics.fill(this.left + 1, barY + 1, this.left + 1 + Math.round((width - 2) * Mth.clamp(progress, 0.0F, 1.0F)), barY + 4, energy);
		Component xpText = level >= Mastery.MAX_LEVEL ? Component.translatable("screen.rings.skills_max")
				: Component.translatable("screen.rings.skills_xp", data.xp() - Mastery.xpFor(level), Mastery.xpFor(level + 1) - Mastery.xpFor(level));
		graphics.text(this.font, xpText, this.left, barY + 8, 0xFFB0B0B0, false);
		Component points = Component.translatable("screen.rings.skills_points", data.points());
		graphics.text(this.font, points, this.left + width - this.font.width(points), barY + 8, data.points() > 0 ? bright : 0xFFB0B0B0, false);

		// Branch names, over their columns
		for (Skill.Branch branch : Skill.Branch.values()) {
			int x0 = this.left + branch.firstColumn() * CELL_WIDTH + 2;
			int x1 = this.left + (branch.lastColumn() + 1) * CELL_WIDTH - 2;
			Component name = Component.translatable(branch.translationKey());
			int y = this.top + HEADER - 13;
			graphics.centeredText(this.font, name, (x0 + x1) / 2, y, 0xFFD8D8D8);
			graphics.horizontalLine(x0, x1, y + 9, dim);
		}

		// Lines from each skill to the ones that grow out of it, lit along what's learned
		for (Skill skill : Skill.values()) {
			Skill parent = skill.parent();
			if (parent == null) {
				continue;
			}
			int color = data.has(skill) ? energy : data.has(parent) ? dim : 0xFF3A3A3A;
			int px = this.nodeX(parent);
			int py = this.nodeY(parent) + NODE / 2;
			int cx = this.nodeX(skill);
			int cy = this.nodeY(skill) - NODE / 2;
			int mid = (py + cy) / 2;
			graphics.fill(px - 1, py, px + 1, mid + 1, color);
			graphics.fill(Math.min(px, cx) - 1, mid - 1, Math.max(px, cx) + 1, mid + 1, color);
			graphics.fill(cx - 1, mid, cx + 1, cy, color);
		}

		// The skills
		float pulse = 0.5F + 0.5F * Mth.sin((Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount + partialTick : 0.0F) * 0.25F);
		Skill hovered = this.at(mouseX, mouseY);
		for (Skill skill : Skill.values()) {
			int x = this.nodeX(skill) - NODE / 2;
			int y = this.nodeY(skill) - NODE / 2;
			boolean learned = data.has(skill);
			boolean ready = !learned && data.blocker(skill) == null;
			int border = learned ? bright : ready ? ARGB.srgbLerp(pulse, dim, bright) : 0xFF4A4A4A;
			int fill = learned ? ARGB.color(230, ARGB.scaleRGB(energy, 0.35F)) : 0xE0101014;
			if (skill == hovered) {
				border = 0xFFFFFFFF;
			}
			graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
			graphics.fill(x, y, x + NODE, y + NODE, fill);
			graphics.item(this.icons.get(skill), x + 3, y + 3);
			if (!learned && !ready) {
				// Dimmed, with a small lock
				graphics.fill(x, y, x + NODE, y + NODE, 0x90000000);
				lock(graphics, x + NODE - 7, y + NODE - 8);
			}
			if (skill.cost() > 1 && !learned) {
				graphics.text(this.font, String.valueOf(skill.cost()), x + 1, y + 1, 0xFFFFD060, true);
			}
		}

		// What everyone starts with
		Component base = Component.translatable("screen.rings.skills_base");
		graphics.centeredText(this.font, base, this.width / 2, this.top + height - 14, 0xFF909090);

		if (hovered != null) {
			graphics.setTooltipForNextFrame(this.tooltip(hovered, data), mouseX, mouseY);
		}
	}

	/** A padlock, 6 wide and 7 tall. */
	private static void lock(GuiGraphicsExtractor graphics, int x, int y) {
		int metal = 0xFFC8C8C8;
		graphics.outline(x + 1, y, 4, 4, metal);
		graphics.fill(x, y + 3, x + 6, y + 7, metal);
		graphics.fill(x + 2, y + 4, x + 4, y + 6, 0xFF404040);
	}

	private List<FormattedCharSequence> tooltip(Skill skill, Mastery.Data data) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.translatable(skill.translationKey()).withColor(this.color().ui()).getVisualOrderText());
		lines.addAll(this.font.split(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY), 200));
		lines.add(Component.translatable("screen.rings.skills_cost", skill.cost(), skill.minLevel()).withStyle(ChatFormatting.DARK_GRAY)
				.getVisualOrderText());
		Component blocker = data.blocker(skill);
		if (data.has(skill)) {
			lines.add(Component.translatable("skill.rings.learned").withStyle(ChatFormatting.GREEN).getVisualOrderText());
		} else if (blocker != null) {
			lines.add(blocker.copy().withStyle(ChatFormatting.RED).getVisualOrderText());
		} else {
			lines.add(Component.translatable("screen.rings.skills_click").withStyle(ChatFormatting.YELLOW).getVisualOrderText());
		}
		return lines;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Skill skill = this.at(event.x(), event.y());
		if (skill != null) {
			Mastery.Data data = this.data();
			if (!data.has(skill) && data.blocker(skill) == null) {
				ClientPlayNetworking.send(new LearnSkillPayload(skill));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (RingsClient.skillsKey.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
