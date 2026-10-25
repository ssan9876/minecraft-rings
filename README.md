# Rings

A Fabric mod for Minecraft 26.3 that adds a ring slot to the player's inventory, above the offhand slot.
Rings grant powers while switched on with the **Activate Ring** key (default `R`, rebindable under
Options → Controls → Rings).

**Hero Ring:** a green power ring. While it's on:
- you can fly. Flying fast you tip into a superhero pose (fist out front, banking into turns) and leave a trail
  of green energy. Flying straight up or down, or hovering, you stay upright;
- you wear the lantern suit, a green and black suit with the lantern emblem, under your own head;
- you're outlined in green. Like the Glowing effect, the outline shows through walls.

Everyone else sees all of this too.

**Power:** the ring runs on its charge, shown by the meter to the right of the hotbar and the bar on the item. A
full ring lasts 30 minutes switched on, or 10 minutes of flying. When it runs out the ring switches off, and it
won't switch back on until you charge it: **hold right-click on a Power Lantern** while wearing or holding the
ring (about 2 seconds from empty).

Crafting (shaped):

```
Hero Ring:       Phantom Membrane  Diamond   Phantom Membrane
                 Gold Ingot        ·         Gold Ingot
                 ·                 Gold Ingot  ·

Power Lantern:   Iron Ingot  Emerald  Iron Ingot
                 Emerald     Lantern  Emerald
                 Iron Ingot  Emerald  Iron Ingot
```

How it behaves:
- Shift-click a ring to equip it, like armor. Taking it off switches it off.
- Switching it off in mid-air gives 5 seconds of slow falling so you don't fall to your death.
- The ring drops on death like the rest of your inventory (kept with `keep_inventory`). Dying switches it off.
- Creative and spectator players keep their own flight when the ring turns off.

It's needed on both the server and every player's client.

## Develop

Needs JDK 25.

| Command | What it does |
|---|---|
| `./gradlew build` | Builds `build/libs/rings-<version>.jar` |
| `./gradlew runClient` | Starts Minecraft with the mod, in singleplayer |
| `./gradlew runClientGameTest` | Opens a real client and plays through the ring (`src/gametest`). Screenshots land in `build/run/clientGameTest/screenshots` |
| `./release.sh` | Publishes the version in `gradle.properties` as a GitHub release and points `mods.txt` at it |
| `./deploy.sh` | Builds and installs the mod on the server, then restarts it |
| `python tools/gen_textures.py` | Redraws the ring, slot icon and costume textures |

## Releasing

Players get the mod through the join scripts in
[minecraft-cloudflare-tunnel](https://github.com/ssan9876/minecraft-cloudflare-tunnel), which read
[`mods.txt`](mods.txt) and install Fabric plus every mod listed there. To ship a change:

1. Bump `version` in `gradle.properties` and commit.
2. `./release.sh` publishes the release and updates `mods.txt`.
3. `./deploy.sh` installs it on the server.

Players pick up the new version the next time they run the join script. Until then the server turns them away
with a message about mismatched mods. If you add or update another mod on the server, add its download link to
`mods.txt` too.

Minecraft 26.x isn't obfuscated, so the code uses Mojang's own names, and `./gradlew genSources` gives readable
sources to look things up in.

## Adding a ring

1. Register it in `Rings.java`, for example `new RingItem(props, false, id("knight"))` for a costume without flight.
2. Add its costume to `Costumes.java`, and its skin at `assets/rings/textures/entity/costume/knight.png`
   (a standard 64×64 player skin) plus an optional `knight_cape.png`.
3. Add the item model (`items/` and `models/item/`), texture, name in `lang/en_us.json`, and a recipe.

Powers live in `RingPowers.java`, which runs every server tick for each player.

## How it works

- **Ring slot:** a mixin adds a slot to the player's inventory menu (index 46, after the vanilla slots). The ring is
  stored in a Fabric data attachment on the player, so it saves with the player and survives relogging.
- **Activation:** the key sends a packet; the server toggles the ring and grants or removes powers.
- **Costume:** the server sets a costume attachment that syncs to every client. The player renderer swaps in the
  costume's skin and cape while it's set, and gives the player a green outline. The costume model hides its head;
  `OwnHeadLayer` draws it with the player's own skin using a second, head-only player model.
- **Power:** the charge is a data component on the ring stack (`rings:charge`), drained once a second by
  `RingPowers` and topped up by `PowerLanternBlock`. `RingHud` draws the meter.
- **Flying pose:** the server also syncs whether the wearer is flying on the ring. Each client eases flying players
  between a hover pose and a superhero pose by their horizontal speed (`RingFlight.java`), reusing the elytra body tilt, and
  `PlayerModelMixin` poses the arms and legs.
