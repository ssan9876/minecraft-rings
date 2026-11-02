# Rings

A Fabric mod for Minecraft 26.3 that adds a ring slot to the player's inventory, above the offhand slot.
Rings grant powers while switched on with the **Activate Ring** key (default `R`). Every key is rebindable under
Options → Controls → Rings.

Rings can't be crafted. The only way to get one is from a ring-bearer dying beside their crashed ship, and those
crashes are very rare (see [Getting a ring](#getting-a-ring)). A new bearer starts weak and grows stronger with the
ring, unlocking its powers on a skill tree (see [Ring levels and the skill tree](#ring-levels-and-the-skill-tree)).

| Key | Default | What it does (with the ring on) |
|---|---|---|
| Activate Ring | `R` | Switches the ring on and off |
| Fire Ring Beam | `G` (hold) | Fires the ring's beam |
| Construct Wheel | `V` (hold) | Opens the construct wheel |
| Ring Power | `C` | The ring colour's own power (sneak too, on a pink ring, to go to a friend) |
| Force Field | `B` | Force field on or off; looking at a creature or player, carries them in a bubble instead |
| Ring Scan | `Z` | Scans what's round about |
| Call Ring | `H` | Calls your rings back to you from wherever they are (works with no ring on) |
| Choose Suit | `J` | Picks which suit your rings dress you in (works with no ring on) |
| Skill Tree | `K` | Shows your ring level and the skill tree, where you spend skill points (works with no ring on) |

Most of these powers have to be learned on the skill tree first. Below, each one that does says which skill it needs.

## Getting a ring

**Crash sites.** Very rarely, a ring-bearer's ship lies crashed somewhere on the Earth: a scorched crater with a furrow
ploughed in behind it, the wreck nosed into the middle (alien hull split open on one side, its canopy cracked, a wing
torn off and lying in the furrow, the engine still glowing), and columns of smoke rising from its fires that can be
seen from far off. They're generated with new ground, on dry land, about one in every 2,400 chunks.

**Falling ships.** Worlds explored before crash sites existed get them too: every so often (about once in two or three
hours of play) a burning ship streaks down out of the sky somewhere 100 to 200 blocks from a player who's out under the
open sky, and everyone near is told which way it came down and how far off. Only one fallen ship waits at a time; the
next can't fall until its pilot's ring has been taken.

**The pilot.** Beside the breach in the hull sits the dying pilot, slumped against the wreck with their ring in their
hand and their lantern beside them. Each colour's pilot is of a different people: the green ring's bearer is **Abin
Sur**. When you come near, the ship's computer warns them you're coming and they call out. Right-click them to talk:
they tell you who they are, what brought their ship down and what the ring is, and offer it to you. Take it and they
give you their ring (low on charge after the crash, and bound to you) and their lantern, then pass, fading into the
ring's light. A player who already wears or carries a ring can't take another.

**The ship's console**, inside the wreck, holds the flight recorder: right-click it to read the ship's last log
entries, and once the ring has found its new bearer, the final one.

The colours come down about this often: green 4 in 11, and blue, red, pink, orange, white, black and yellow 1 in 11
each. The Guardians on Oa no longer hand out rings, and Nekron no longer drops one.

## Ring levels and the skill tree

Everything you do with your ring on earns it **ring experience**: wearing it switched on and flying on it, hitting
hostile creatures with the beam, defeating creatures (more for tougher ones: a Manhunter 25, Parallax and Nekron 400),
making constructs and items, using your colour's power, bubbles and the scan, and charging the ring at its
lantern. Enough of it and the ring goes up a **level**, up to level 40; each level gives a **skill
point**. Press **Skill Tree** (`K`) to see your level, your progress to the next and the skill tree, and click a skill
to learn it. The meter by the hotbar shows your level, with a star when there are points to spend.

Experience and skills are yours, not the ring's: they stay with you whatever ring you wear, and through death. Players
who already had a ring when levels came in (1.13.0) start at level 10.

A new bearer can switch the ring on, hover, fire the beam (weakly), make walls, bridges, stairs, fists and grabs,
charge the ring and call it back, and the ring gives +4 armor. Each level makes hovering a little faster, up to nearly
twice vanilla's flight. Everything else is learned. Each skill needs the one above it, and some need a level first:

| Branch | Skill | Points | From level | What it does |
|---|---|---|---|---|
| Flight | Soaring | 1 | 1 | Elytra mode: sprint forward while flying |
| | Super Flight | 2 | 4 | Hold sneak in elytra mode to blast off, up to 50 blocks a second |
| | Sonic Flight | 2 | 12 | Super flight up to 80 blocks a second, breaking the sound barrier |
| | Spacefarer | 2 | 8 | Fly up into space (without it the ring can't break out of the sky, though a construct jet can carry you) |
| Power | Focused Beam | 1 | 1 | The beam hits for 2 hearts (1¼ before) |
| | Lance | 1 | 6 | 3 hearts |
| | Annihilator | 2 | 15 | 4½ hearts, and sets what it hits alight |
| | Emotional Spectrum | 1 | 2 | Your colour's power, on the power key |
| | Spectrum Mastery | 2 | 10 | The colour's power costs 40% less and reaches half as far again |
| Constructs | Shaper | 1 | 1 | The dome, cage, giant hammer, axe, pickaxe and shovel |
| | Armory | 1 | 3 | The sword and shield |
| | Living Light | 2 | 6 | The wolf and golem |
| | Enduring Constructs | 1 | 5 | Walls, bridges, stairs, domes, cages and creatures last twice as long |
| | Starship | 1 | 10 | The jet |
| | Matter Mastery | 3 | 14 | Construct any item (the eye in the middle of the wheel) |
| Defence | Hard-Light Skin | 1 | 1 | The ring's armor goes to +8, with +2 toughness |
| | Guardian Angel | 2 | 5 | The last save from a fatal hit |
| | Fortress | 2 | 12 | +12 armor, +4 toughness, and half the knockback |
| | Force Field | 1 | 2 | The force field round you |
| | Bubbles | 1 | 4 | Carry creatures and players in bubbles |
| | Aegis | 1 | 9 | The force field and bubbles take hits for half the charge |
| Will | Ring Scan | 1 | 1 | The scan |
| | Deep Scan | 1 | 6 | The scan reaches 80 blocks for creatures and 28 for ores |
| | Efficiency | 1 | 3 | Everything the ring does uses a quarter less charge |
| | Willpower | 2 | 10 | Everything the ring does uses half the charge |
| | Lantern Bond | 1 | 8 | Lanterns charge your ring twice as fast |

The whole tree takes 37 points, so it's level 37 before a bearer has learned everything. Locked constructs are dimmed
on the construct wheel, and trying a power you haven't learned tells you which skill it needs.

## The rings

**Power rings** come in eight colours: green, yellow, blue, red, pink, orange, white and black. The ring shows on
your right hand whenever it's worn, glowing when it's on. While one is on:
- you can fly, leaving a trail of the ring's energy. You stay upright whichever way you fly. Sprint forward and you
  switch into **elytra mode**: real elytra flight, with its pose and its low view, flying where you look at about
  30 blocks a second, fist out ahead. Let go of forward to drop back into hovering. Coming down to land, you drop
  into a superhero landing: one knee down, fist to the ground;
- **super flight:** hold sneak and forward while flying to blast off where you look, building up to 80 blocks a
  second. Past cruising speed you go sonic: a sonic boom as you break through, a streamlined pose, a white-hot trail,
  rushing wind, a wider view and a shaking camera. Hitting the ground fast sends a shockwave out around your
  landing. Let go and your momentum carries you back into hovering. Holding sneak to boost doesn't crouch you.
  Sneak on its own still flies down;
- you wear the ring's suit, in its colour with the lantern emblem, under your own head. Press **Choose Suit** (`J`)
  to pick which: **Classic** (black shoulders and sleeves over the ring's colour), **Shadow** (black, lit by the
  ring's colour at the emblem, stripes, gloves and boots), **Armored** (plates of the colour with metal pauldrons,
  belt, knee guards and gauntlets), **Robed** (a long tunic with a sash over black trousers), or **No suit**, keeping
  your own skin with just the glowing outline. More are earned: **Veteran** at ring level 5 (the classic suit,
  battle-worn, with pauldrons, rank chevrons and knee pads), **Honor Guard** at 10 (a high collar, gold trim and a
  cape), **Starfield** at 15 (the night sky, full of stars, traced with the ring's light), **Sentinel** at 20 (heavy
  armour plates in a steel frame), **Paragon** at 30 (light pouring down from the shoulders, gold and white, with a
  cape), and the **Fallen Pilot's Uniform**, for taking a ring from a dying ring-bearer. The suit screen shows what
  each locked one needs. Your choice is kept, for every ring you wear;
- you're outlined in the ring's colour. Like the Glowing effect, the outline shows through walls;
- you glow, lighting up the world around you (brighter while firing the beam), with LambDynamicLights installed;
- the ring's energy shields you: +4 armor at first, on top of whatever you wear; +8 and +2 toughness (about a full
  set of iron armor) with Hard-Light Skin, and +12 and +4 toughness with Fortress;
- hold **Fire Ring Beam** (default `G`) to fire a beam of the ring's energy from your fist to wherever you look,
  up to 40 blocks. It hits every half second with knockback, for 1¼ hearts at first and up to 4½ with the beam
  skills, and uses extra power while it fires.

Switching it on, you dip and then thrust the ring to the sky. Making a construct, you draw your fist back and lunge;
using the colour's power, you gather your fists and fling them wide; putting up the force field, you cross your arms
and throw them apart; scanning, you raise your hand to your brow and look the world over. The ring's energy shows as
glowing motes in its colour that flare up, twinkle and fade, and shine even in the dark. Everyone else sees all of
this too.

**Each colour's own power** (the Ring Power key, once you've learned Emotional Spectrum) and gift (always on with the
ring):

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
- **Force field** (`B`, needs Force Field; bubbles need Bubbles): a sphere of the ring's energy round you that takes hits in your place, paid for out of the
  ring's charge. Look at a creature or player when you press it and you blow a bubble round them instead, carrying
  them along beside you wherever you fly, into space too, safe and breathing. They sneak to climb out; press it
  again to let them go.
- **Ring scan** (`Z`, needs Ring Scan): for 15 seconds, creatures within 48 blocks are outlined through walls (hostile red, players
  white, the rest green), ores within 16 blocks glow through the rock in their own colours, and looking at a
  creature reads out its health, armor and ring.
- **A last save** (needs Guardian Angel): a hit that would kill you is stopped instead, if the ring has a quarter of its charge to spend.
- **Rings come back:** a dropped ring lying 10 seconds flies back to whoever dropped it, one fallen into the void
  comes straight back, and rings don't burn. When its wearer dies, a ring flies off to choose a new one: the nearest
  player within 64 blocks with no ring. With nobody to choose, it stays on its wearer's finger through death.
- **Call your ring** (`H`): a ring is bound to the first player to wear it (or the heir it chooses), and its tooltip
  says who. Press the call key and every ring bound to you that isn't on you comes: out of chests, barrels, hoppers
  and any other container, out of shulker boxes and bundles inside those, item frames, armor stands, mobs' hands,
  chest minecarts, and other players' inventories, ring slots and ender chests. It bursts out and flies to you
  through whatever's in the way; from more than 96 blocks off or another world, it streaks in out of the sky. A ring
  left where nobody is (its chunk unloaded) is found where it was last seen: that spot wakes up for a few seconds
  while the ring answers.

**Space:** fly up past Y 450 on a ring (once you've learned Spacefarer) and you're in space, over the curve of the Earth, with three planets hanging
in the stars and the sun blazing beyond them (always out of reach; fly at it all you like). The sun shines in every
planet's sky too, in place of the Overworld's sun and moon. Fly into a planet to land on it:

| Planet | What it's like |
|---|---|
| The Moon | Grey dust and stone under a black sky, the Earth hanging over it. A sixth of the Earth's gravity. No air. |
| Mars | Red sand and rock under a butterscotch sky. 38% of the Earth's gravity. No air. |
| Oa | Home of the Lantern Corps: green land under a green sky, with the violet giant Korugar overhead. Breathable. |

Each planet is made of its own rock, with its own things to find:

| Planet | Ground | To find |
|---|---|---|
| The Moon | Moon Dust over Moon Rock | Craters, with moonstone glinting in their floors; Moon Iron Ore; Moonstone Ore, which drops moonstone (nine make a glowing Block of Moonstone) |
| Mars | Red sand over Mars Rock | Tall banded rock spires; Mars Iron Ore, rich in iron (2 to 4 raw iron); Mars Gold Ore; geodes of glowing Mars Crystal; ice frozen in the rock |
| Oa | Green ground over Oan Stone | Glowing Oan Crystals, on the surface and in caves; Oan Emerald Ore; Will Crystal Ore, which drops Will Shards |

A **Will Shard** charges the ring you wear by a quarter, whatever its colour: right-click with it. Every planet rock
can be crafted (2×2) or stonecut into bricks. Ores need the same tools as vanilla's (stone for iron, iron for the
rest), and fortune and silk touch work on them. The ring scan shows the new ores too. Ground that was explored before
1.10.0 keeps its old blocks; new ground has the new ones.

Fly back up past Y 400 to return to space, and down into the Earth to come home where you left. In space and on
airless planets only an active ring keeps you breathing: if it goes out, your air runs out like underwater.
Low gravity means higher jumps, slower falls and softer landings. Beds and respawn anchors don't work off the Earth.

**Oa** has the Guardians' citadel at its middle, right where you land: a great plaza ringed by towers, with the
**Central Power Battery** at its heart. Right-click its glowing core and any ring, of any colour, is charged full at
once. The **Guardians of the Universe** walk the plaza: right-click one to hear what they know.

**Enemies among the stars:**
- **Manhunters**, androids that fly and shoot bolts of energy, hunt players in space and on the Moon and Mars.
- **Parallax**, fear itself, is sealed in the Central Power Battery. A yellow ring's touch sets it free: a vast
  flying serpent that spits fear, swoops, slows everyone near and saps their rings. A yellow ring can't hurt it.
  It drops a yellow lantern, diamonds and gold.
- **Nekron**, lord of the dead, sleeps on the Moon. Charge a black ring full at a black lantern there and he
  wakes: a towering skeleton with a scythe who raises the dead, hurls bolts of death and steps through the shadows
  to whoever runs. A black ring can't hurt him, and a white ring hurts him three times over. He drops a white
  lantern, will shards and diamonds.

**Constructs:** hold **Construct Wheel** (default `V`) while the ring is on, point at a construct and let go (or
click) to make it out of the ring's light, in its colour. Each costs a little power (the wheel shows how much). Every
bearer can make the wall, bridge, stairs, fist and grab; the rest are learned on the skill tree (the wheel dims those
you haven't, and says which skill they need).

**Any item** (needs Matter Mastery): click the eye in the middle of the wheel and type an item's name, then click an item for one or
shift-click for a stack. It's a real, lasting item, paid for out of the ring's charge: 0.5% each for common things,
2% for gold, emerald and uncommon items, 7.5% for diamond and rare ones, 20% for netherite and epic ones. Operator
blocks (command and structure blocks, barriers, light, the debug stick) and the rings themselves can't be made; the
list is the `rings:not_constructable` item tag, which a data pack can change.

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
| Sword | A diamond-strength sword in your hand. Right-click to send a crescent of light flying off the blade, up to 19 blocks, cutting everything in its path (7 damage, a little of the ring's charge). With a shield in the other hand, sneak and right-click instead |
| Shield | A shield of light on your off hand: it blocks straight away, axes can't knock it down, and whatever hits it up close is thrown back |
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
language file as `oath.rings.<colour>.1` to `.4`, so a resource pack can swap in other words: a pack with
`assets/rings/lang/en_us.json` setting those keys). When the ring is full the power bursts out: a shockwave along the ground as you throw your fist to the
sky. A lantern of another colour won't charge it.

**Power Lanterns** stand on a stepped plinth: a barrel of etched glass with the lantern emblem, held in metal ribs
and bands of the colour, with a living, pulsing core of light inside that lights up the room, and motes of energy
rising off the top. Rings can't be crafted, but more lanterns can (shaped):

```
Power Lantern:   Iron Ingot  Side     Iron Ingot
                 Side        Lantern  Side
                 Iron Ingot  Side     Iron Ingot
```

| Colour | Lantern sides |
|---|---|
| Green | Emerald |
| Blue | Lapis Lazuli |
| Red | Redstone Dust |
| Pink | Amethyst Shard |
| Orange | Copper Ingot |
| White | Nether Quartz |
| Black | Obsidian |
| Yellow | Glowstone Dust |

**For operators:** `/rings level <players> <level>`, `/rings xp <players> <amount>`, `/rings unlockall <players>`
(every skill and suit), `/rings reset <players>` (back to a new bearer), `/rings info <player>`, `/rings crash
[colour]` (a ship falls 40 to 70 blocks from you) and `/rings crashsite [colour]` (a crash site built right where you
stand).

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
| `python tools/gen_textures.py` | Redraws every texture: each colour's ring, suits, capes, lantern and crashed pilot, constructs, particles, the ships' and the planets' blocks |
| `python tools/gen_resources.py` | Writes every colour's models, recipes, loot tables and names, the planets' blocks', the ships' blocks' and the crash sites' worldgen |
| `python tools/gen_worlds.py` | Writes space and the planets (dimensions, terrain, skies, ores and features) and their maps |
| `RINGS_SPACE=1 ./gradlew runClientGameTest` | Only flies to space and each planet and back, filming each |
| `RINGS_FLIGHT=1 ./gradlew runClientGameTest` | Only films the ring on the hand, elytra mode and super flight |
| `RINGS_POWERS=1 ./gradlew runClientGameTest` | Only the force field, bubble, scan, the last save and rings coming back |
| `RINGS_COLORS=1 ./gradlew runClientGameTest` | Only each colour's power and gift |
| `RINGS_CREATURES=1 ./gradlew runClientGameTest` | Only the construct wolf, golem and jet |
| `RINGS_OA=1 ./gradlew runClientGameTest` | Only Oa, the Guardians, Manhunters, Parallax and Nekron |
| `RINGS_POSES=1 ./gradlew runClientGameTest` | Only films the ring animations, from four sides, for tuning them |
| `RINGS_GEAR=1 ./gradlew runClientGameTest` | Only the construct sword's slash, the construct shield and a will shard |
| `RINGS_SUITS=1 ./gradlew runClientGameTest` | Only choosing each suit, on the suit screen and from the front |
| `RINGS_MASTERY=1 ./gradlew runClientGameTest` | Only ring levels, the skill tree, the earned suits, a crash site and its pilot's dialogue, the console, the lanterns and a falling ship |

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
- **Force field, scan, saves, returns and calls:** `RingAbilities`, `ForceBubble`, `RingProtection`, `RingReturn`,
  `RingCall` (the bond component, the last-seen map kept on the Overworld, and the search), and on
  the client `ForceFields`, `EnergySphere` and `RingScan` (ores are drawn with a see-through render pipeline).
- **Particles:** `RingColor.dust` makes a `RingParticles.Energy` mote (registered with its colour and size);
  `EnergyParticle` draws it full-bright, growing then shrinking through a soft-orb-to-star sprite set.
- **Construct weapons:** `ConstructSword` and `SwordWave` (the slash, moved on and cut every server tick); the
  shield is a plain construct item with vanilla's `blocks_attacks` component, and `LivingEntityMixin` throws back
  what it blocks.
- **Planets' blocks:** `PlanetBlocks` (blocks and items), `PlanetFeatures` (craters, spires and crystal patches,
  as worldgen feature types), placed by the planets' biome files that `tools/gen_worlds.py` writes.
- **Levels and skills:** `Mastery` (the player's experience and skills, a synced attachment, and what each skill
  changes), `Skill` (the tree), `SkillTreeScreen`. Each power checks its skill on the server (`Mastery.require`); the
  client checks too for what it decides itself (elytra mode and super flight in `SuperFlight`, the wheel, the scan's
  reach).
- **Crash sites:** `CrashSite` builds one (the world generates them through its `Feature`, added to every Overworld
  land biome in `Rings`), `Crashes` sends ships falling out of the sky, `CrashPilot` is the dying pilot,
  `ShipBlocks` the wreck's blocks and console, and `Dialogues` what the pilot and the console say (the client walks
  the dialogue in `DialogueScreen`; only taking the ring goes to the server). `RingCommands` is `/rings`.
- **Oa and its foes:** `OaCitadel` builds the citadel on the first visit (once, marked on the level), `Bosses`
  spawns Manhunters and wakes Parallax and Nekron; `SpaceFlyer` is the flying base for Manhunters and Parallax.
