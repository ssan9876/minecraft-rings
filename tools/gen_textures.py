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
    "yellow": Palette((240, 200, 30), emblem=(30, 28, 20)),
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


def suit_head(p):
    """A suit's head. It only shows where the player's own head can't (worn, the player keeps their head)."""
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
    return head


def paint_suit(head, body, right_arm, left_arm, right_leg, left_leg):
    image = Image.new("RGBA", (64, 64), CLEAR)
    paint_box(image, 0, 0, 8, 8, 8, head)
    paint_box(image, 16, 16, 8, 12, 4, body)
    paint_box(image, 40, 16, 4, 12, 4, right_arm)
    paint_box(image, 32, 48, 4, 12, 4, left_arm)
    paint_box(image, 0, 16, 4, 12, 4, right_leg)
    paint_box(image, 16, 48, 4, 12, 4, left_leg)
    return image


def lantern_skin(p):
    """The classic suit: black shoulders, sleeves and boots, the ring's colour over the chest, gloves and legs."""
    rng = random.Random(7)
    suit = lambda: textured(p.suit, p.suit_dark, p.suit_light, rng)
    black = lambda: textured(p.panel, p.panel, p.panel_light, rng)

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

    return paint_suit(suit_head(p), body, arm, arm, leg("right"), leg("left"))


def shadow_skin(p):
    """The shadow suit: black all over, lit by the ring's colour at the emblem, the shoulders, stripes down the outside
    of the arms and legs, and the gloves and boots."""
    rng = random.Random(11)
    black = lambda: textured(p.panel, p.panel, p.panel_light, rng)

    def body(face, x, y, w, h):
        if face == "front":
            ch = CHEST[y][x]
            if ch == "W":
                return p.glow
            if ch == "K" and y < 2:
                return p.suit if y == 0 else p.suit_dark
            return black()
        if face == "back":
            return p.suit if y == 0 else black()
        if face == "top":
            return p.suit
        return black()

    def arm(outer_face):
        def paint(face, x, y, w, h):
            if face == "top":
                return p.suit
            if face == "bottom":
                return p.suit_dark
            if y > 8:
                return p.suit if y > 9 else p.light
            return p.suit if face == outer_face and x in (1, 2) else black()
        return paint

    def leg(outer_face):
        def paint(face, x, y, w, h):
            if face == "top":
                return p.panel
            if face == "bottom":
                return p.suit_dark
            if y > 9:
                return p.suit if y > 10 else p.light
            return p.suit if face == outer_face and x in (1, 2) else black()
        return paint

    return paint_suit(suit_head(p), body, arm("right"), arm("left"), leg("right"), leg("left"))


def armored_skin(p):
    """The armored suit: plates of the ring's colour with seams between, metal pauldrons, belt, knee guards, gauntlets
    and boots, and the emblem glowing on the breastplate."""
    rng = random.Random(13)
    plate = lambda: textured(p.suit, p.suit_dark, p.suit_light, rng)
    metal = lambda: textured(p.metal, p.metal_dark, p.metal_light, rng)

    def body(face, x, y, w, h):
        if face == "front":
            if y >= 10:
                return p.rivet if y == 10 and x in (3, 4) else metal()
            ch = CHEST[y][x]
            if ch == "W":
                return p.glow
            if ch == "K":
                return p.metal_light if y == 0 else metal()
            return p.suit_dark if y == 7 else plate()
        if face == "back":
            if y >= 10 or y == 0:
                return metal()
            return p.suit_dark if y in (4, 7) or x == w // 2 else plate()
        if face in ("left", "right"):
            return metal() if y >= 10 else p.suit_dark if y in (4, 7) else plate()
        return metal()

    def arm(face, x, y, w, h):
        if face == "top" or y < 3:
            # The pauldron, studded along its edge
            if face == "top" or y == 0:
                return p.metal_light
            return p.rivet if y == 2 and x % 2 == 0 else metal()
        if face == "bottom" or y > 8:
            return metal()
        if y == 8:
            return p.metal_light
        return p.suit_dark if y == 5 else plate()

    def leg(face, x, y, w, h):
        if face == "top":
            return plate()
        if face == "bottom" or y > 8:
            return p.metal_dark if y == 11 else metal()
        if y in (5, 6):
            return p.metal_light if y == 5 else metal()
        return p.suit_dark if y == 2 else plate()

    return paint_suit(suit_head(p), body, arm, arm, leg, leg)


def robed_skin(p):
    """The robed suit: a long tunic of the ring's colour with a light trim and a black sash, its emblem high on the
    chest, wide sleeves over black cuffs, and black trousers and boots below the hem."""
    rng = random.Random(17)
    cloth = lambda: textured(p.suit, p.suit_dark, p.suit_light, rng)
    black = lambda: textured(p.panel, p.panel, p.panel_light, rng)
    emblem = {(3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)}

    def body(face, x, y, w, h):
        if face == "top":
            return p.light
        if face == "bottom":
            return p.suit_dark
        if y == 0:
            return p.light
        if y == 8:
            return p.glow if face == "front" and x in (3, 4) else black()
        if face == "front":
            if (x, y) in emblem:
                return p.emblem
            # The tunic's front edge, trimmed down the middle below the sash
            return p.light if y > 8 and x in (3, 4) else cloth()
        return cloth()

    def arm(face, x, y, w, h):
        if face == "top":
            return p.light
        if face == "bottom" or y > 9:
            return p.suit_light if face == "bottom" else p.suit
        if y == 9:
            return p.light
        return black() if y > 7 else cloth()

    def leg(face, x, y, w, h):
        if face == "top":
            return cloth()
        if face == "bottom":
            return p.panel
        # The tunic hangs to the knee, trimmed at the hem
        if y < 4:
            return cloth()
        if y == 4:
            return p.light
        return HAIR if y > 9 else black()

    return paint_suit(suit_head(p), body, arm, arm, leg, leg)


# Same names as SuitStyle (the classic suit keeps its first name, lantern)
SUITS = {"lantern": lantern_skin, "shadow": shadow_skin, "armored": armored_skin, "robed": robed_skin}


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


# The construct shield's face, 12 wide (columns 2 to 13 of the texture): a heater shield of hard light, bright
# rimmed, with the lantern emblem in the middle. "r" rim, "f" the see-through face, "e" the emblem.
CONSTRUCT_SHIELD_FACE = [
    "..rrrrrrrrrrrr..",
    "..rffffffffffr..",
    "..rffffffffffr..",
    "..rfeeeeeeeefr..",
    "..rffffffffffr..",
    "..rfffeeeefffr..",
    "..rffeffffeffr..",
    "..rffeffffeffr..",
    "..rfffeeeefffr..",
    "..rffffffffffr..",
    "..rfeeeeeeeefr..",
    "..rffffffffffr..",
    "...rffffffffr...",
    "....rffffffr....",
    ".....rffffr.....",
    "......rrrr......",
]


def construct_shield(back):
    image = Image.new("RGBA", (16, 16), CLEAR)
    shades = {"r": (255, 255, 255, 255), "f": (200, 200, 200, 150), "e": (255, 255, 255, 235)}
    if back:
        shades = {"r": (225, 225, 225, 255), "f": (165, 165, 165, 170), "e": (165, 165, 165, 170)}
    for y, row in enumerate(CONSTRUCT_SHIELD_FACE):
        for x, ch in enumerate(row[:16]):
            if ch != ".":
                image.putpixel((x, y), shades[ch])
    return image


def construct_tool(pattern):
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                image.putpixel((x, y), TOOL_SHADES[ch])
    return image


def ring_band():
    """The ring worn on the hand: plain white with a faint pattern, tinted with the ring's colours."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            v = 255 if (x + y) % 5 else 236
            image.putpixel((x, y), (v, v, v, 255))
    return image


def power_battery():
    """The Central Power Battery's core: living green light, brightest at the lantern emblem in the middle."""
    p = COLORS["green"]
    rng = random.Random(11)
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            ch = LANTERN_GLASS[y][x]
            color = {"g": p.light, "d": p.mid, "l": p.glow, "w": (255, 255, 255, 255)}[ch]
            if ch == "g" and rng.random() < 0.2:
                color = p.glow
            image.putpixel((x, y), color)
    return image


def creature_skin(head, body, arm, leg):
    """A 64x64 skin in the zombie layout: the left arm and leg are drawn mirrored from the right ones."""
    image = Image.new("RGBA", (64, 64), CLEAR)
    paint_box(image, 0, 0, 8, 8, 8, head)
    paint_box(image, 16, 16, 8, 12, 4, body)
    paint_box(image, 40, 16, 4, 12, 4, arm)
    paint_box(image, 0, 16, 4, 12, 4, leg)
    return image


def guardian_skin():
    """A Guardian of the Universe: blue skin, a fringe of white hair, a red robe with the lantern on its chest."""
    rng = random.Random(21)
    skin, skin_dark = (84, 140, 224, 255), (60, 110, 190, 255)
    hair = (236, 236, 240, 255)
    robe = lambda: textured((176, 28, 36, 255), (140, 20, 28, 255), (200, 50, 56, 255), rng)
    black = (24, 20, 22, 255)

    def head(face, x, y, w, h):
        if face == "top":
            return skin
        if face == "bottom":
            return skin_dark
        if face in ("left", "right", "back"):
            return hair if 2 <= y <= 5 else skin
        if y == 2 and x in (1, 2, 5, 6):
            return hair
        if y == 3 and x in (1, 2, 5, 6):
            return (20, 20, 40, 255) if x in (2, 5) else (240, 240, 250, 255)
        if y == 5 and x in (3, 4):
            return skin_dark
        if y == 6 and 2 <= x <= 5:
            return (40, 60, 120, 255)
        return skin

    def body(face, x, y, w, h):
        if y in (7, 8):
            return black
        if face == "front" and y in (1, 2, 3, 4) and 2 <= x <= 5 and (y in (1, 4) or x in (2, 5)):
            return (240, 240, 240, 255)
        return robe()

    def arm(face, x, y, w, h):
        return skin if y >= 9 else robe()

    def leg(face, x, y, w, h):
        return black if y >= 10 else robe()

    return creature_skin(head, body, arm, leg)


def manhunter_skin():
    """A Manhunter: a blue android in red, its face a silver mask with a black visor and red eyes."""
    rng = random.Random(31)
    blue = lambda: textured((36, 54, 150, 255), (26, 38, 110, 255), (60, 80, 180, 255), rng)
    red = lambda: textured((190, 30, 30, 255), (150, 20, 20, 255), (220, 60, 50, 255), rng)
    silver = (205, 208, 216, 255)
    black = (18, 18, 22, 255)

    def head(face, x, y, w, h):
        if face == "top" or face == "back":
            return blue()
        if face in ("left", "right"):
            return blue() if y < 3 else silver
        if face == "bottom":
            return silver
        if y < 2:
            return blue()
        if y in (3, 4):
            return (255, 40, 30, 255) if y == 3 and x in (2, 5) else black
        if y == 6 and 2 <= x <= 5:
            return (120, 124, 132, 255)
        return silver

    def body(face, x, y, w, h):
        if y in (8,):
            return black
        if face in ("front", "back") and 1 <= x <= 6 and y < 8:
            return red()
        return blue()

    def arm(face, x, y, w, h):
        return red() if y >= 8 else blue()

    def leg(face, x, y, w, h):
        return red() if y >= 8 else blue()

    return creature_skin(head, body, arm, leg)


def nekron_skin():
    """Nekron: a bone-white skull with black sockets, in a black robe trimmed with deathly violet."""
    rng = random.Random(41)
    bone, bone_dark = (226, 222, 204, 255), (176, 170, 150, 255)
    robe = lambda: textured((26, 20, 30, 255), (16, 12, 20, 255), (40, 32, 46, 255), rng)
    violet = (112, 44, 150, 255)
    black = (6, 4, 8, 255)

    def head(face, x, y, w, h):
        if face != "front":
            return bone if face != "bottom" else bone_dark
        if y in (2, 3, 4) and x in (1, 2, 5, 6):
            return black
        if y == 5 and x in (3, 4):
            return black
        if y == 7 and x % 2 == 1:
            return bone_dark
        return bone

    def body(face, x, y, w, h):
        if x in (0, w - 1) or y == 0:
            return violet
        if face == "front" and 2 <= x <= 5 and 3 <= y <= 6 and (y in (3, 6) or x in (2, 5)):
            return violet
        return robe()

    def arm(face, x, y, w, h):
        if y >= 9:
            return bone
        return violet if y == 8 else robe()

    def leg(face, x, y, w, h):
        return violet if y == 11 else robe()

    return creature_skin(head, body, arm, leg)


# Energy motes, 16x16, white so the particle's colour tints them: a soft orb that sharpens into a four-pointed
# star and shrinks to a point over the mote's life
ENERGY_FRAMES = 6


def energy_particle(frame):
    import math
    t = frame / (ENERGY_FRAMES - 1)
    image = Image.new("RGBA", (16, 16), CLEAR)
    glow_radius = 7.5 - 4.5 * t
    star = t * 1.4
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            glow = max(0.0, 1.0 - d / glow_radius) ** 2
            # The star's rays along the axes, thinning out with distance
            ray = max(0.0, 1.0 - min(abs(dx), abs(dy)) / 0.9) * max(0.0, 1.0 - d / (2.0 + 5.5 * min(star, 1.0)))
            a = min(1.0, glow + ray * min(star, 1.0))
            if a <= 0.02:
                continue
            # White-hot in the middle, the tint's full colour toward the edge
            v = round(255 - 60 * min(1.0, d / max(glow_radius, 1.0)))
            image.putpixel((x, y), (v, v, v, round(a * 255)))
    return image


# The planets' blocks. Rock is drawn from tileable noise in a few shades of each planet's stone.
PLANET_ROCK = {
    "moon": [(88, 88, 92), (112, 112, 116), (134, 134, 138), (156, 156, 160)],
    "moon_dust": [(150, 150, 154), (170, 170, 174), (186, 186, 190), (200, 200, 204)],
    "mars": [(110, 46, 30), (140, 62, 38), (166, 82, 50), (190, 106, 66)],
    "oan": [(30, 54, 42), (42, 72, 56), (56, 92, 70), (74, 114, 88)],
}


def tile_noise(seed, cells=4):
    """16x16 smooth noise in 0..1 that wraps at the edges, so the texture tiles without seams."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * 16 for _ in range(16)]
    step = 16 / cells
    for y in range(16):
        for x in range(16):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = gx - int(gx), gy - int(gy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            bottom = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = top * (1 - ty) + bottom * ty
    return out


def rock(shades, seed, pits=0):
    rng = random.Random(seed)
    coarse, fine = tile_noise(seed, 4), tile_noise(seed + 1, 8)
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            v = coarse[y][x] * 0.6 + fine[y][x] * 0.4 + (rng.random() - 0.5) * 0.25
            shade = shades[max(0, min(len(shades) - 1, int(v * len(shades))))]
            image.putpixel((x, y), shade + (255,))
    # Little pits, dark with a lit lower rim, as on the Moon's dust and rock
    for _ in range(pits):
        px, py = rng.randrange(16), rng.randrange(16)
        image.putpixel((px, py), shades[0] + (255,))
        image.putpixel(((px + 1) % 16, (py + 1) % 16), shades[-1] + (255,))
    return image


def bricks(shades, seed):
    """Bricks cut from the rock: rows of 8x4 blocks, staggered, with dark mortar and lit top edges."""
    rng = random.Random(seed)
    image = Image.new("RGBA", (16, 16), CLEAR)
    mortar = tuple(round(c * 0.6) for c in shades[0])
    for y in range(16):
        row = y // 4
        for x in range(16):
            bx = (x + (4 if row % 2 else 0)) % 8
            if y % 4 == 3 or bx == 7:
                color = mortar
            elif y % 4 == 0 or bx == 0:
                color = shades[3]
            else:
                color = shades[1] if rng.random() < 0.3 else shades[2]
            image.putpixel((x, y), color + (255,))
    return image


def ore(base, gem, seed, clusters=4):
    """Gems or metal in the rock: small clumps, dark edged, with a highlight."""
    rng = random.Random(seed)
    image = base.copy()
    dark = tuple(round(c * 0.55) for c in gem)
    light = tuple(min(255, round(c + (255 - c) * 0.6)) for c in gem)
    for _ in range(clusters):
        cx, cy = rng.randrange(2, 13), rng.randrange(2, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
            if rng.random() < 0.8 or (dx, dy) == (0, 0):
                image.putpixel((cx + dx, cy + dy), gem + (255,))
        image.putpixel((cx - 1, cy + 1), dark + (255,))
        image.putpixel((cx + 1, cy + 2), dark + (255,))
        image.putpixel((cx, cy), light + (255,))
    return image


def crystal_block(gem, seed):
    """Crystal grown solid: diagonal facets in light and shadow."""
    rng = random.Random(seed)
    dark = tuple(round(c * 0.6) for c in gem)
    light = tuple(min(255, round(c + (255 - c) * 0.55)) for c in gem)
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            facet = ((x + y) // 4 + (x - y + 16) // 6) % 3
            color = (dark, gem, light)[facet]
            if rng.random() < 0.06:
                color = (255, 255, 255)
            image.putpixel((x, y), color + (255,))
    return image


def polished(gem, seed):
    """A block of cut gem: four tiles, bright edged, glowing in the middle."""
    rng = random.Random(seed)
    dark = tuple(round(c * 0.7) for c in gem)
    light = tuple(min(255, round(c + (255 - c) * 0.7)) for c in gem)
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            tx, ty = x % 8, y % 8
            if tx == 7 or ty == 7:
                color = dark
            elif tx == 0 or ty == 0:
                color = light
            elif 2 <= tx <= 5 and 2 <= ty <= 5:
                color = light if rng.random() < 0.5 else gem
            else:
                color = gem
            image.putpixel((x, y), color + (255,))
    return image


# Crystal clusters, drawn for vanilla's cross model: three shards growing up from the bottom
CRYSTAL = [
    "................",
    "........l.......",
    ".......lg.......",
    ".......lgd......",
    "...l...lgd......",
    "..lg...lgd...l..",
    "..lgd..lgd..lg..",
    "..lgd..lgd..lgd.",
    "..lgd..lgd..lgd.",
    "..lgd.llggd.lgd.",
    "..lgddlggdd.lgd.",
    "...lgdlggd.lgdd.",
    "...lggdlgddlgd..",
    "....lgdlgdlggd..",
    ".....lggggggd...",
    "......dddddd....",
]
GEM_ITEMS = {
    # A round, milky gem with a bright glint
    "moonstone": [
        "................",
        "................",
        "......kkkk......",
        "....kkggggkk....",
        "...kgwwggggdk...",
        "...kgwgggggdk...",
        "..kggggggggddk..",
        "..kggggggggddk..",
        "..kgggggggdddk..",
        "..kggggggdddk...",
        "...kgggddddk....",
        "....kkddddk.....",
        "......kkkk......",
        "................",
        "................",
        "................",
    ],
    # A long, sharp shard of green will
    "will_shard": [
        "................",
        "...........kk...",
        "..........kwgk..",
        ".........kwggk..",
        "........kwggdk..",
        ".......kwggdk...",
        "......kwggdk....",
        ".....kwggdk.....",
        "....kwggdk......",
        "...kwggdk.......",
        "...kggdk........",
        "..kggdk.........",
        "..kgdk..........",
        "..kkk...........",
        "................",
        "................",
    ],
}


def sprite(pattern, gem):
    dark = tuple(round(c * 0.6) for c in gem)
    light = tuple(min(255, round(c + (255 - c) * 0.6)) for c in gem)
    outline = tuple(round(c * 0.3) for c in gem)
    shades = {"g": gem, "d": dark, "l": light, "w": (255, 255, 255), "k": outline}
    image = Image.new("RGBA", (16, 16), CLEAR)
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                image.putpixel((x, y), shades[ch] + (255,))
    return image


MOONSTONE = (196, 222, 240)
MARS_CRYSTAL = (226, 60, 48)
OAN_CRYSTAL = (90, 240, 120)
WILL = (130, 255, 150)


def planet_textures():
    moon, mars, oan = rock(PLANET_ROCK["moon"], 101, pits=5), rock(PLANET_ROCK["mars"], 201), rock(PLANET_ROCK["oan"], 301)
    save(rock(PLANET_ROCK["moon_dust"], 111, pits=7), "textures/block/moon_dust.png")
    save(moon, "textures/block/moon_rock.png")
    save(bricks(PLANET_ROCK["moon"], 102), "textures/block/moon_rock_bricks.png")
    save(ore(moon, (216, 175, 147), 103), "textures/block/moon_iron_ore.png")
    save(ore(moon, MOONSTONE, 104, clusters=3), "textures/block/moonstone_ore.png")
    save(polished(MOONSTONE, 105), "textures/block/moonstone_block.png")
    save(mars, "textures/block/mars_rock.png")
    save(bricks(PLANET_ROCK["mars"], 202), "textures/block/mars_rock_bricks.png")
    save(ore(mars, (226, 214, 200), 203, clusters=5), "textures/block/mars_iron_ore.png")
    save(ore(mars, (252, 222, 70), 204), "textures/block/mars_gold_ore.png")
    save(crystal_block(MARS_CRYSTAL, 205), "textures/block/mars_crystal_block.png")
    save(sprite(CRYSTAL, MARS_CRYSTAL), "textures/block/mars_crystal.png")
    save(oan, "textures/block/oan_stone.png")
    save(bricks(PLANET_ROCK["oan"], 302), "textures/block/oan_stone_bricks.png")
    save(ore(oan, (60, 230, 110), 303), "textures/block/oan_emerald_ore.png")
    save(ore(oan, WILL, 304, clusters=3), "textures/block/will_crystal_ore.png")
    save(sprite(CRYSTAL, OAN_CRYSTAL), "textures/block/oan_crystal.png")
    save(sprite(GEM_ITEMS["moonstone"], MOONSTONE), "textures/item/moonstone.png")
    save(sprite(GEM_ITEMS["will_shard"], WILL), "textures/item/will_shard.png")


def icon():
    return ring_item(COLORS["green"]).resize((128, 128), Image.NEAREST)


if __name__ == "__main__":
    for name, palette in COLORS.items():
        save(ring_item(palette), f"textures/item/{RING_IDS[name]}.png")
        for style, skin in SUITS.items():
            save(skin(palette), f"textures/entity/costume/{name}_{style}.png")
        save(lantern_glass(palette), f"textures/block/{LANTERN_IDS[name]}_glass.png")
        save(lantern_metal(palette), f"textures/block/{LANTERN_IDS[name]}_metal.png")
        save(hard_light(palette), f"textures/block/hard_light_{name}.png")
    save(ring_slot_icon(), "textures/gui/sprites/container/slot/ring.png")
    save(ring_band(), "textures/entity/ring_band.png")
    save(power_battery(), "textures/block/power_battery.png")
    save(guardian_skin(), "textures/entity/oa_guardian.png")
    save(manhunter_skin(), "textures/entity/manhunter.png")
    save(nekron_skin(), "textures/entity/nekron.png")
    save(construct_tool(CONSTRUCT_SWORD), "textures/item/construct_sword.png")
    save(construct_tool(CONSTRUCT_PICKAXE), "textures/item/construct_pickaxe.png")
    save(construct_tool(CONSTRUCT_AXE), "textures/item/construct_axe.png")
    save(construct_tool(CONSTRUCT_SHOVEL), "textures/item/construct_shovel.png")
    save(construct_shield(False), "textures/item/construct_shield.png")
    save(construct_shield(True), "textures/item/construct_shield_back.png")
    for frame in range(ENERGY_FRAMES):
        save(energy_particle(frame), f"textures/particle/energy_{frame}.png")
    planet_textures()
    save(icon(), "icon.png")
