"""Draws the mod's pixel-art textures. Run from the repo root: python tools/gen_textures.py"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("src/main/resources/assets/rings")

CLEAR = (0, 0, 0, 0)
SLOT_ICON = (85, 85, 85, 255)  # vanilla's empty-slot icon gray

BLACK = (24, 26, 28, 255)
BLACK_LIGHT = (44, 48, 52, 255)
WHITE = (236, 244, 236, 255)
SKIN = (96, 62, 44, 255)
SKIN_DARK = (78, 50, 36, 255)
LIP = (70, 42, 32, 255)
HAIR = (22, 18, 16, 255)


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a[:3], b[:3])) + (255,)


def shade(c, f):
    return tuple(min(255, round(x * f)) for x in c[:3]) + (255,)


class Palette:
    """Every shade one colour's textures use. Most are worked out from the colour's middle shade."""

    def __init__(self, mid, **overrides):
        mid = mid + (255,)
        white = (255, 255, 255)
        self.outline = shade(mid, 0.25)
        self.dark = shade(mid, 0.58)
        self.mid = mid
        self.light = mix(mid, white, 0.5)
        self.glow = mix(mid, white, 0.88)
        self.suit = shade(mid, 0.88)
        self.suit_dark = shade(mid, 0.62)
        self.suit_light = mix(self.suit, white, 0.3)
        self.panel = BLACK
        self.panel_light = BLACK_LIGHT
        self.emblem = WHITE
        self.metal = mix(shade(mid, 0.3), (28, 30, 32), 0.45)
        self.metal_dark = shade(self.metal, 0.55)
        self.metal_light = mix(self.metal, mid, 0.45)
        self.rivet = mix(mid, white, 0.6)
        for name, color in overrides.items():
            setattr(self, name, color + (255,))


# Same names as RingColor
COLORS = {
    "green": Palette((48, 190, 80),
                     outline=(12, 48, 20), dark=(24, 112, 44), light=(130, 255, 150), glow=(225, 255, 230),
                     suit=(36, 168, 72), suit_dark=(22, 118, 48), suit_light=(86, 214, 110),
                     metal=(30, 60, 40), metal_dark=(16, 34, 22), metal_light=(70, 120, 84), rivet=(150, 200, 160)),
    "blue": Palette((50, 130, 235)),
    "red": Palette((220, 40, 40)),
    "pink": Palette((240, 90, 190)),
    "orange": Palette((245, 140, 25)),
    # White and black don't work out from one shade
    "white": Palette((215, 220, 230),
                     outline=(80, 86, 100), dark=(160, 166, 180), light=(240, 243, 250), glow=(255, 255, 255),
                     suit=(228, 232, 238), suit_dark=(190, 195, 205), suit_light=(250, 250, 252),
                     emblem=(50, 54, 62),
                     metal=(200, 204, 212), metal_dark=(150, 154, 164), metal_light=(235, 238, 244), rivet=(255, 255, 255)),
    "black": Palette((40, 40, 48),
                     outline=(6, 6, 8), dark=(22, 22, 28), light=(80, 80, 94), glow=(150, 150, 170),
                     suit=(34, 34, 40), suit_dark=(22, 22, 26), suit_light=(52, 52, 60),
                     panel=(90, 92, 104), panel_light=(110, 112, 124), emblem=(220, 222, 230),
                     metal=(24, 24, 28), metal_dark=(12, 12, 14), metal_light=(56, 56, 64), rivet=(130, 130, 145)),
}
# Item and block names, matching RingColor (green keeps its 1.0 names)
RING_IDS = {c: "hero_ring" if c == "green" else c + "_ring" for c in COLORS}
LANTERN_IDS = {c: "power_lantern" if c == "green" else c + "_power_lantern" for c in COLORS}


def save(image, path):
    out = ASSETS / path
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    print("wrote", out)


# The ring's face, seen from the front: a square face with the lantern emblem (a circle between two bars)
RING_FACE = [
    "...kkkkkkkkkk...",
    "..kdllggggggdk..",
    "..kdwwwwwwwwdk..",
    "..kdggwwwwggdk..",
    "..kdgwggggwgdk..",
    "..kdgwggggwgdk..",
    "..kdggwwwwggdk..",
    "..kdwwwwwwwwdk..",
    "..kdggggggggdk..",
    "...kkkkkkkkkk...",
]


def ring_pixels(p):
    """Returns {(x, y): color} on a 16x16 grid: the band underneath, then the face on top."""
    px = {}
    cx, cy = 7.5, 10.5
    for y in range(8, 16):
        for x in range(16):
            outer = ((x - cx) / 6.5) ** 2 + ((y - cy) / 5.0) ** 2
            inner = ((x - cx) / 4.0) ** 2 + ((y - cy) / 2.8) ** 2
            if outer <= 1.0 and inner > 1.0:
                if outer > 0.78 or inner < 1.2:
                    px[(x, y)] = p.outline
                else:
                    px[(x, y)] = p.light if x < cx - 3 and y < 13 else p.mid if y < 14 else p.dark
    colors = {"k": p.outline, "d": p.dark, "g": p.mid, "l": p.light, "w": p.glow}
    for y, row in enumerate(RING_FACE):
        for x, ch in enumerate(row):
            if ch != ".":
                px[(x, y)] = colors[ch]
    return px


def ring_item(p):
    image = Image.new("RGBA", (16, 16), CLEAR)
    for (x, y), color in ring_pixels(p).items():
        image.putpixel((x, y), color)
    return image


def ring_slot_icon():
    """Flat gray silhouette, like vanilla's empty armor and shield slot icons."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    for (x, y) in ring_pixels(COLORS["green"]):
        image.putpixel((x, y), SLOT_ICON)
    return image


def paint_box(image, u, v, w, h, d, painter):
    """Paints one model box in skin UV layout. painter(face, x, y, w, h) returns a color."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                image.putpixel((fx + x, fy + y), painter(face, x, y, fw, fh))


def textured(base, dark, light, rng):
    """Adds a little fabric noise so big areas don't look flat."""
    r = rng.random()
    return dark if r < 0.12 else light if r > 0.94 else base


# Chest, 8x12: black shoulders and side panels, the lantern emblem in white on the green suit
CHEST = [
    "KKKKKKKK",
    "KWWWWWWK",
    "KGWWWWGK",
    "KWGGGGWK",
    "KWGGGGWK",
    "KGWWWWGK",
    "KWWWWWWK",
    "KGGGGGGK",
    "KGGGGGGK",
    "KGGGGGGK",
    "KGGGGGGK",
    "KGGGGGGK",
]


def lantern_skin(p):
    """The suit. Its head only shows where the player's own head can't (worn, the player keeps their head)."""
    rng = random.Random(7)
    image = Image.new("RGBA", (64, 64), CLEAR)
    suit = lambda: textured(p.suit, p.suit_dark, p.suit_light, rng)
    black = lambda: textured(p.panel, p.panel, p.panel_light, rng)

    def head(face, x, y, w, h):
        if face == "top":
            return HAIR
        if face == "bottom":
            return SKIN_DARK
        if face == "back":
            return HAIR if y < 4 else SKIN
        if face in ("left", "right"):
            return HAIR if y < 2 else SKIN
        # Front: close-cropped hair, brows, eyes glowing with the ring's energy, nose, mouth
        if y == 0 or (y == 1 and x in (0, 7)):
            return HAIR
        if y == 3 and x in (1, 2, 5, 6):
            return HAIR
        if y == 4 and x in (1, 2, 5, 6):
            return p.light
        if y == 5 and x in (3, 4):
            return SKIN_DARK
        if y == 6 and x in (3, 4):
            return LIP
        return SKIN

    def body(face, x, y, w, h):
        if face == "front":
            ch = CHEST[y][x]
            return black() if ch == "K" else p.emblem if ch == "W" else suit()
        if face in ("left", "right", "top"):
            return black()
        if face == "back":
            return black() if y < 2 or x in (0, w - 1) else suit()
        return suit()

    def arm(face, x, y, w, h):
        # Black sleeves, green gloves
        if face == "top":
            return black()
        if face == "bottom":
            return p.suit
        if y == 8:
            return p.suit_light
        return suit() if y > 8 else black()

    def leg(outer_face):
        def paint(face, x, y, w, h):
            # Green legs with a black stripe down the outside, black boots
            if face == "top":
                return p.suit
            if face == "bottom":
                return p.panel
            if y == 8:
                return p.suit_dark
            if y > 8:
                return black()
            return black() if face == outer_face else suit()
        return paint

    paint_box(image, 0, 0, 8, 8, 8, head)
    paint_box(image, 16, 16, 8, 12, 4, body)
    paint_box(image, 40, 16, 4, 12, 4, arm)             # right arm
    paint_box(image, 32, 48, 4, 12, 4, arm)             # left arm
    paint_box(image, 0, 16, 4, 12, 4, leg("right"))     # right leg
    paint_box(image, 16, 48, 4, 12, 4, leg("left"))     # left leg
    return image


# Power lantern glass, 16x16. The lantern's glass faces show x 4-11, y 5-13 of it (the model's UVs).
LANTERN_GLASS = [
    "gggggggggggggggg",
    "gggggggggggggggg",
    "gggggggggggggggg",
    "gggggggggggggggg",
    "ggggddddddddgggg",
    "ggggdwwwwwwdgggg",
    "ggggdlwwwwldgggg",
    "ggggdwllllwdgggg",
    "ggggdwllllwdgggg",
    "ggggdlwwwwldgggg",
    "ggggdwwwwwwdgggg",
    "ggggdlllllldgggg",
    "ggggdlllllldgggg",
    "ggggddddddddgggg",
    "gggggggggggggggg",
    "gggggggggggggggg",
]


def lantern_glass(p):
    colors = {"g": p.mid, "d": p.dark, "l": p.light, "w": p.glow}
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y, row in enumerate(LANTERN_GLASS):
        for x, ch in enumerate(row):
            image.putpixel((x, y), colors[ch])
    return image


def lantern_metal(p):
    """Metal tinted with the colour, with a lighter top edge and rivets."""
    rng = random.Random(3)
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            color = textured(p.metal, p.metal_dark, p.metal_light, rng)
            if x in (0, 15) or y in (0, 15):
                color = p.metal_dark
            elif y == 1:
                color = p.metal_light
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                color = p.rivet
            image.putpixel((x, y), color)
    return image


def hard_light(p):
    """Solid light: a bright rim, a see-through glowing middle, and a few streaks of shimmer."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                color = p.light[:3] + (235,)
            elif edge == 1:
                color = p.mid[:3] + (170,)
            elif (x + y) % 7 == 0 and 3 <= x <= 12:
                color = p.glow[:3] + (130,)
            else:
                color = p.mid[:3] + (95,)
            image.putpixel((x, y), color)
    return image


# Construct tools, drawn in white and greys so the ring's colour (the item's dye) tints them
CONSTRUCT_SWORD = [
    "..............ww",
    ".............wlw",
    "............wlw.",
    "...........wlw..",
    "..........wlw...",
    ".........wlw....",
    "........wlw.....",
    ".......wlw......",
    "..g...wlw.......",
    "..gg.wlw........",
    "...gglw.........",
    "....gg..........",
    "...h.gg.........",
    "..h...g.........",
    ".h..............",
    "p...............",
]
CONSTRUCT_PICKAXE = [
    "....wwwwww......",
    "..wwllllllww....",
    ".wl.......lww...",
    "w.........h.lw..",
    "w........h...lw.",
    ".........h....w.",
    "........h.....w.",
    ".......h.......w",
    "......h........w",
    ".....h..........",
    "....h...........",
    "...h............",
    "..h.............",
    ".h..............",
    "p...............",
    "................",
]
CONSTRUCT_AXE = [
    "......wwww......",
    ".....wllllw.....",
    "....wll..lw.....",
    "....wl..hlw.....",
    ".....w.h.w......",
    "......h.........",
    ".....h..........",
    "....h...........",
    "...h............",
    "..h.............",
    ".h..............",
    "p...............",
    "................",
    "................",
    "................",
    "................",
]
CONSTRUCT_SHOVEL = [
    "...........www..",
    "..........wllw..",
    ".........wlllw..",
    ".........wllw...",
    "........hwww....",
    ".......h........",
    "......h.........",
    ".....h..........",
    "....h...........",
    "...h............",
    "..h.............",
    ".h..............",
    "p...............",
    "................",
    "................",
    "................",
]
TOOL_SHADES = {"w": (255, 255, 255, 255), "l": (205, 205, 205, 255), "g": (230, 230, 230, 255),
               "h": (160, 160, 160, 255), "p": (120, 120, 120, 255)}


def construct_tool(pattern):
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                image.putpixel((x, y), TOOL_SHADES[ch])
    return image


def icon():
    return ring_item(COLORS["green"]).resize((128, 128), Image.NEAREST)


if __name__ == "__main__":
    for name, palette in COLORS.items():
        save(ring_item(palette), f"textures/item/{RING_IDS[name]}.png")
        save(lantern_skin(palette), f"textures/entity/costume/{name}_lantern.png")
        save(lantern_glass(palette), f"textures/block/{LANTERN_IDS[name]}_glass.png")
        save(lantern_metal(palette), f"textures/block/{LANTERN_IDS[name]}_metal.png")
        save(hard_light(palette), f"textures/block/hard_light_{name}.png")
    save(ring_slot_icon(), "textures/gui/sprites/container/slot/ring.png")
    save(construct_tool(CONSTRUCT_SWORD), "textures/item/construct_sword.png")
    save(construct_tool(CONSTRUCT_PICKAXE), "textures/item/construct_pickaxe.png")
    save(construct_tool(CONSTRUCT_AXE), "textures/item/construct_axe.png")
    save(construct_tool(CONSTRUCT_SHOVEL), "textures/item/construct_shovel.png")
    save(icon(), "icon.png")
