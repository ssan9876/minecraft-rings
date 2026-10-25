# Rings

A Fabric mod for Minecraft 26.3 that adds a ring slot to the player's inventory, above the offhand slot.
Rings grant powers while switched on with the **Activate Ring** key (default `R`). Both keys are rebindable under
Options → Controls → Rings.

**Power rings** come in seven colours: green, blue, red, pink, orange, white and black. While one is on:
- you can fly. Flying fast you tip into a superhero pose (fist out front, banking into turns) and leave a trail
  of the ring's energy. Flying straight up or down, or hovering, you stay upright. Coming down to land, you drop
  into a superhero landing: one knee down, fist to the ground;
- **super flight:** hold sneak and forward while flying to blast off where you look, building up to 40 blocks a
  second. Near top speed you go sonic: a sonic boom as you break through, a streamlined pose, a white-hot trail,
  rushing wind, a wider view and a shaking camera. Rocketing straight up you raise your fist to the sky, diving
  down you go head first, and hitting the ground fast sends a shockwave out around your landing. Let go and your
  momentum carries you back into normal flight. Sneak on its own still flies down;
- you wear the ring's suit, in its colour with the lantern emblem, under your own head;
- you're outlined in the ring's colour. Like the Glowing effect, the outline shows through walls;
- the ring's energy shields you: +8 armor and +2 armor toughness, about a full set of iron armor, on top of
  whatever you wear;
- hold **Fire Ring Beam** (default `G`) to fire a beam of the ring's energy from your fist to wherever you look,
  up to 40 blocks. It hits for 2 hearts every half second with knockback, and uses extra power while it fires.

Switching it on, you thrust the ring to the sky. Everyone else sees all of this too.

**Power:** the ring runs on its charge, shown by the meter to the right of the hotbar and the bar on the item. A
full ring lasts 30 minutes switched on, 10 minutes of flying, about 4 minutes of super flight, or about 6 minutes
of firing the beam. When it runs out the ring switches off, and it
won't switch back on until you charge it: **hold right-click on a Power Lantern of the ring's colour** while
wearing or holding the ring (about 2 seconds from empty). While it charges you take the lantern oath: braced,
ring fist thrust at the lantern and the other hand gripping the wrist, pulsing with each surge as energy spirals up
around you. When the ring is full the power bursts out: a shockwave along the ground as you throw your fist to the
sky. A lantern of another colour won't charge it.

Crafting (shaped). Every colour uses the same pattern with its own material:

```
Power Ring:      Phantom Membrane  Core        Phantom Membrane
                 Gold Ingot        ·           Gold Ingot
                 ·                 Gold Ingot  ·

Power Lantern:   Iron Ingot  Side     Iron Ingot
                 Side        Lantern  Side
                 Iron Ingot  Side     Iron Ingot
```

| Colour | Ring core | Lantern sides |
|---|---|---|
| Green | Diamond | Emerald |
| Blue | Block of Lapis Lazuli | Lapis Lazuli |
| Red | Block of Redstone | Redstone Dust |
| Pink | Block of Amethyst | Amethyst Shard |
| Orange | Block of Copper | Copper Ingot |
| White | Block of Quartz | Nether Quartz |
| Black | Obsidian | Obsidian |

How it behaves:
- Shift-click a ring to equip it, like armor. Taking it off switches it off.
- Switching it off in mid-air gives 5 seconds of slow falling so you don't fall to your death.
- The ring drops on death like the rest of your inventory (kept with `keep_inventory`). Dying switches it off.
- Creative and spectator players keep their own flight when the ring turns off.

It's needed on both the server and every player's client. Players should also install two client-only libraries,
which the join scripts install from `mods.txt`:
- [Player Animation Library](https://modrinth.com/mod/player-animation-library) plays the activation, charging
  and landing animations. Without it the ring works the same, without those animations.
- [Bendable Cuboids](https://modrinth.com/mod/bendable-cuboids) bends knees and elbows in them. Without it they
  play with straight limbs.

## Develop

Needs JDK 25.

| Command | What it does |
|---|---|
| `./gradlew build` | Builds `build/libs/rings-<version>.jar` |
| `./gradlew runClient` | Starts Minecraft with the mod, in singleplayer |
| `./gradlew runClientGameTest` | Opens a real client and plays through the ring (`src/gametest`). Screenshots land in `build/run/clientGameTest/screenshots` |
| `./release.sh` | Publishes the version in `gradle.properties` as a GitHub release and points `mods.txt` at it |
| `./deploy.sh` | Builds and installs the mod on the server, then restarts it |
| `python tools/gen_textures.py` | Redraws every colour's ring, suit and lantern textures, and the slot icon |
| `python tools/gen_resources.py` | Writes every colour's models, recipes, loot tables and names |
| `RINGS_POSES=1 ./gradlew runClientGameTest` | Only films the ring animations, from four sides, for tuning them |

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

## Adding a colour

1. Add it to `RingColor.java` (its ids, energy colour and map colour).
2. Add it to `COLORS` in `tools/gen_textures.py` (its palette) and `tools/gen_resources.py` (its name and
   crafting materials), then run both.

Powers live in `RingPowers.java`, which runs every server tick for each player. The animations are keyframes in
`assets/rings/player_animations/rings.json` (Blockbench's Bedrock format, which Blockbench can open and edit).

## How it works

- **Ring slot:** a mixin adds a slot to the player's inventory menu (index 46, after the vanilla slots). The ring is
  stored in a Fabric data attachment on the player, so it saves with the player and survives relogging.
- **Activation:** the key sends a packet; the server toggles the ring and grants or removes powers.
- **Costume:** the server sets a costume attachment that syncs to every client. The player renderer swaps in the
  costume's skin and cape while it's set, and outlines the player in the ring's colour. The costume model hides its head;
  `OwnHeadLayer` draws it with the player's own skin using a second, head-only player model.
- **Power:** the charge is a data component on the ring stack (`rings:charge`), drained once a second by
  `RingPowers` and topped up by `PowerLanternBlock`. `RingHud` draws the meter.
- **Flying pose:** the server also syncs whether the wearer is flying on the ring. Each client eases flying players
  between a hover pose and a superhero pose by their horizontal speed (`RingFlight.java`), reusing the elytra body tilt, and
  `PlayerModelMixin` poses the arms and legs.
- **Beam:** the key sends a packet when it goes down and up, and the server sets a firing attachment that syncs to
  every client. `RingBeam` traces the line of sight (on the server for hits, on each client to draw it) and
  `RingBeams` draws it as camera-facing strips in the world, with sparks and a hum.
- **Super flight:** movement is the client's to decide, so `SuperFlight` moves the local player in place of
  vanilla's flying movement (`PlayerTravelMixin`); everyone else sees it through the usual movement updates, and
  `RingFlight` picks each player's pose and effects from how fast and which way they move. The server only charges
  the extra power, from the movement the client reports.
- **Animations:** `RingAnimations` registers a Player Animation Library layer on every player. The server sends
  each nearby client a packet when a ring switches on or charges; landing is spotted by each client from the
  synced flying flag. The layer only moves the bones an animation keys, on top of the vanilla and flying poses.
