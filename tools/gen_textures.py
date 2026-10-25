"""Draws the mod's pixel-art textures. Run from the repo root: python tools/gen_textures.py"""
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("src/main/resources/assets/rings")

# Palette (gold matches vanilla's gold ingot)
CLEAR = (0, 0, 0, 0)
GOLD_LIGHT = (253, 245, 95, 255)
GOLD = (250, 214, 74, 255)
GOLD_MID = (220, 150, 19, 255)
GOLD_DARK = (178, 100, 17, 255)
GOLD_EDGE = (117, 40, 2, 255)
RUBY_LIGHT = (255, 120, 120, 255)
RUBY = (214, 32, 42, 255)
RUBY_DARK = (128, 12, 22, 255)
SLOT_ICON = (85, 85, 85, 255)  # vanilla's empty-slot icon gray

SUIT = (43, 79, 168, 255)
SUIT_DARK = (31, 58, 128, 255)
SUIT_LIGHT = (66, 108, 204, 255)
RED = (200, 32, 42, 255)
RED_DARK = (150, 20, 30, 255)
SKIN = (224, 176, 138, 255)
SKIN_DARK = (196, 146, 110, 255)
HAIR = (58, 42, 30, 255)
HAIR_DARK = (40, 28, 20, 255)
MASK = (27, 27, 46, 255)
EYE = (240, 240, 255, 255)


def save(image, path):
    out = ASSETS / path
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    print("wrote", out)


def ring_pixels():
    """A gold band seen at an angle, with a ruby set on top. Returns {(x, y): color} on a 16x16 grid."""
    px = {}
    cx, cy = 7.5, 9.5
    for y in range(16):
        for x in range(16):
            # Elliptical band: outer radii 6 x 4.5, inner 3.6 x 2.4
            outer = ((x - cx) / 6.0) ** 2 + ((y - cy) / 4.5) ** 2
            inner = ((x - cx) / 3.6) ** 2 + ((y - cy) / 2.4) ** 2
            if outer <= 1.0 and inner > 1.0:
                angle = math.atan2(y - cy, x - cx)
                # Light from the top left
                shade = math.cos(angle + 2.3)
                if outer > 0.8 or inner < 1.25:
                    color = GOLD_EDGE if shade < -0.2 else GOLD_DARK
                elif shade > 0.55:
                    color = GOLD_LIGHT
                elif shade > 0.0:
                    color = GOLD
                elif shade > -0.5:
                    color = GOLD_MID
                else:
                    color = GOLD_DARK
                px[(x, y)] = color

    # Gem setting on top of the band
    for x in range(5, 11):
        px[(x, 5)] = GOLD_DARK
    gem = {
        (6, 1): RUBY_DARK, (7, 1): RUBY, (8, 1): RUBY, (9, 1): RUBY_DARK,
        (5, 2): RUBY_DARK, (6, 2): RUBY_LIGHT, (7, 2): RUBY_LIGHT, (8, 2): RUBY, (9, 2): RUBY, (10, 2): RUBY_DARK,
        (5, 3): RUBY_DARK, (6, 3): RUBY_LIGHT, (7, 3): RUBY, (8, 3): RUBY, (9, 3): RUBY, (10, 3): RUBY_DARK,
        (6, 4): RUBY_DARK, (7, 4): RUBY, (8, 4): RUBY, (9, 4): RUBY_DARK,
    }
    px.update(gem)
    for x in (5, 10):
        px[(x, 4)] = GOLD_MID
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
    return dark if r < 0.12 else light if r > 0.93 else base


def hero_skin():
    rng = random.Random(7)
    image = Image.new("RGBA", (64, 64), CLEAR)

    def head(face, x, y, w, h):
        if face == "top":
            return HAIR if rng.random() > 0.15 else HAIR_DARK
        if face == "bottom":
            return SKIN
        if face == "back":
            return HAIR if y < 6 else SKIN
        if face in ("left", "right"):
            if y < 3:
                return HAIR
            if y in (3, 4):
                return MASK
            return SKIN
        # Front: hair fringe, mask band with eye holes, mouth
        if y < 2:
            return HAIR if (x + y) % 5 else HAIR_DARK
        if y in (3, 4):
            return EYE if y == 4 and x in (1, 2, 5, 6) else MASK
        if y == 6 and x in (3, 4):
            return SKIN_DARK
        return SKIN

    def body(face, x, y, w, h):
        if face == "front":
            # Gold ring emblem on the chest
            dx, dy = x - 3.5, y - 3.5
            dist = (dx * dx + dy * dy) ** 0.5
            if 1.3 <= dist <= 2.6:
                return GOLD if dy < 0.5 else GOLD_MID
            if y == 8:
                return GOLD_LIGHT if x in (3, 4) else GOLD_MID
        elif face in ("left", "right", "back") and y == 8:
            return GOLD_MID
        return textured(SUIT, SUIT_DARK, SUIT_LIGHT, rng)

    def arm(face, x, y, w, h):
        if face == "bottom":
            return RED
        if face == "top":
            return SUIT
        if y == 8:
            return GOLD_MID
        if y > 8:
            return RED if rng.random() > 0.1 else RED_DARK
        return textured(SUIT, SUIT_DARK, SUIT_LIGHT, rng)

    def leg(face, x, y, w, h):
        if face == "bottom":
            return RED_DARK
        if face == "top":
            return SUIT
        if y == 7:
            return GOLD_MID
        if y > 7:
            return RED if rng.random() > 0.1 else RED_DARK
        return textured(SUIT, SUIT_DARK, SUIT_LIGHT, rng)

    paint_box(image, 0, 0, 8, 8, 8, head)
    paint_box(image, 16, 16, 8, 12, 4, body)
    paint_box(image, 40, 16, 4, 12, 4, arm)   # right arm
    paint_box(image, 32, 48, 4, 12, 4, arm)   # left arm
    paint_box(image, 0, 16, 4, 12, 4, leg)    # right leg
    paint_box(image, 16, 48, 4, 12, 4, leg)   # left leg
    return image


def hero_cape():
    rng = random.Random(11)
    image = Image.new("RGBA", (64, 32), CLEAR)

    def cape(face, x, y, w, h):
        if face in ("front", "back"):
            # Gold trim along the sides and hem, ring emblem on the outside
            if x in (0, w - 1) or y == h - 1:
                return GOLD_MID
            if face == "front":
                dx, dy = x - 4.5, y - 6.5
                dist = (dx * dx + dy * dy) ** 0.5
                if 1.4 <= dist <= 2.6:
                    return GOLD
        if face == "top":
            return RED_DARK
        return RED if rng.random() > 0.12 else RED_DARK

    paint_box(image, 0, 0, 10, 16, 1, cape)
    return image


def icon():
    return ring_item().resize((128, 128), Image.NEAREST)


if __name__ == "__main__":
    save(ring_item(), "textures/item/hero_ring.png")
    save(ring_slot_icon(), "textures/gui/sprites/container/slot/ring.png")
    save(hero_skin(), "textures/entity/costume/hero.png")
    save(hero_cape(), "textures/entity/costume/hero_cape.png")
    save(icon(), "icon.png")
