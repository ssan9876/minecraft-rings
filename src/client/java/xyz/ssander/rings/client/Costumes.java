package xyz.ssander.rings.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.Rings;

/**
 * How each costume looks. A costume's skin is at {@code textures/entity/costume/<name>.png} and its cape,
 * if it has one, at {@code <name>_cape.png}. Each ring colour has its own suit, outlined in its energy.
 */
public final class Costumes {
	public record Costume(PlayerSkin.Patch skin, RingColor color) {
	}

	private static final Map<Identifier, Costume> COSTUMES = new HashMap<>();

	static {
		for (RingColor color : RingColor.values()) {
			COSTUMES.put(color.costume(), new Costume(skin(color.costume().getPath(), false, PlayerModelType.WIDE), color));
		}
	}

	private Costumes() {
	}

	public static @Nullable Costume get(@Nullable Identifier costume) {
		return costume == null ? null : COSTUMES.get(costume);
	}

	private static PlayerSkin.Patch skin(String name, boolean cape, PlayerModelType model) {
		return PlayerSkin.Patch.create(
				Optional.of(texture(name)),
				cape ? Optional.of(texture(name + "_cape")) : Optional.empty(),
				Optional.empty(),
				Optional.of(model));
	}

	private static ClientAsset.ResourceTexture texture(String name) {
		return new ClientAsset.ResourceTexture(Rings.id("entity/costume/" + name));
	}
}
