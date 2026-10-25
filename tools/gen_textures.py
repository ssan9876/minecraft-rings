"""Draws the mod's pixel-art textures. Run from the repo root: python tools/gen_textures.py"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("src/main/resources/assets/rings")

CLEAR = (0, 0, 0, 0)
SLOT_ICON = (85, 85, 85, 255)  # vanilla's empty-slot icon gray

# Ring energy greens
OUTLINE = (12, 48, 20, 255)
DARK = (24, 112, 44, 255)
GREEN = (48, 190, 80, 255)
LIGHT = (130, 255, 150, 255)
GLOW = (225, 255, 230, 255)

# Suit
SUIT = (36, 168, 72, 255)
SUIT_DARK = (22, 118, 48, 255)
SUIT_LIGHT = (86, 214, 110, 255)
BLACK = (24, 26, 28, 255)
BLACK_LIGHT = (44, 48, 52, 255)
EMBLEM = (236, 244, 236, 255)
SKIN = (96, 62, 44, 255)
SKIN_DARK = (78, 50, 36, 255)
LIP = (70, 42, 32, 255)
HAIR = (22, 18, 16, 255)
EYE = (140, 255, 160, 255)


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
RING_COLORS = {"k": OUTLINE, "d": DARK, "g": GREEN, "l": LIGHT, "w": GLOW}


def ring_pixels():
    """Returns {(x, y): color} on a 16x16 grid: the band underneath, then the face on top."""
    px = {}
    cx, cy = 7.5, 10.5
    for y in range(8, 16):
        for x in range(16):
            outer = ((x - cx) / 6.5) ** 2 + ((y - cy) / 5.0) ** 2
            inner = ((x - cx) / 4.0) ** 2 + ((y - cy) / 2.8) ** 2
            if outer <= 1.0 and inner > 1.0:
                if outer > 0.78 or inner < 1.2:
                    px[(x, y)] = OUTLINE
                else:
                    px[(x, y)] = LIGHT if x < cx - 3 and y < 13 else GREEN if y < 14 else DARK
    for y, row in enumerate(RING_FACE):
        for x, ch in enumerate(row):
            if ch != ".":
                px[(x, y)] = RING_COLORS[ch]
    return px


def ring_item():
    image = Image.new("RGBA", (16, 16), CLEAR)
    for (x, y), color in ring_pixels().items():
        image.putpixel((x, y), color)
    return image


def ring_slot_icon():
    """Flat gray silhouette, like vanilla's empty armor and shield slot icons."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    for (x, y) in ring_pixels():
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


def lantern_skin():
    rng = random.Random(7)
    image = Image.new("RGBA", (64, 64), CLEAR)
    suit = lambda: textured(SUIT, SUIT_DARK, SUIT_LIGHT, rng)
    black = lambda: textured(BLACK, BLACK, BLACK_LIGHT, rng)

    def head(face, x, y, w, h):
        if face == "top":
            return HAIR
        if face == "bottom":
            return SKIN_DARK
        if face == "back":
            return HAIR if y < 4 else SKIN
        if face in ("left", "right"):
            return HAIR if y < 2 else SKIN
        # Front: close-cropped hair, brows, glowing ring-green eyes, nose, mouth
        if y == 0 or (y == 1 and x in (0, 7)):
            return HAIR
        if y == 3 and x in (1, 2, 5, 6):
            return HAIR
        if y == 4 and x in (1, 2, 5, 6):
            return EYE
        if y == 5 and x in (3, 4):
            return SKIN_DARK
        if y == 6 and x in (3, 4):
            return LIP
        return SKIN

    def body(face, x, y, w, h):
        if face == "front":
            ch = CHEST[y][x]
            return black() if ch == "K" else EMBLEM if ch == "W" else suit()
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
            return SUIT
        if y == 8:
            return SUIT_LIGHT
        return suit() if y > 8 else black()

    def leg(outer_face):
        def paint(face, x, y, w, h):
            # Green legs with a black stripe down the outside, black boots
            if face == "top":
                return SUIT
            if face == "bottom":
                return BLACK
            if y == 8:
                return SUIT_DARK
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


def icon():
    return ring_item().resize((128, 128), Image.NEAREST)


if __name__ == "__main__":
    save(ring_item(), "textures/item/hero_ring.png")
    save(ring_slot_icon(), "textures/gui/sprites/container/slot/ring.png")
    save(lantern_skin(), "textures/entity/costume/lantern.png")
    save(icon(), "icon.png")
