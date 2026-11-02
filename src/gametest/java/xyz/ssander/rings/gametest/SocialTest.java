package xyz.ssander.rings.gametest;

import java.nio.file.Files;
import java.util.Optional;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.Codex;
import xyz.ssander.rings.ForceBubble;
import xyz.ssander.rings.HardLightBlock;
import xyz.ssander.rings.KorugarFortress;
import xyz.ssander.rings.Manhunter;
import xyz.ssander.rings.Mastery;
import xyz.ssander.rings.Missions;
import xyz.ssander.rings.OaCitadel;
import xyz.ssander.rings.OaGuardian;
import xyz.ssander.rings.PlanetBlocks;
import xyz.ssander.rings.RingAbilities;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingComponents;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingCorps;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingPowers;
import xyz.ssander.rings.RingStyles;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.RingsConfig;
import xyz.ssander.rings.Space;
import xyz.ssander.rings.client.CodexScreen;
import xyz.ssander.rings.client.DialogueScreen;
import xyz.ssander.rings.client.SkillTreeScreen;
import xyz.ssander.rings.network.RingActionPayload;

import static xyz.ssander.rings.gametest.RingsClientGameTest.activate;
import static xyz.ssander.rings.gametest.RingsClientGameTest.camera;
import static xyz.ssander.rings.gametest.RingsClientGameTest.check;
import static xyz.ssander.rings.gametest.RingsClientGameTest.checkClient;
import static xyz.ssander.rings.gametest.RingsClientGameTest.friend;
import static xyz.ssander.rings.gametest.RingsClientGameTest.player;

/**
 * Corps, the roll of honour and the daily bounty, the new missions (scan, escort, rescue), ring styles, the emergency
 * light, Korugar and its fortress, the codex and the config file. Run on its own with RINGS_SOCIAL=1.
 */
final class SocialTest {
	private SocialTest() {
	}

	static void run(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty normal");
		server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.ring(RingColor.GREEN))));
		server.runCommand("rings unlockall @a");
		activate(context, connection);

		check(server, p -> Files.exists(FabricLoader.getInstance().getConfigDir().resolve("rings.json")) && RingsConfig.get().xpMultiplier == 1.0,
				"the config file is written with its defaults");
		corps(context, connection, server);
		styles(context, connection, server);
		emergency(context, connection, server);
		oa(context, connection, server);
		missions(context, connection, server);
		korugar(context, connection, server);
		codex(context, server);
	}

	private static void corps(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer friend = RingsClientGameTest.joinTestPlayer(s, "Friend");
			RingAttachments.setRing(friend, new ItemStack(Rings.ring(RingColor.BLUE)));
			Mastery.setLevel(friend, 12);
			friend.teleportTo(0.5, -60, 4.5);
		});
		server.runCommand("tp @a[name=!Friend] 0.5 -60 0.5 0 0");
		server.runCommand("execute as @a[name=!Friend] run corps create Lanterns");
		server.runCommand("execute as @a[name=!Friend] run corps invite Friend");
		server.runCommand("execute as Friend run corps join Lanterns");
		check(server, p -> RingCorps.together(p, friend(p.level().getServer())), "a player invited into a Corps joins it");
		check(server, p -> "Lanterns".equals(friend(p.level().getServer()).getAttached(RingCorps.NAME)), "a member wears their Corps' name");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(20.0F);
			p.hurtServer(p.level(), friend(s).damageSources().playerAttack(friend(s)), 6.0F);
		});
		check(server, p -> p.getHealth() == 20.0F, "Corps members don't hurt each other");

		// A Manhunter one of them hunts counts for both
		server.runOnServer(s -> {
			player(s).setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.MANHUNTERS, 0));
			friend(s).setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.MANHUNTERS, 0));
			ServerLevel level = player(s).level();
			Manhunter manhunter = Rings.MANHUNTER.create(level, EntitySpawnReason.COMMAND);
			manhunter.snapTo(3.5, -58, 3.5);
			level.addFreshEntity(manhunter);
			manhunter.hurtServer(level, player(s).damageSources().playerAttack(player(s)), 1000.0F);
		});
		check(server, p -> Missions.of(friend(p.level().getServer())).progress() == 1 && Missions.of(p).progress() == 1,
				"a Corps comrade's hunt counts for the whole Corps");
		server.runOnServer(s -> {
			player(s).removeAttached(Missions.MISSION);
			friend(s).removeAttached(Missions.MISSION);
		});
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		checkClient(context, mc -> mc.level.players().stream().anyMatch(other -> "Lanterns".equals(other.getAttached(RingCorps.NAME))),
				"Corps names sync to everyone");
		context.takeScreenshot("social-1-corps-name");
	}

	private static void styles(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("execute as @a[name=!Friend] run ring engrave In brightest day");
		check(server, p -> "In brightest day".equals(RingAttachments.getRing(p).get(RingComponents.ENGRAVING)), "a ring can be engraved");
		server.runCommand("execute as @a[name=!Friend] run ring glow pulse");
		check(server, p -> RingStyles.glow(p) == RingStyles.Glow.PULSE, "the glow can pulse");
		server.runCommand("execute as @a[name=!Friend] run ring constructs frame");
		check(server, p -> RingStyles.constructs(p) == RingStyles.ConstructStyle.FRAME, "constructs can be made as a frame");
		server.runOnServer(s -> {
			player(s).setYRot(180.0F);
			RingConstructs.make(player(s), RingConstructs.Construct.WALL);
		});
		check(server, p -> BlockPos.betweenClosedStream(p.blockPosition().offset(-6, -1, -6), p.blockPosition().offset(6, 6, 6))
				.anyMatch(pos -> p.level().getBlockState(pos).getBlock() instanceof HardLightBlock
						&& p.level().getBlockState(pos).getValue(HardLightBlock.STYLE) == RingStyles.ConstructStyle.FRAME),
				"a frame-style player's wall is a frame");
		server.runOnServer(s -> player(s).lookAt(EntityAnchorArgument.Anchor.EYES, player(s).position().add(0.0, 1.5, -3.0)));
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(10);
		context.takeScreenshot("social-2-frame-wall-pulse-glow");
		camera(context, CameraType.FIRST_PERSON);
		server.runCommand("fill -10 -60 -10 10 -50 10 air replace rings:hard_light");
	}

	private static void emergency(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(0.5, -20.0, 0.5);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			RingAttachments.getRing(p).set(RingComponents.CHARGE, 1);
		});
		// (Willpower halves the drain, rounded at random, so it can take a few seconds to run out)
		for (int i = 0; i < 20 && server.computeOnServer(s -> RingAttachments.isActive(player(s))); i++) {
			context.waitTicks(10);
		}
		context.waitTicks(2);
		check(server, p -> !RingAttachments.isActive(p) && p.hasEffect(MobEffects.SLOW_FALLING) && p.getAttachedOrElse(RingPowers.EMERGENCY_USED, false),
				"a ring that dies in the air lets its bearer down gently");
		server.runOnServer(s -> {
			RingAttachments.getRing(player(s)).set(RingComponents.CHARGE, RingItem.MAX_CHARGE);
			player(s).removeEffect(MobEffects.SLOW_FALLING);
			player(s).teleportTo(0.5, -60, 0.5);
		});
		context.waitTicks(10);
		activate(context, connection);
	}

	/** Oa: the roll of honour, and the day's bounty. */
	private static void oa(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("tp @a[name=!Friend] 0.5 455 0.5");
		Vec3 oa = Space.Planet.OA.centre();
		server.runCommand("execute in rings:space run tp @a[name=!Friend] " + oa.x + " " + oa.y + " " + oa.z);
		context.waitTicks(10);
		int ground = server.computeOnServer(s -> OaCitadel.groundY(player(s).level()));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(0.5, ground + 1, -12.5);
			OaGuardian guardian = guardian(p);
			guardian.setNoAi(true);
			guardian.teleportTo(p.getX(), p.getY(), p.getZ() + 3.0);
		});
		context.waitTicks(5);
		talk(context, connection, server);
		choose(context, connection, "roll");
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && screen.node().id().equals("roll"), "a Guardian reads the roll of honour");
		context.waitTicks(100);
		context.takeScreenshot("social-3-roll");
		choose(context, connection, "back");
		choose(context, connection, "bounty");
		check(server, p -> Missions.of(p) != null && Missions.of(p).bounty(), "a Guardian gives the day's bounty");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.MOONSTONE, 0, Optional.empty(), true));
			p.getInventory().add(new ItemStack(PlanetBlocks.MOONSTONE, 16));
		});
		talk(context, connection, server);
		choose(context, connection, "work");
		choose(context, connection, "done");
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && screen.node().id().equals("mission_done"), "the bounty is turned in");
		check(server, p -> p.getAttached(Missions.BOUNTY_DAY) != null && Codex.found(Mastery.of(p), "bounty"), "a bounty done is marked for the day");
		talk(context, connection, server);
		choose(context, connection, "bounty");
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && screen.node().id().equals("bounty_done_today"), "one bounty a day");
		context.setScreen(() -> null);
	}

	/** Scanning Mars, carrying a creature to the Moon, and bringing a stranded explorer home. */
	private static void missions(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> player(s).setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.SCAN_MARS, 0)));
		for (int i = 0; i < 3; i++) {
			int x = i * 100;
			server.runCommand("execute in rings:mars run tp @a[name=!Friend] " + x + " 200 0");
			context.waitTicks(25);
			server.runOnServer(s -> RingAbilities.use(player(s), RingActionPayload.Action.SCAN));
		}
		check(server, p -> Missions.of(p).progress() == 3, "scanning Mars from three far-apart places");

		// Escort: a pig, carried in a bubble from space into the Moon
		server.runOnServer(s -> player(s).setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.ESCORT_MOON, 0)));
		Vec3 moon = Space.Planet.MOON.centre();
		// Just above the Moon, so the bubble's along when we drop in (a bubble left far behind pops)
		Vec3 above = moon.add(0.0, Space.Planet.MOON.radius() + 6.0, 0.0);
		server.runCommand("execute in rings:space run tp @a[name=!Friend] " + above.x + " " + above.y + " " + above.z);
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Pig pig = EntityTypes.PIG.create(p.level(), EntitySpawnReason.COMMAND);
			pig.snapTo(p.getX() + 2.0, p.getY(), p.getZ());
			p.level().addFreshEntity(pig);
			ForceBubble bubble = ForceBubble.around(p, pig, RingColor.GREEN);
			p.level().addFreshEntity(bubble);
			bubble.carry(pig);
		});
		context.waitTicks(5);
		server.runCommand("execute in rings:space run tp @a[name=!Friend] " + above.x + " " + (above.y - 8.0) + " " + above.z);
		context.waitTicks(15);
		check(server, p -> p.level().dimension() == Space.Planet.MOON.dimension(), "into the Moon");
		check(server, p -> Missions.of(p).progress() == 1, "carrying a creature to the Moon");

		// Rescue: the explorer, carried home to the Earth
		server.runOnServer(s -> player(s).setAttached(Missions.MISSION, new Missions.Active(Missions.Mission.RESCUE_MARS, 0)));
		server.runCommand("execute in rings:space run tp @a[name=!Friend] 0.5 140 0.5");
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Villager explorer = EntityTypes.VILLAGER.create(p.level(), EntitySpawnReason.COMMAND);
			explorer.snapTo(p.getX() + 2.0, p.getY(), p.getZ());
			explorer.addTag(Missions.STRANDED);
			p.level().addFreshEntity(explorer);
			ForceBubble bubble = ForceBubble.around(p, explorer, RingColor.GREEN);
			p.level().addFreshEntity(bubble);
			bubble.carry(explorer);
		});
		context.waitTicks(5);
		server.runCommand("execute in rings:space run tp @a[name=!Friend] 0.5 -5 0.5");
		context.waitTicks(15);
		check(server, p -> p.level().dimension() == Level.OVERWORLD && Missions.of(p).progress() == 1, "bringing the stranded explorer home");
		server.runOnServer(s -> player(s).removeAttached(Missions.MISSION));
	}

	/** Korugar: its fortress, and the Fear Battery that only fills yellow rings. */
	private static void korugar(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("execute in rings:space run tp @a[name=!Friend] 0.5 140 0.5");
		context.waitTicks(10);
		Vec3 korugar = Space.Planet.KORUGAR.centre();
		server.runCommand("execute in rings:space run tp @a[name=!Friend] " + korugar.x + " " + korugar.y + " " + korugar.z);
		context.waitTicks(15);
		check(server, p -> p.level().dimension() == Space.Planet.KORUGAR.dimension(), "flying into Korugar lands on it");
		check(server, p -> Mastery.of(p).firsts().contains(Mastery.First.KORUGAR.key()), "the first landing on Korugar earns ring experience");
		int ground = server.computeOnServer(s -> KorugarFortress.groundY(player(s).level()));
		check(server, p -> p.level().getBlockState(new BlockPos(0, ground + 5, 0)).is(Rings.FEAR_BATTERY), "the Fortress of Fear stands on Korugar");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(0.5, ground + 20, -30.5);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, ground + 6, 0.5));
		});
		connection.waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot("social-4-korugar-fortress");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ItemStack yellow = new ItemStack(Rings.ring(RingColor.YELLOW));
			yellow.set(RingComponents.CHARGE, 10);
			RingAttachments.setRing(p, yellow);
			BlockPos core = new BlockPos(0, ground + 5, 0);
			p.level().getBlockState(core).useWithoutItem(p.level(), p, new BlockHitResult(Vec3.atCenterOf(core), net.minecraft.core.Direction.NORTH, core, false));
		});
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == RingItem.MAX_CHARGE, "the Fear Battery fills a yellow ring");
	}

	private static void codex(ClientGameTestContext context, TestServerContext server) {
		check(server, p -> Codex.found(Mastery.of(p), "korugar") && Codex.found(Mastery.of(p), "fear_battery") && Codex.found(Mastery.of(p), "guardian"),
				"the codex fills in as things are found");
		context.setScreen(() -> new CodexScreen(new SkillTreeScreen()));
		context.waitTicks(5);
		context.takeScreenshot("social-5-codex");
		context.setScreen(() -> null);
	}

	private static OaGuardian guardian(ServerPlayer player) {
		return player.level().getEntitiesOfClass(OaGuardian.class, player.getBoundingBox().inflate(64)).getFirst();
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
}
