package xyz.ssander.rings.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.CrashSite;
import xyz.ssander.rings.Crashes;
import xyz.ssander.rings.Devourer;
import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.Mastery;
import xyz.ssander.rings.Missions;
import xyz.ssander.rings.OaCitadel;
import xyz.ssander.rings.OaGuardian;
import xyz.ssander.rings.PlanetBlocks;
import xyz.ssander.rings.PowerLanternBlock;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingPowers;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.Skill;
import xyz.ssander.rings.Space;
import xyz.ssander.rings.client.DialogueScreen;
import xyz.ssander.rings.client.SkillTreeScreen;

import static xyz.ssander.rings.gametest.RingsClientGameTest.activate;
import static xyz.ssander.rings.gametest.RingsClientGameTest.camera;
import static xyz.ssander.rings.gametest.RingsClientGameTest.check;
import static xyz.ssander.rings.gametest.RingsClientGameTest.checkClient;
import static xyz.ssander.rings.gametest.RingsClientGameTest.player;

/**
 * Reasons to go places, and things to do with other players: Oa's doubled experience, the Guardians' missions and
 * respec, the Moon's shrines and Mars's wrecks, Oan relics, attuned lanterns and the oath's charge bar, the Devourer,
 * duels and ranks, skill previews, crash colours by biome and how often ships fall. Run on its own with
 * RINGS_WORLDS=1.
 */
final class WorldsTest {
	private WorldsTest() {
	}

	static void run(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamerule spawn_mobs false");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mastery.reset(p);
			RingAttachments.setRing(p, new ItemStack(Rings.ring(RingColor.GREEN)));
		});
		server.runCommand("rings level @a 20");
		activate(context, connection);

		crashColors(context, server);
		skillPreview(context, connection, server);
		lanterns(context, connection, server);
		ranksAndDuels(context, connection, server);
		oa(context, connection, server);
		ruins(context, connection, server);
		devourer(context, connection, server);
	}

	private static void crashColors(ClientGameTestContext context, TestServerContext server) {
		check(server, p -> CrashSite.special(p.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT)) == RingColor.YELLOW,
				"yellow bearers fall in deserts");
		check(server, p -> CrashSite.special(p.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SNOWY_PLAINS)) == RingColor.WHITE,
				"white bearers fall in the snow");
		check(server, p -> CrashSite.special(p.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DARK_FOREST)) == RingColor.BLACK,
				"black bearers fall in dark forests");
		check(server, p -> CrashSite.special(p.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)) == null,
				"plains get only the common colours");
		check(server, p -> Crashes.every(p.level().getServer()) == Crashes.DEFAULT_EVERY, "ships fall every 150 minutes by default");
		server.runCommand("rings crashes every 30");
		check(server, p -> Crashes.every(p.level().getServer()) == 30, "/rings crashes every sets how often ships fall");
		server.runCommand("rings crashes off");
		check(server, p -> Crashes.every(p.level().getServer()) == 0, "/rings crashes off stops them");
	}

	/** Hovering a skill shows the player acting it out. */
	private static void skillPreview(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		context.setScreen(SkillTreeScreen::new);
		context.waitTicks(3);
		// Over Force Field (column 8, row 0)
		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		int width = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int height = context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
		int left = (width - Skill.COLUMNS * 36) / 2;
		int top = Math.max(4, (height - 50 - Skill.ROWS * 42 - 26) / 2);
		context.getInput().setCursorPos((left + Skill.FORCE_FIELD.x() * 36 + 18) * scale, (top + 50 + 11) * scale);
		context.waitTicks(12);
		context.takeScreenshot("worlds-1-skill-preview");
		context.setScreen(() -> null);
	}

	/** A lantern attuned with a Mars crystal charges any colour; the oath shows how full the ring is. */
	private static void lanterns(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("setblock 0 -60 2 rings:power_lantern");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos pos = new BlockPos(0, -60, 2);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PlanetBlocks.MARS_CRYSTAL.asItem()));
			level.getBlockState(pos).useItemOn(p.getMainHandItem(), level, p, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
			RingAttachments.setRing(p, new ItemStack(Rings.ring(RingColor.RED)));
			RingAttachments.getRing(p).set(xyz.ssander.rings.RingComponents.CHARGE, 100);
		});
		check(server, p -> p.level().getBlockState(new BlockPos(0, -60, 2)).getValue(PowerLanternBlock.ATTUNED), "a Mars crystal attunes a lantern");
		check(server, p -> p.getMainHandItem().isEmpty(), "attuning uses the crystal up");
		context.getInput().holdKey(options -> options.keyUse);
		for (int i = 0; i < 12; i++) {
			server.runOnServer(s -> RingPowers.chargeAt(player(s), player(s).level(), new BlockPos(0, -60, 2), Rings.lantern(RingColor.GREEN), true));
			context.waitTicks(4);
		}
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) > 100, "an attuned green lantern charges a red ring");
		context.takeScreenshot("worlds-2-oath-charge-bar");
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(80);
	}

	/** Ranks show under ring-bearers' names; a duel ends at the would-be killing blow. */
	private static void ranksAndDuels(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer friend = RingsClientGameTest.joinTestPlayer(s, "Friend");
			RingAttachments.setRing(friend, new ItemStack(Rings.ring(RingColor.BLUE)));
			Mastery.setLevel(friend, 31);
			friend.teleportTo(0.5, -60, 4.5);
		});
		server.runCommand("tp @a[name=!Friend] 0.5 -60 0.5 0 0");
		connection.waitForClientboundPackets();
		context.waitTicks(20);
		check(server, p -> RingsClientGameTest.friend(p.level().getServer()).getAttachedOrElse(Mastery.RANK, 0) == 31, "a player's rank is shared");
		checkClient(context, mc -> mc.level.players().stream().anyMatch(other -> other != mc.player && other.getAttachedOrElse(Mastery.RANK, 0) == 31),
				"other players' ranks sync to the client");
		context.takeScreenshot("worlds-3-rank");

		server.runCommand("execute as @a[name=!Friend] run duel Friend");
		server.runCommand("execute as Friend run duel accept");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer friend = RingsClientGameTest.friend(s);
			p.setHealth(4.0F);
			p.hurtServer(p.level(), friend.damageSources().playerAttack(friend), 50.0F);
		});
		check(server, p -> p.isAlive() && p.getHealth() >= 1.0F, "a duel's killing blow leaves the loser standing");
		check(server, p -> Mastery.of(RingsClientGameTest.friend(p.level().getServer())).firsts().contains(Mastery.First.DUEL.key()),
				"winning a duel earns the winner ring experience");
		server.runOnServer(s -> player(s).setHealth(20.0F));
	}

	/** Oa: doubled ring experience, and the Guardians' missions and respec. */
	private static void oa(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.ring(RingColor.GREEN))));
		server.runCommand("tp @a[name=!Friend] 0.5 455 0.5");
		Vec3 oa = Space.Planet.OA.centre();
		server.runCommand("execute in rings:space run tp @a[name=!Friend] " + oa.x + " " + oa.y + " " + oa.z);
		context.waitTicks(10);
		check(server, p -> p.level().dimension() == Space.Planet.OA.dimension(), "into Oa");
		check(server, p -> Mastery.of(p).firsts().contains(Mastery.First.OA.key()), "the first visit to Oa earns ring experience");
		int ground = server.computeOnServer(s -> OaCitadel.groundY(player(s).level()));
		server.runOnServer(s -> player(s).teleportTo(0.5, ground + 1, -12.5));
		context.waitTicks(10);

		int before = server.computeOnServer(s -> Mastery.of(player(s)).xp());
		server.runCommand("rings xp @a[name=!Friend] 50");
		check(server, p -> Mastery.of(p).xp() == before + 100, "ring experience is doubled on Oa");

		// A mission: asked for, done and turned in
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			OaGuardian guardian = guardian(p);
			guardian.setNoAi(true);
			guardian.teleportTo(p.getX(), p.getY(), p.getZ() + 3.0);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, guardian.getEyePosition());
		});
		context.waitTicks(5);
		talk(context, connection, server);
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && screen.node().id().equals("start"), "a Guardian talks");
		context.waitTicks(70);
		context.takeScreenshot("worlds-4-guardian");
		choose(context, connection, "work");
		check(server, p -> Missions.of(p) != null, "asking for work gives a mission");
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && screen.node().id().equals("mission_new"), "the Guardian sets the task");
		context.waitTicks(80);
		context.takeScreenshot("worlds-5-mission");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.MOONSTONE, 0));
			p.getInventory().add(new ItemStack(PlanetBlocks.MOONSTONE, 16));
		});
		int beforeMission = server.computeOnServer(s -> Mastery.of(player(s)).xp());
		talk(context, connection, server);
		choose(context, connection, "work");
		choose(context, connection, "done");
		check(server, p -> Missions.of(p) == null && p.getInventory().countItem(PlanetBlocks.MOONSTONE) == 0, "turning in a mission takes what was asked");
		check(server, p -> Mastery.of(p).xp() > beforeMission, "a mission earns ring experience");
		context.waitTicks(60);
		context.takeScreenshot("worlds-6-mission-done");

		// Unlearning everything, for will shards
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mastery.learn(p, Skill.SCAN);
			p.getInventory().add(new ItemStack(PlanetBlocks.WILL_SHARD, Dialogues.RESPEC_SHARDS));
		});
		check(server, p -> Mastery.has(p, Skill.SCAN), "a skill to unlearn");
		talk(context, connection, server);
		choose(context, connection, "respec");
		choose(context, connection, "respec_yes");
		check(server, p -> Mastery.of(p).skills().isEmpty() && p.getInventory().countItem(PlanetBlocks.WILL_SHARD) == 0,
				"a Guardian unmakes what was learned, for will shards");
		context.setScreen(() -> null);

		// An Oan relic: one more skill point
		int points = server.computeOnServer(s -> Mastery.of(player(s)).points());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Rings.OAN_RELIC));
			p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
		});
		check(server, p -> Mastery.of(p).points() == points + 1 && p.getMainHandItem().isEmpty(), "an Oan relic gives a skill point");
	}

	private static void talk(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			OaGuardian guardian = guardian(p);
			guardian.interact(p, InteractionHand.MAIN_HAND, guardian.position());
		});
		connection.waitForClientboundPackets();
		context.waitTicks(3);
	}

	private static void choose(ClientGameTestContext context, TestServerConnection connection, String option) {
		context.runOnClient(mc -> {
			DialogueScreen screen = (DialogueScreen) mc.gui.screen();
			screen.choose(screen.node().options().stream().filter(o -> o.id().equals(option)).findFirst()
					.orElseThrow(() -> new AssertionError("Expected the answer " + option + " in " + screen.node().id())));
		});
		connection.waitForServerboundPackets();
		context.waitTicks(3);
		connection.waitForClientboundPackets();
		context.waitTicks(3);
	}

	private static OaGuardian guardian(ServerPlayer player) {
		return player.level().getEntitiesOfClass(OaGuardian.class, player.getBoundingBox().inflate(64)).getFirst();
	}

	/** A shrine on the Moon and a wreck on Mars, each with its chest of loot. */
	private static void ruins(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		for (String[] place : new String[][] {{"rings:moon", "rings:moon_shrine", "worlds-7-moon-shrine"}, {"rings:mars", "rings:mars_wreck", "worlds-8-mars-wreck"}}) {
			server.runCommand("execute in " + place[0] + " run tp @a[name=!Friend] 0.5 200 0.5");
			context.waitTicks(20);
			server.runCommand("execute in " + place[0] + " run forceload add -16 -16 16 16");
			server.runCommand("execute in " + place[0] + " run place feature " + place[1] + " 0 100 0");
			int ground = server.computeOnServer(s -> chestY(player(s).level()));
			check(server, p -> ground > p.level().getMinY(), place[1] + " has a chest of loot");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(7.5, ground + 5.0, 7.5);
				p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, ground - 1, 0.5));
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			connection.waitForChunksRender();
			context.waitTicks(30);
			context.takeScreenshot(place[2]);
		}
	}

	/** The height of the ruin's chest (with its loot still to roll), or the bottom of the world if there's none. */
	private static int chestY(ServerLevel level) {
		for (BlockPos pos : BlockPos.betweenClosed(-8, level.getMinY(), -8, 8, level.getMaxY(), 8)) {
			if (level.getBlockState(pos).is(Blocks.CHEST) && level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null) {
				return pos.getY();
			}
		}
		return level.getMinY();
	}

	/** The Devourer comes for a strong ring-bearer in space, and drops Oan relics. */
	private static void devourer(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("difficulty normal");
		server.runCommand("execute in rings:space run tp @a[name=!Friend] 0.5 140 0.5");
		context.waitTicks(10);
		check(server, p -> p.level().dimension() == Space.SPACE, "in space");
		server.runCommand("execute as @a[name=!Friend] at @s run rings devourer");
		context.waitTicks(40);
		check(server, p -> !p.level().getEntitiesOfClass(Devourer.class, p.getBoundingBox().inflate(80)).isEmpty(), "the Devourer comes");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Devourer devourer = p.level().getEntitiesOfClass(Devourer.class, p.getBoundingBox().inflate(80)).getFirst();
			devourer.setNoAi(true);
			devourer.teleportTo(p.getX(), p.getY(), p.getZ() + 12.0);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, devourer.position().add(0.0, 3.0, 0.0));
		});
		context.waitTicks(20);
		camera(context, CameraType.FIRST_PERSON);
		context.takeScreenshot("worlds-9-devourer");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Devourer devourer = p.level().getEntitiesOfClass(Devourer.class, p.getBoundingBox().inflate(80)).getFirst();
			devourer.hurtServer(p.level(), p.damageSources().playerAttack(p), 10000.0F);
		});
		context.waitTicks(40);
		check(server, p -> Mastery.of(p).firsts().contains(Mastery.First.DEVOURER.key()), "destroying the Devourer earns ring experience");
		check(server, p -> !p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(40), e -> e.getItem().is(Rings.OAN_RELIC)).isEmpty(),
				"the Devourer drops Oan relics");
		server.runCommand("difficulty peaceful");
	}
}
