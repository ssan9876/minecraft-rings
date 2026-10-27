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

    # The construct shield: a plate of see-through light with a handle behind, laid out like vanilla's shield (whose
    # model is drawn in code) so vanilla's display settings for it fit, and raised while blocking like it
    def shield_face(texture, uv):
        return {"texture": texture, "uv": uv, "tintindex": 0}

    shield_elements = [
        {"from": [-6, -8, 1], "to": [6, 8, 2], "faces": {
            "south": shield_face("#front", [2, 0, 14, 16]), "north": shield_face("#back", [14, 0, 2, 16]),
            "east": shield_face("#back", [2, 0, 3, 16]), "west": shield_face("#back", [13, 0, 14, 16]),
            "up": shield_face("#back", [2, 0, 14, 1]), "down": shield_face("#back", [6, 15, 10, 16])}},
        {"from": [-1, -3, -5], "to": [1, 3, 1],
         "faces": {side: shield_face("#back", [2, 1, 4, 7]) for side in ("north", "south", "east", "west", "up", "down")}},
    ]
    shield_textures = {
        "front": {"sprite": "rings:item/construct_shield", "force_translucent": True},
        "back": {"sprite": "rings:item/construct_shield_back", "force_translucent": True},
        "particle": "rings:item/construct_shield",
    }
    write(ASSETS / "models/item/construct_shield.json", {"gui_light": "front", "textures": shield_textures, "elements": shield_elements, "display": {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
        "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 1.75, -10], "scale": [1.25, 1.25, 1.25]},
        "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
        "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
        "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
        "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
    }})
    write(ASSETS / "models/item/construct_shield_blocking.json", {"parent": "rings:item/construct_shield", "display": {
        "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
        "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
        "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 3.25, -11], "scale": [1.25, 1.25, 1.25]},
        "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
        "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    }})
    shield_tint = [{"type": "minecraft:dye", "default": -1}]
    write(ASSETS / "items/construct_shield.json", {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": "rings:item/construct_shield", "tints": shield_tint},
        "on_true": {"type": "minecraft:model", "model": "rings:item/construct_shield_blocking", "tints": shield_tint},
    }})
    lang["item.rings.construct_shield"] = "Construct Shield"
    lang["construct.rings.shield"] = "Shield"

    # The ring's energy motes (frames drawn by gen_textures.py)
    write(ASSETS / "particles/energy.json", {"textures": [f"rings:energy_{frame}" for frame in range(6)]})

    # The Central Power Battery's core: its own block (it never drops, so it has no loot table)
    write(ASSETS / "blockstates/power_battery.json", {"variants": {"": {"model": "rings:block/power_battery"}}})
    write(ASSETS / "models/block/power_battery.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "rings:block/power_battery"}})
    write(ASSETS / "items/power_battery.json", {"model": {"type": "minecraft:model", "model": "rings:block/power_battery"}})

    # What the creatures of space drop
    def drops(name, *pools):
        write(DATA / f"loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": list(pools),
                                                         "random_sequence": f"rings:entities/{name}"})

    def pool(item, low, high):
        return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]}]}

    drops("manhunter", pool("minecraft:iron_ingot", 1, 3), pool("minecraft:redstone", 0, 3))
    drops("parallax", pool("rings:yellow_power_lantern", 1, 1), pool("minecraft:diamond", 3, 6), pool("minecraft:gold_block", 2, 4))
    drops("nekron", pool("rings:white_ring", 1, 1), pool("rings:white_power_lantern", 1, 1), pool("minecraft:diamond", 3, 6))

    # The planets' blocks (see PlanetBlocks)
    planet_blocks(lang)
    write(lang_path, lang)


# What each planet block drops: None for itself, else (item, least, most, with fortune). Silk touch always gives the
# block itself back.
PLANET_BLOCKS = {
    "moon_dust": ("Moon Dust", None, "shovel", None),
    "moon_rock": ("Moon Rock", None, "pickaxe", None),
    "moon_rock_bricks": ("Moon Rock Bricks", None, "pickaxe", None),
    "moon_iron_ore": ("Moon Iron Ore", ("minecraft:raw_iron", 1, 1), "pickaxe", "stone"),
    "moonstone_ore": ("Moonstone Ore", ("rings:moonstone", 1, 2), "pickaxe", "iron"),
    "moonstone_block": ("Block of Moonstone", None, "pickaxe", "iron"),
    "mars_rock": ("Mars Rock", None, "pickaxe", None),
    "mars_rock_bricks": ("Mars Rock Bricks", None, "pickaxe", None),
    "mars_iron_ore": ("Mars Iron Ore", ("minecraft:raw_iron", 2, 4), "pickaxe", "stone"),
    "mars_gold_ore": ("Mars Gold Ore", ("minecraft:raw_gold", 1, 2), "pickaxe", "iron"),
    "mars_crystal_block": ("Block of Mars Crystal", None, "pickaxe", None),
    "mars_crystal": ("Mars Crystal", None, "pickaxe", None),
    "oan_stone": ("Oan Stone", None, "pickaxe", None),
    "oan_stone_bricks": ("Oan Stone Bricks", None, "pickaxe", None),
    "oan_emerald_ore": ("Oan Emerald Ore", ("minecraft:emerald", 1, 1), "pickaxe", "iron"),
    "will_crystal_ore": ("Will Crystal Ore", ("rings:will_shard", 1, 2), "pickaxe", "iron"),
    "oan_crystal": ("Oan Crystal", None, "pickaxe", None),
}
CRYSTALS = ("mars_crystal", "oan_crystal")


def planet_blocks(lang):
    for block, (name, drop, tool, tier) in PLANET_BLOCKS.items():
        lang[f"block.rings.{block}"] = name
        if block in CRYSTALS:
            # Like amethyst clusters: a cross of shards, turned to face away from whatever it grows on
            write(ASSETS / f"blockstates/{block}.json", {"variants": {
                f"facing={facing}": {"model": f"rings:block/{block}", **rotation} for facing, rotation in {
                    "up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
                    "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}.items()}})
            write(ASSETS / f"models/block/{block}.json", {"parent": "minecraft:block/cross", "textures": {"cross": f"rings:block/{block}"}})
            write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"rings:item/{block}"}})
            write(ASSETS / f"models/item/{block}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rings:block/{block}"}})
        else:
            write(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"rings:block/{block}"}}})
            write(ASSETS / f"models/block/{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"rings:block/{block}"}})
            write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"rings:block/{block}"}})

        itself = {"type": "minecraft:item", "condition": {"type": "minecraft:survives_explosion"}, "name": f"rings:{block}"}
        if drop is None:
            entry = itself
        else:
            item, least, most = drop
            modifiers = []
            if (least, most) != (1, 1):
                modifiers.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": least, "max": most}})
            modifiers += [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                          {"type": "minecraft:explosion_decay"}]
            entry = {"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"rings:{block}"},
                {"type": "minecraft:item", "modifier": modifiers, "name": item},
            ]}
        write(DATA / f"loot_table/blocks/{block}.json", {"type": "minecraft:block", "pools": [{"entries": [entry], "rolls": 1}],
                                                        "random_sequence": f"rings:blocks/{block}"})

    lang["item.rings.moonstone"] = "Moonstone"
    lang["item.rings.will_shard"] = "Will Shard"
    lang["message.rings.shard_no_ring"] = "Wear a ring to charge it with the shard"
    lang["message.rings.shard_full"] = "%s is already full"
    for item in ("moonstone", "will_shard"):
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"rings:item/{item}"}})
        write(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rings:item/{item}"}})

    # Mining: which tool, and how good it has to be
    def tag(path, values):
        write(RESOURCES / f"data/minecraft/tags/block/{path}.json", {"values": values})

    for tool in ("pickaxe", "shovel"):
        values = [f"rings:{block}" for block, entry in PLANET_BLOCKS.items() if entry[2] == tool]
        if tool == "pickaxe":
            values = [f"rings:{lantern_id(color)}" for color in COLORS] + values
        tag(f"mineable/{tool}", values)
    for tier in ("stone", "iron"):
        tag(f"needs_{tier}_tool", [f"rings:{block}" for block, entry in PLANET_BLOCKS.items() if entry[3] == tier])
    # So ring scans and other mods see the ores for what they are
    tag("iron_ores", ["rings:moon_iron_ore", "rings:mars_iron_ore"])
    tag("gold_ores", ["rings:mars_gold_ore"])
    tag("emerald_ores", ["rings:oan_emerald_ore"])
    tag("ores", ["rings:moonstone_ore", "rings:will_crystal_ore"])

    # Crafting: bricks from rock, and moonstone to its block and back
    for rock_name, bricks in (("moon_rock", "moon_rock_bricks"), ("mars_rock", "mars_rock_bricks"), ("oan_stone", "oan_stone_bricks")):
        write(DATA / f"recipe/{bricks}.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                               "key": {"#": f"rings:{rock_name}"}, "pattern": ["##", "##"],
                                               "result": {"count": 4, "id": f"rings:{bricks}"}})
        write(DATA / f"recipe/{bricks}_from_{rock_name}_stonecutting.json", {"type": "minecraft:stonecutting",
                                                                             "ingredient": f"rings:{rock_name}", "result": {"id": f"rings:{bricks}"}})
    write(DATA / "recipe/moonstone_block.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                                 "key": {"#": "rings:moonstone"}, "pattern": ["###", "###", "###"],
                                                 "result": {"id": "rings:moonstone_block"}})
    write(DATA / "recipe/moonstone.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                           "ingredients": ["rings:moonstone_block"], "result": {"count": 9, "id": "rings:moonstone"}})
    write(DATA / "recipe/mars_crystal_block.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                                    "key": {"#": "rings:mars_crystal"}, "pattern": ["##", "##"],
                                                    "result": {"id": "rings:mars_crystal_block"}})


if __name__ == "__main__":
    main()
