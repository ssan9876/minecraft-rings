"""Writes each ring colour's models, recipes, loot tables and names. Run from the repo root: python tools/gen_resources.py"""
import json
from pathlib import Path

RESOURCES = Path("src/main/resources")
ASSETS = RESOURCES / "assets/rings"
DATA = RESOURCES / "data/rings"

# name: (display name, ring core, lantern sides). Same names and order as RingColor.
COLORS = {
    "green": ("Green", "minecraft:diamond", "minecraft:emerald"),
    "blue": ("Blue", "minecraft:lapis_block", "minecraft:lapis_lazuli"),
    "red": ("Red", "minecraft:redstone_block", "minecraft:redstone"),
    "pink": ("Pink", "minecraft:amethyst_block", "minecraft:amethyst_shard"),
    "orange": ("Orange", "minecraft:copper_block", "minecraft:copper_ingot"),
    "white": ("White", "minecraft:quartz_block", "minecraft:quartz"),
    "black": ("Black", "minecraft:obsidian", "minecraft:obsidian"),
    "yellow": ("Yellow", "minecraft:gold_block", "minecraft:glowstone_dust"),
}


def ring_id(color):
    # Green keeps its 1.0 names
    return "hero_ring" if color == "green" else color + "_ring"


def lantern_id(color):
    return "power_lantern" if color == "green" else color + "_power_lantern"


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("wrote", path)


def main():
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))

    for color, (name, core, sides) in COLORS.items():
        ring = ring_id(color)
        lantern = lantern_id(color)

        write(ASSETS / f"items/{ring}.json", {"model": {"type": "minecraft:model", "model": f"rings:item/{ring}"}})
        write(ASSETS / f"models/item/{ring}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rings:item/{ring}"}})

        write(ASSETS / f"blockstates/{lantern}.json", {"variants": {"": {"model": f"rings:block/{lantern}"}}})
        write(ASSETS / f"models/block/{lantern}.json", {
            "parent": "rings:block/template_power_lantern",
            "textures": {"metal": f"rings:block/{lantern}_metal", "glass": f"rings:block/{lantern}_glass"},
        })
        write(ASSETS / f"items/{lantern}.json", {"model": {"type": "minecraft:model", "model": f"rings:block/{lantern}"}})

        write(DATA / f"recipe/{ring}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "key": {"C": core, "F": "minecraft:phantom_membrane", "G": "minecraft:gold_ingot"},
            "pattern": ["FCF", "G G", " G "],
            "result": {"id": f"rings:{ring}"},
        })
        write(DATA / f"recipe/{lantern}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"E": sides, "I": "minecraft:iron_ingot", "L": "minecraft:lantern"},
            "pattern": ["IEI", "ELE", "IEI"],
            "result": {"id": f"rings:{lantern}"},
        })
        write(DATA / f"loot_table/blocks/{lantern}.json", {
            "type": "minecraft:block",
            "pools": [{
                "condition": {"type": "minecraft:survives_explosion"},
                "entries": [{"type": "minecraft:item", "name": f"rings:{lantern}"}],
                "rolls": 1,
            }],
            "random_sequence": f"rings:blocks/{lantern}",
        })

        lang[f"item.rings.{ring}"] = f"{name} Power Ring"
        lang[f"block.rings.{lantern}"] = f"{name} Power Lantern"

    # Hard light, in every colour: see-through, so its texture is marked translucent
    write(ASSETS / "blockstates/hard_light.json",
          {"variants": {f"color={color}": {"model": f"rings:block/hard_light_{color}"} for color in COLORS}})
    for color in COLORS:
        write(ASSETS / f"models/block/hard_light_{color}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": {"force_translucent": True, "sprite": f"rings:block/hard_light_{color}"}},
        })

    # Construct tools: white sprites tinted with the ring's colour, which is the item's dye
    for tool in ("construct_sword", "construct_axe", "construct_pickaxe", "construct_shovel"):
        write(ASSETS / f"items/{tool}.json", {"model": {
            "type": "minecraft:model",
            "model": f"rings:item/{tool}",
            "tints": [{"type": "minecraft:dye", "default": -1}],
        }})
        write(ASSETS / f"models/item/{tool}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"rings:item/{tool}"}})

    write(RESOURCES / "data/minecraft/tags/block/mineable/pickaxe.json",
          {"values": [f"rings:{lantern_id(color)}" for color in COLORS]})
    write(lang_path, lang)


if __name__ == "__main__":
    main()
