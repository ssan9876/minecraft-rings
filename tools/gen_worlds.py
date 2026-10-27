"""Writes space and the planets: their dimensions, terrain and skies. Run from the repo root: python tools/gen_worlds.py

The planets' terrain uses vanilla's overworld terrain shape with each planet's own blocks and no water, so it reads
vanilla's overworld noise settings out of the Minecraft jar Loom downloaded.
"""
import glob
import json
import os
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

DATA = Path("src/main/resources/data/rings")
PLANET_TEXTURES = Path("src/main/resources/assets/rings/textures/environment/planet")

# Planet blocks: (surface, under the surface, the rest of the ground)
PLANETS = {
    "moon": {
        "blocks": ("rings:moon_dust", "rings:moon_rock", "rings:moon_rock"),
        "sky": "#000000", "fog": "#16161c", "stars": 0.9, "ambient": 0.05, "clouds": False,
        "carvers": ["minecraft:cave", "minecraft:canyon"],
    },
    "mars": {
        "blocks": ("minecraft:red_sand", "minecraft:red_sandstone", "rings:mars_rock"),
        "sky": "#d99a66", "fog": "#c77a4e", "stars": 0.0, "ambient": 0.0, "clouds": False,
        "carvers": ["minecraft:cave", "minecraft:canyon"],
    },
    "oa": {
        "blocks": ("minecraft:green_concrete_powder", "minecraft:green_terracotta", "rings:oan_stone"),
        "sky": "#3e9a5e", "fog": "#2f7d4b", "stars": 0.3, "ambient": 0.1, "clouds": True,
        "carvers": ["minecraft:cave"],
    },
}

# Nowhere to sleep or set a spawn away from Earth (beds blow up, as in the End)
BED_RULE = {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True}


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("wrote", path)


def vanilla(path):
    home = Path(os.path.expanduser("~"))
    jars = glob.glob(str(home / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.3/*.jar"))
    if not jars:
        raise SystemExit("Couldn't find the Minecraft 26.3 jar: run ./gradlew build once first")
    with zipfile.ZipFile(jars[0]) as jar:
        return json.loads(jar.read(path))


def dimension_type(sky, fog, stars, ambient, clouds, min_y, height, fog_end):
    attributes = {
        "minecraft:gameplay/bed_rule": BED_RULE,
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:visual/sky_color": sky,
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/star_brightness": stars,
        "minecraft:visual/sky_light_factor": 1.0,
        "minecraft:visual/sun_angle": 35.0,
        "minecraft:visual/fog_start_distance": fog_end * 0.6,
        "minecraft:visual/fog_end_distance": fog_end,
        "minecraft:visual/sky_fog_end_distance": fog_end,
    }
    if not clouds:
        attributes["minecraft:visual/cloud_color"] = "#00000000"
    return {
        "ambient_light": ambient,
        "attributes": attributes,
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:the_end",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": height,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": height,
        "min_y": min_y,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "timelines": "#minecraft:in_end",
    }


# The steps features generate in, in vanilla's order (a biome lists its features for each)
STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
         "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]


def biome(carvers, features=None):
    features = features or {}
    return {
        "carvers": carvers,
        "downfall": 0.0,
        "effects": {"water_color": "#3f76e4"},
        # Each step's features, by name (see planet_features); space has none
        "features": [[f"rings:{name}" for name in features.get(step, [])] for step in STEPS],
        "has_precipitation": False,
        "temperature": 0.5,
    }


def main():
    # Space: an empty void (a flat world with no layers), black sky, every star out, no fog to hide the planets
    write(DATA / "dimension_type/space.json",
          dimension_type("#000000", "#000000", 1.0, 0.3, False, 0, 256, 4096.0))
    write(DATA / "worldgen/biome/space.json", biome([]))
    write(DATA / "dimension/space.json", {
        "type": "rings:space",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": "rings:space", "features": False, "lakes": False, "layers": [], "structure_overrides": []}},
    })

    overworld = vanilla("data/minecraft/worldgen/noise_settings/overworld.json")
    for name, planet in PLANETS.items():
        surface, under, ground = planet["blocks"]
        write(DATA / f"dimension_type/{name}.json", dimension_type(
            planet["sky"], planet["fog"], planet["stars"], planet["ambient"], planet["clouds"], -64, 384, 640.0))
        write(DATA / f"worldgen/biome/{name}.json", biome(planet["carvers"], planet_features(name, ground)))

        # The overworld's shape in the planet's own rock, with no water, aquifers, ore veins or mobs
        settings = dict(overworld)
        settings.pop("aquifers", None)
        settings.update({
            "default_block": ground,
            "default_fluid": "minecraft:air",
            "disable_mob_generation": True,
            "material_rule": f"rings:{name}",
            "spawn_target": [],
        })
        write(DATA / f"worldgen/noise_settings/{name}.json", settings)
        write(DATA / f"worldgen/material_rule/{name}.json", {"type": "minecraft:sequence", "sequence": [
            "minecraft:bedrock_floor",
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"}, "then_run": {
                "type": "minecraft:sequence", "sequence": [
                    {"type": "minecraft:condition", "if_true": "minecraft:on_floor",
                     "then_run": {"type": "minecraft:block", "result_state": surface}},
                    {"type": "minecraft:condition", "if_true": "minecraft:under_floor",
                     "then_run": {"type": "minecraft:block", "result_state": under}},
                ]}},
        ]})
        write(DATA / f"dimension/{name}.json", {
            "type": f"rings:{name}",
            "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:fixed", "biome": f"rings:{name}"},
                          "settings": f"rings:{name}"},
        })

    write(DATA / "damage_type/vacuum.json", {"message_id": "rings.vacuum", "exhaustion": 0.0, "scaling": "never"})


def uniform(low, high):
    return {"type": "minecraft:uniform", "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}


def ore(name, block, rock, size, count, low, high, air_exposure=0.0):
    """Veins of an ore (or any block) in the planet's rock, spread evenly between two heights."""
    write(DATA / f"worldgen/feature/{name}.json", {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": air_exposure, "size": size,
        "targets": [{"state": block, "target": {"predicate_type": "minecraft:block_match", "block": rock}}]})
    write(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"rings:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": uniform(low, high)}, {"type": "minecraft:biome"}]})
    return name


def on_surface(name, feature, rarity):
    """A feature on the ground, in about one chunk in {rarity}."""
    write(DATA / f"worldgen/feature/{name}.json", feature)
    write(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"rings:{name}", "placement": [
        {"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    return name


def in_caves(name, feature, count, low, high):
    """A feature underground, tried {count} times a chunk between two heights."""
    write(DATA / f"worldgen/feature/{name}.json", feature)
    write(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"rings:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": uniform(low, high)}, {"type": "minecraft:biome"}]})
    return name


def crystals(block, tries, spread):
    # The patch stands each crystal upright itself
    return {"type": "rings:crystal_patch", "crystal": block, "tries": tries, "spread": spread}


def planet_features(name, rock):
    """What's to be found on and in each planet, by generation step (see PlanetFeatures for the planets' own)."""
    if name == "moon":
        return {
            "local_modifications": [on_surface("moon_crater", {"type": "rings:crater"}, 3)],
            "underground_ores": [
                ore("moon_ore_iron", "rings:moon_iron_ore", rock, 9, 14, -60, 160),
                ore("moon_ore_moonstone", "rings:moonstone_ore", rock, 5, 6, -60, 50),
            ],
        }
    if name == "mars":
        write(DATA / "worldgen/feature/mars_geode.json", {
            "type": "minecraft:geode",
            "blocks": {
                "filling_provider": {"id": "minecraft:air"},
                "inner_layer_provider": {"id": "rings:mars_crystal_block"},
                "alternate_inner_layer_provider": {"id": "rings:mars_crystal_block"},
                "middle_layer_provider": {"id": "minecraft:red_sandstone"},
                "outer_layer_provider": {"id": "minecraft:smooth_basalt"},
                "inner_placements": ["rings:mars_crystal"],
                "cannot_replace": "#minecraft:features_cannot_replace",
                "invalid_blocks": "#minecraft:geode_invalid_blocks",
            },
            "crack": {"generate_crack_chance": 0.95},
            "invalid_blocks_threshold": 1,
            "layers": {},
            "outer_wall_distance": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6},
            "use_alternate_layer0_chance": 0.083,
        })
        write(DATA / "worldgen/placed_feature/mars_geode.json", {"feature": "rings:mars_geode", "placement": [
            {"type": "minecraft:rarity_filter", "chance": 12}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": uniform(-54, 40)}, {"type": "minecraft:biome"}]})
        return {
            "local_modifications": ["mars_geode"],
            "underground_ores": [
                ore("mars_ore_iron", "rings:mars_iron_ore", rock, 12, 20, -60, 180),
                ore("mars_ore_gold", "rings:mars_gold_ore", rock, 8, 6, -60, 40),
                # Ice, frozen deep in the rock: what water Mars has
                ore("mars_ice", "minecraft:packed_ice", rock, 24, 3, -40, 70),
                ore("mars_blue_ice", "minecraft:blue_ice", rock, 8, 2, -60, 20),
            ],
            "vegetal_decoration": [
                on_surface("mars_spire", {"type": "rings:spire"}, 2),
                in_caves("mars_cave_crystals", crystals("rings:mars_crystal", 10, 3), 6, -60, 60),
            ],
        }
    return {
        "underground_ores": [
            ore("oa_ore_emerald", "rings:oan_emerald_ore", rock, 4, 12, -60, 140),
            ore("oa_ore_will_crystal", "rings:will_crystal_ore", rock, 5, 7, -60, 60),
        ],
        "vegetal_decoration": [
            on_surface("oa_crystals", crystals("rings:oan_crystal", 8, 4), 2),
            in_caves("oa_cave_crystals", crystals("rings:oan_crystal", 10, 3), 8, -60, 80),
        ],
    }


# Planet maps: equirectangular textures for the spheres drawn in the sky. Noise is sampled on the sphere itself so
# the maps wrap round without a seam.
# The Earth seen from space fills the view below, so its map is finer.
WIDTH, HEIGHT = 256, 128


def sphere_points(width=WIDTH, height=HEIGHT):
    lon = (np.arange(width) + 0.5) / width * 2 * np.pi
    lat = (0.5 - (np.arange(height) + 0.5) / height) * np.pi
    lon, lat = np.meshgrid(lon, lat)
    return np.cos(lat) * np.cos(lon), np.sin(lat), np.cos(lat) * np.sin(lon), lat


def value_noise(x, y, z, seed, scale):
    """Smooth 3D value noise in about 0..1."""
    rng = np.random.default_rng(seed)
    size = int(scale * 2) + 4
    grid = rng.random((size, size, size))
    fx, fy, fz = (x + 1.0) * scale, (y + 1.0) * scale, (z + 1.0) * scale
    ix, iy, iz = np.floor(fx).astype(int), np.floor(fy).astype(int), np.floor(fz).astype(int)
    tx, ty, tz = [t * t * (3 - 2 * t) for t in (fx - ix, fy - iy, fz - iz)]
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                out = out + w * grid[(ix + dx) % size, (iy + dy) % size, (iz + dz) % size]
    return out


def fbm(x, y, z, seed, scale=2.0, octaves=5):
    total, amplitude, norm = 0.0, 1.0, 0.0
    for octave in range(octaves):
        total = total + amplitude * value_noise(x, y, z, seed + octave, scale * 2 ** octave)
        norm += amplitude
        amplitude *= 0.5
    return total / norm


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(a, float) * (1 - t) + np.array(b, float) * t


def save_map(name, rgb):
    PLANET_TEXTURES.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").save(PLANET_TEXTURES / f"{name}.png")
    print("wrote", PLANET_TEXTURES / f"{name}.png")


def planet_maps():
    x, y, z, lat = sphere_points(1024, 512)

    # Earth: blue oceans, green and brown land, ice at the poles, clouds over it all
    land = fbm(x, y, z, 1, 2.0, 7)
    dry = fbm(x, y, z, 7, 3.0, 6)
    rgb = mix((18, 52, 128), (34, 92, 170), fbm(x, y, z, 3, 4.0))
    ground = mix((58, 120, 52), (150, 126, 80), (dry - 0.4) * 3)
    rgb = np.where((land > 0.52)[..., None], ground, rgb)
    rgb = np.where((np.abs(lat) > 1.25)[..., None], np.array((235, 240, 245.0)), rgb)
    clouds = fbm(x, y, z, 11, 3.0, 6)
    rgb = mix(rgb, (250, 250, 252), (clouds - 0.55) * 3)
    save_map("earth", rgb)
    x, y, z, lat = sphere_points()

    # The Moon: grey dust with darker seas and scattered craters
    rgb = mix((92, 92, 96), (178, 178, 182), fbm(x, y, z, 21, 3.0))
    rgb = mix(rgb, (70, 70, 76), (fbm(x, y, z, 23, 1.5) - 0.58) * 4)
    rng = np.random.default_rng(29)
    for _ in range(90):
        cx, cy, cz = rng.normal(size=3)
        n = np.sqrt(cx * cx + cy * cy + cz * cz)
        size = rng.uniform(0.03, 0.14)
        d = np.arccos(np.clip((x * cx + y * cy + z * cz) / n, -1, 1))
        rgb = mix(rgb, (60, 60, 64), ((d < size * 0.8) * 0.6))
        rgb = mix(rgb, (205, 205, 208), ((d >= size * 0.8) & (d < size)) * 0.5)
    save_map("moon", rgb)

    # Mars: rust, darker basalt plains, white caps at the poles
    rgb = mix((150, 62, 32), (212, 108, 60), fbm(x, y, z, 31, 3.0))
    rgb = mix(rgb, (92, 40, 26), (fbm(x, y, z, 33, 2.0) - 0.56) * 4)
    rgb = np.where((np.abs(lat) > 1.38)[..., None], np.array((240, 232, 226.0)), rgb)
    save_map("mars", rgb)

    # Oa: green lands and darker seas, laced with the glowing lines of its cities
    rgb = mix((26, 92, 52), (64, 168, 92), fbm(x, y, z, 41, 3.0))
    rgb = mix(rgb, (14, 54, 34), (fbm(x, y, z, 43, 1.8) - 0.55) * 4)
    lines = np.abs(fbm(x, y, z, 47, 6.0, 3) - 0.5) < 0.012
    rgb = np.where(lines[..., None], np.array((170, 255, 190.0)), rgb)
    save_map("oa", rgb)

    # Korugar, a violet gas giant in Oa's sky: bands with a swirl in them
    bands = np.sin(y * 18 + fbm(x, y, z, 51, 2.0) * 6)
    rgb = mix((96, 44, 120), (176, 104, 190), bands * 0.5 + 0.5)
    rgb = mix(rgb, (230, 170, 210), (fbm(x, y, z, 53, 4.0) - 0.6) * 3)
    save_map("korugar", rgb)


if __name__ == "__main__":
    main()
    planet_maps()
