package xyz.ssander.rings.client;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import xyz.ssander.rings.Rings;

/**
 * How each costume looks. A costume's skin is at {@code textures/entity/costume/<name>.png} and its cape,
 * if it has one, at {@code <name>_cape.png}.
 */
public final class Costumes {
	private static final Map<Identifier, PlayerSkin.Patch> COSTUMES = Map.of(
			Rings.id("lantern"), costume("lantern", false, PlayerModelType.WIDE));

	private Costumes() {
	}

	public static PlayerSkin.@Nullable Patch get(Identifier costume) {
		return COSTUMES.get(costume);
	}

	private static PlayerSkin.Patch costume(String name, boolean cape, PlayerModelType model) {
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
