# Rings

A Fabric mod for Minecraft 26.3 that adds a ring slot to the player's inventory, above the offhand slot.
Rings grant powers while switched on with the **Activate Ring** key (default `R`). Every key is rebindable under
Options → Controls → Rings.

| Key | Default | What it does (with the ring on) |
|---|---|---|
| Activate Ring | `R` | Switches the ring on and off |
| Fire Ring Beam | `G` (hold) | Fires the ring's beam |
| Construct Wheel | `V` (hold) | Opens the construct wheel |
| Ring Power | `C` | The ring colour's own power (sneak too, on a pink ring, to go to a friend) |
| Force Field | `B` | Force field on or off; looking at a creature, carries it in a bubble instead |
| Ring Scan | `Z` | Scans what's round about |

**Power rings** come in eight colours: green, yellow, blue, red, pink, orange, white and black. The ring shows on
your right hand whenever it's worn, glowing when it's on. While one is on:
- you can fly, leaving a trail of the ring's energy. You stay upright whichever way you fly. Sprint forward and you
  switch into **elytra mode**: real elytra flight, with its pose and its low view, flying where you look at about
  30 blocks a second, fist out ahead. Let go of forward to drop back into hovering. Coming down to land, you drop
  into a superhero landing: one knee down, fist to the ground;
- **super flight:** hold sneak and forward while flying to blast off where you look, building up to 80 blocks a
  second. Past cruising speed you go sonic: a sonic boom as you break through, a streamlined pose, a white-hot trail,
  rushing wind, a wider view and a shaking camera. Hitting the ground fast sends a shockwave out around your
  landing. Let go and your momentum carries you back into hovering. Sneak on its own still flies down;
- you wear the ring's suit, in its colour with the lantern emblem, under your own head;
- you're outlined in the ring's colour. Like the Glowing effect, the outline shows through walls;
- you glow, lighting up the world around you (brighter while firing the beam), with LambDynamicLights installed;
- the ring's energy shields you: +8 armor and +2 armor toughness, about a full set of iron armor, on top of
  whatever you wear;
- hold **Fire Ring Beam** (default `G`) to fire a beam of the ring's energy from your fist to wherever you look,
  up to 40 blocks. It hits for 2 hearts every half second with knockback, and uses extra power while it fires.

Switching it on, you thrust the ring to the sky. Everyone else sees all of this too.

**Each colour's own power** (the Ring Power key) and gift (always on with the ring):

| Colour | Power | Gift |
|---|---|---|
| Green (will) | A burst of will that strikes and throws back everything round you | Constructs cost a quarter less |
| Yellow (fear) | A wave of terror: creatures flee, weakened and slowed | Hostile creatures that come close panic |
| Red (rage) | Spits burning plasma where you look | Strength, but the ring's armor is halved |
| Blue (hope) | Heals you and every player near you | Green rings near you don't run down |
| Orange (greed) | Calls up copies of creatures you've claimed to fight for you | Hostile creatures you defeat are claimed by the ring (see its tooltip) |
| Pink (love) | Traps the creature you look at in crystal and charms it | Sneak and press the power key to go straight to a friend, anywhere |
| White (life) | Brings back a player who died near you in the last minute, or heals you fully | You slowly heal |
| Black (death) | Drains the life of everything round you into you | What you kill rises as a Black Lantern to fight for you for 2 minutes |

Creatures a ring raises (orange copies, Black Lanterns, construct creatures) fight whatever hurts you or what you
attack, follow you about, and fade away when their time's up or they die, leaving nothing.

**The ring looks after you:**
- **Force field** (`B`): a sphere of the ring's energy round you that takes hits in your place, paid for out of the
  ring's charge. Look at a creature or player when you press it and you blow a bubble round them instead, carrying
  them along beside you wherever you fly, into space too, safe and breathing. They sneak to climb out; press it
  again to let them go.
- **Ring scan** (`Z`): for 15 seconds, creatures within 48 blocks are outlined through walls (hostile red, players
  white, the rest green), ores within 16 blocks glow through the rock in their own colours, and looking at a
  creature reads out its health, armor and ring.
- **A last save:** a hit that would kill you is stopped instead, if the ring has a quarter of its charge to spend.
- **Rings come back:** a dropped ring lying 10 seconds flies back to whoever dropped it, one fallen into the void
  comes straight back, and rings don't burn. When its wearer dies, a ring flies off to choose a new one: the nearest
  player within 64 blocks with no ring. With nobody to choose, it stays on its wearer's finger through death.

**Space:** fly up past Y 450 on a ring and you're in space, over the curve of the Earth, with three planets hanging
in the stars. Fly into one to land on it:

| Planet | What it's like |
|---|---|
| The Moon | Grey dust and stone under a black sky, the Earth hanging over it. A sixth of the Earth's gravity. No air. |
| Mars | Red sand and rock under a butterscotch sky. 38% of the Earth's gravity. No air. |
| Oa | Home of the Lantern Corps: green land under a green sky, with the violet giant Korugar overhead. Breathable. |

Fly back up past Y 400 to return to space, and down into the Earth to come home where you left. In space and on
airless planets only an active ring keeps you breathing: if it goes out, your air runs out like underwater.
Low gravity means higher jumps, slower falls and softer landings. Beds and respawn anchors don't work off the Earth.

**Oa** has the Guardians' citadel at its middle, right where you land: a great plaza ringed by towers, with the
**Central Power Battery** at its heart. Right-click its glowing core and any ring, of any colour, is charged full at
once. The **Guardians of the Universe** walk the plaza: right-click one to hear what they know (a visitor with no
ring is given one, once).

**Enemies among the stars:**
- **Manhunters**, androids that fly and shoot bolts of energy, hunt players in space and on the Moon and Mars.
- **Parallax**, fear itself, is sealed in the Central Power Battery. A yellow ring's touch sets it free: a vast
  flying serpent that spits fear, swoops, slows everyone near and saps their rings. A yellow ring can't hurt it.
  It drops a yellow lantern, diamonds and gold.
- **Nekron**, lord of the dead, sleeps on the Moon. Charge a black ring full at a black lantern there and he
  wakes: a towering skeleton with a scythe who raises the dead, hurls bolts of death and steps through the shadows
  to whoever runs. A black ring can't hurt him, and a white ring hurts him three times over. He drops a white ring,
  a white lantern and diamonds.

**Constructs:** hold **Construct Wheel** (default `V`) while the ring is on, point at a construct and let go (or
click) to make it out of the ring's light, in its colour. Each costs a little power (the wheel shows how much).

| Construct | What it makes |
|---|---|
| Wall | A 5×4 wall of hard light in front of you, for 30 seconds |
| Bridge | A bridge 3 wide and 16 long from your feet, for a minute |
| Cage | A box of hard light around the creature you look at, for 15 seconds |
| Giant Fist | A fist that flies where you look and smashes whatever it hits, throwing everything near it back |
| Stairs | A staircase of hard light 3 wide, 12 steps up, for 45 seconds |
| Dome | A dome of hard light over you, 4 blocks out, for 20 seconds |
| Grab | A chain of light that yanks the creature you look at to you |
| Giant Hammer | A hammer brought down where you look, its shockwave throwing everything near it up |
| Sword | A diamond-strength sword in your hand |
| Axe, Pickaxe, Shovel | Netherite-speed tools |
| Wolf | A wolf of light that fights for you, for a minute |
| Golem | A golem of light, slow and mighty, that fights for you for 45 seconds |
| Jet | A jet of light with you at the controls: it flies where you look while you hold forward (faster sprinting), seats three friends (right-click to climb in), and flies to space like the ring does |

Hard light only fills empty space, breaks no blocks and drops nothing. Construct tools last while the ring stays on
(up to 5 minutes) and fade away if dropped.

**Power:** the ring runs on its charge, shown by the meter to the right of the hotbar and the bar on the item. A
full ring lasts 30 minutes switched on, 10 minutes of flying, about 4 minutes of super flight, or about 6 minutes
of firing the beam. When it runs out the ring switches off, and it
won't switch back on until you charge it: **hold right-click on a Power Lantern of the ring's colour** while
wearing or holding the ring (about 6 seconds from empty), as its energy pours into the ring. While it charges you take the lantern oath: braced,
ring fist thrust at the lantern and the other hand gripping the wrist, pulsing with each surge as energy spirals up
around you, as the colour's oath appears line by line across your screen (each colour has its own, in the
language file as `oath.rings.<colour>.1` to `.4`). When the ring is full the power bursts out: a shockwave along the ground as you throw your fist to the
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
| Yellow | Block of Gold | Glowstone Dust |

How it behaves:
- Shift-click a ring to equip it, like armor. Taking it off switches it off.
- Switching it off in mid-air gives 5 seconds of slow falling so you don't fall to your death.
- Dying switches the ring off (and it may go to choose a new wearer, above).
- Creative and spectator players keep their own flight when the ring turns off.

It's needed on both the server and every player's client. Players should also install two client-only libraries,
which the join scripts install from `mods.txt`:
- [Player Animation Library](https://modrinth.com/mod/player-animation-library) plays the activation, charging
  and landing animations. Without it the ring works the same, without those animations.
- [Bendable Cuboids](https://modrinth.com/mod/bendable-cuboids) bends knees and elbows in them. Without it they
  play with straight limbs.
- [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) makes suited-up players light up their
  surroundings. Without it they don't.

When a newer Rings is out than the one running, the title, multiplayer and disconnected screens show a notice to
close the game and run join again, which installs it.

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
| `python tools/gen_worlds.py` | Writes space and the planets (dimensions, terrain, skies) and their maps |
| `RINGS_SPACE=1 ./gradlew runClientGameTest` | Only flies to space and each planet and back, filming each |
| `RINGS_FLIGHT=1 ./gradlew runClientGameTest` | Only films the ring on the hand, elytra mode and super flight |
| `RINGS_POWERS=1 ./gradlew runClientGameTest` | Only the force field, bubble, scan, the last save and rings coming back |
| `RINGS_COLORS=1 ./gradlew runClientGameTest` | Only each colour's power and gift |
| `RINGS_CREATURES=1 ./gradlew runClientGameTest` | Only the construct wolf, golem and jet |
| `RINGS_OA=1 ./gradlew runClientGameTest` | Only Oa, the Guardians, Manhunters, Parallax and Nekron |
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
  between their poses (`RingFlight.java`), and `PlayerModelMixin` poses the arms and legs. Elytra mode is vanilla's
  real gliding, which `RingGlide` and `PlayerMixin` allow without an elytra while a ring is on.
- **Beam:** the key sends a packet when it goes down and up, and the server sets a firing attachment that syncs to
  every client. `RingBeam` traces the line of sight (on the server for hits, on each client to draw it) and
  `RingBeams` draws it as camera-facing strips in the world, with sparks and a hum.
- **Elytra mode and super flight:** movement is the client's to decide, so `SuperFlight` switches the local player
  into gliding and moves them in place of vanilla's movement (`PlayerTravelMixin`); everyone else sees it through the usual movement updates, and
  `RingFlight` picks each player's pose and effects from how fast and which way they move. The server only charges
  the extra power, from the movement the client reports.
- **Animations:** `RingAnimations` registers a Player Animation Library layer on every player. The server sends
  each nearby client a packet when a ring switches on or charges; landing is spotted by each client from the
  synced flying flag. The layer only moves the bones an animation keys, on top of the vanilla and flying poses.
- **Colour powers:** `ColorPowers` (the power key and gifts), `Thralls` (creatures raised to fight for a wearer: real
  vanilla creatures whose target goals are swapped for the ring's), `MobMoods` (fear and charm) and `EnergyBolt`.
- **Force field, scan, saves and returns:** `RingAbilities`, `ForceBubble`, `RingProtection`, `RingReturn`, and on
  the client `ForceFields`, `EnergySphere` and `RingScan` (ores are drawn with a see-through render pipeline).
- **Oa and its foes:** `OaCitadel` builds the citadel on the first visit (once, marked on the level), `Bosses`
  spawns Manhunters and wakes Parallax and Nekron; `SpaceFlyer` is the flying base for Manhunters and Parallax.
