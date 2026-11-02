package xyz.ssander.rings.gametest;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.CrashPilot;
import xyz.ssander.rings.Crashes;
import xyz.ssander.rings.Dialogues;
import xyz.ssander.rings.HardLightBlock;
import xyz.ssander.rings.Mastery;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.ShipBlocks;
import xyz.ssander.rings.Skill;
import xyz.ssander.rings.SuitStyle;
import xyz.ssander.rings.client.DialogueScreen;
import xyz.ssander.rings.client.RingsClient;
import xyz.ssander.rings.client.SkillTreeScreen;
import xyz.ssander.rings.client.SuitScreen;
import xyz.ssander.rings.network.CreateConstructPayload;
import xyz.ssander.rings.network.LearnSkillPayload;
import xyz.ssander.rings.network.RingActionPayload;

import static xyz.ssander.rings.gametest.RingsClientGameTest.activate;
import static xyz.ssander.rings.gametest.RingsClientGameTest.camera;
import static xyz.ssander.rings.gametest.RingsClientGameTest.check;
import static xyz.ssander.rings.gametest.RingsClientGameTest.checkClient;
import static xyz.ssander.rings.gametest.RingsClientGameTest.player;

/**
 * Ring levels, the skill tree and the suits it earns, a crash site with its dying pilot handing their ring on, the
 * ship's console, the new lanterns, and a ship falling out of the sky. Run on its own with RINGS_MASTERY=1.
 */
final class MasteryTest {
	/** A stone platform well above the flat world's floor, for crash sites to come down on. */
	private static final int GROUND = -44;

	private MasteryTest() {
	}

	static void run(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("fill -24 -60 -24 24 " + GROUND + " 24 stone");
		server.runCommand("fill -24 " + GROUND + " -24 24 " + GROUND + " 24 grass_block");
		server.runCommand("tp @a 0 " + (GROUND + 1) + " 0 0 0");
		connection.waitForChunksRender();

		newBearer(context, connection, server);
		skillTree(context, connection, server);
		suits(context, connection, server);
		crashSite(context, connection, server);
		lanterns(context, connection, server);
		fallingShip(context, connection, server);
	}

	/** A new ring-bearer is level 1, with only the basics. */
	private static void newBearer(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mastery.reset(p);
			RingAttachments.setRing(p, new ItemStack(Rings.ring(RingColor.GREEN)));
		});
		activate(context, connection);
		check(server, p -> Mastery.level(p) == 1 && Mastery.of(p).points() == 1, "a new bearer is level 1 with one skill point");
		check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 4.0 && p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 0.0,
				"a new bearer's ring gives +4 armor");

		context.runOnClient(mc -> ClientPlayNetworking.send(new CreateConstructPayload(RingConstructs.Construct.DOME)));
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> hardLight(p) == 0, "a new bearer can't make a dome");
		context.runOnClient(mc -> ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.FORCE_FIELD)));
		connection.waitForServerboundPackets();
		context.waitTick();
		check(server, p -> !p.getAttachedOrElse(RingAttachments.SHIELD, false), "a new bearer has no force field");
		context.runOnClient(mc -> ClientPlayNetworking.send(new CreateConstructPayload(RingConstructs.Construct.WALL)));
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> hardLight(p) > 0, "a new bearer can make a wall");
		server.runCommand("fill -10 " + (GROUND + 1) + " -10 10 " + (GROUND + 8) + " 10 air replace rings:hard_light");
	}

	private static int hardLight(ServerPlayer player) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8, -2, -8), player.blockPosition().offset(8, 8, 8))) {
			if (player.level().getBlockState(pos).getBlock() instanceof HardLightBlock) {
				count++;
			}
		}
		return count;
	}

	/** Earning levels, and spending the points on the skill tree. */
	private static void skillTree(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("rings xp @a 500");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		check(server, p -> Mastery.level(p) == 7 && Mastery.of(p).points() == 7, "500 ring experience is level 7, with 7 points");
		context.takeScreenshot("rings-14-level-up");
		checkClient(context, mc -> Mastery.level(mc.player) == 7, "the level syncs to the player");

		context.getInput().pressKey(RingsClient.skillsKey);
		context.waitTicks(3);
		checkClient(context, mc -> mc.gui.screen() instanceof SkillTreeScreen, "the skills key opens the skill tree");
		context.takeScreenshot("rings-14-skills-new");

		learn(context, connection, Skill.FORCE_FIELD);
		learn(context, connection, Skill.SHAPER);
		learn(context, connection, Skill.HARD_LIGHT_SKIN);
		learn(context, connection, Skill.SONIC);
		check(server, p -> Mastery.has(p, Skill.FORCE_FIELD) && Mastery.has(p, Skill.SHAPER) && Mastery.has(p, Skill.HARD_LIGHT_SKIN),
				"skills can be learned with points");
		check(server, p -> !Mastery.has(p, Skill.SONIC), "a skill can't be learned before the one it grows out of, or its level");
		check(server, p -> Mastery.of(p).points() == 4, "learning skills spends points");
		check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 8.0, "hard-light skin raises the ring's armor");
		connection.waitForClientboundPackets();
		context.waitTicks(3);
		context.takeScreenshot("rings-14-skills-learned");
		context.setScreen(() -> null);

		context.runOnClient(mc -> ClientPlayNetworking.send(new CreateConstructPayload(RingConstructs.Construct.DOME)));
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> hardLight(p) > 0, "learning shaper unlocks the dome");
		server.runCommand("fill -10 " + (GROUND + 1) + " -10 10 " + (GROUND + 8) + " 10 air replace rings:hard_light");
		context.runOnClient(mc -> ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.FORCE_FIELD)));
		connection.waitForServerboundPackets();
		context.waitTick();
		check(server, p -> p.getAttachedOrElse(RingAttachments.SHIELD, false), "learning the force field unlocks it");
		context.runOnClient(mc -> ClientPlayNetworking.send(new RingActionPayload(RingActionPayload.Action.FORCE_FIELD)));
		connection.waitForServerboundPackets();

		server.runCommand("rings unlockall @a");
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.setScreen(SkillTreeScreen::new);
		context.waitTicks(3);
		context.takeScreenshot("rings-14-skills-all");
		context.setScreen(() -> null);
		check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 12.0, "fortress raises the ring's armor to 12");
	}

	private static void learn(ClientGameTestContext context, TestServerConnection connection, Skill skill) {
		context.runOnClient(mc -> ClientPlayNetworking.send(new LearnSkillPayload(skill)));
		connection.waitForServerboundPackets();
		context.waitTick();
	}

	/** The suits a level earns, and the ones still locked. */
	private static void suits(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("rings reset @a");
		server.runCommand("rings level @a 12");
		server.runOnServer(s -> player(s).removeAttached(Mastery.HEIR));
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		check(server, p -> SuitStyle.HONOR_GUARD.unlocked(p) && !SuitStyle.STARFIELD.unlocked(p) && !SuitStyle.PILOT.unlocked(p),
				"level 12 unlocks the veteran and honor guard suits, not the rest");
		context.runOnClient(mc -> ClientPlayNetworking.send(new xyz.ssander.rings.network.ChooseSuitPayload(SuitStyle.PARAGON)));
		connection.waitForServerboundPackets();
		context.waitTick();
		check(server, p -> RingAttachments.suit(p) != SuitStyle.PARAGON, "a locked suit can't be chosen");
		context.runOnClient(mc -> ClientPlayNetworking.send(new xyz.ssander.rings.network.ChooseSuitPayload(SuitStyle.HONOR_GUARD)));
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> RingColor.GREEN.costume(SuitStyle.HONOR_GUARD).equals(p.getAttached(RingAttachments.COSTUME)), "an earned suit can be worn");
		connection.waitForClientboundPackets();
		context.setScreen(SuitScreen::new);
		context.waitTicks(3);
		context.takeScreenshot("rings-13-suit-screen-locked");
		context.setScreen(() -> null);
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.takeScreenshot("rings-13-suit-honor_guard-front");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("rings-13-suit-honor_guard-cape");
		camera(context, CameraType.FIRST_PERSON);
		server.runCommand("rings unlockall @a");
		for (SuitStyle style : new SuitStyle[] {SuitStyle.VETERAN, SuitStyle.STARFIELD, SuitStyle.SENTINEL, SuitStyle.PARAGON, SuitStyle.PILOT}) {
			context.runOnClient(mc -> ClientPlayNetworking.send(new xyz.ssander.rings.network.ChooseSuitPayload(style)));
			connection.waitForServerboundPackets();
			connection.waitForClientboundPackets();
			context.waitTicks(4);
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("rings-13-suit-" + style.id() + "-front");
			if (style == SuitStyle.PARAGON) {
				camera(context, CameraType.THIRD_PERSON_BACK);
				context.takeScreenshot("rings-13-suit-paragon-cape");
			}
		}
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> ClientPlayNetworking.send(new xyz.ssander.rings.network.ChooseSuitPayload(SuitStyle.CLASSIC)));
		connection.waitForServerboundPackets();
		activate(context, connection);
	}

	/** A crashed ship and its pilot: their talk, handing the ring on, their passing, and the ship's console. */
	private static void crashSite(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("rings reset @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			RingAttachments.setRing(p, ItemStack.EMPTY);
			p.removeAttached(Mastery.HEIR);
		});
		server.runCommand("clear @a");
		server.runCommand("tp @a 0 " + (GROUND + 1) + " 0 0 0");
		server.runCommand("execute as @a at @s run rings crashsite green");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		check(server, p -> pilot(p) != null, "a crash site has its pilot");
		check(server, p -> pilot(p).color() == RingColor.GREEN && !pilot(p).ringTaken(), "the pilot still has their ring");
		check(server, p -> console(p) != null, "a crash site has its console");

		// From above, then beside the pilot (as a spectator, so the camera can go anywhere)
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			CrashPilot pilot = pilot(p);
			Vec3 at = pilot.position().add(10.0, 14.0, 10.0);
			p.teleportTo(at.x, at.y, at.z);
			look(p, pilot.position());
			p.getAbilities().flying = true;
			p.getAbilities().mayfly = true;
			p.onUpdateAbilities();
		});
		connection.waitForClientboundPackets();
		context.waitTicks(40);
		context.takeScreenshot("rings-15-crash-site");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			CrashPilot pilot = pilot(p);
			// In front of them and a little up, clear of the ground
			Vec3 facing = Vec3.directionFromRotation(0.0F, pilot.yBodyRot);
			Vec3 front = pilot.position().add(facing.scale(2.6)).add(facing.yRot((float) Math.PI / 2.0F).scale(1.0)).add(0.0, 1.4, 0.0);
			p.teleportTo(front.x, front.y, front.z);
			look(p, pilot.position().add(0.0, 0.6, 0.0));
		});
		connection.waitForClientboundPackets();
		context.waitTicks(50);
		context.takeScreenshot("rings-15-crash-pilot");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Vec3 above = pilot(p).position().add(0.0, 6.0, 0.0);
			p.teleportTo(above.x, above.y, above.z);
		});
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});

		// Talk to them
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			pilot(p).interact(p, InteractionHand.MAIN_HAND, pilot(p).position());
		});
		connection.waitForClientboundPackets();
		context.waitTicks(80);
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen, "talking to the pilot opens their dialogue");
		context.takeScreenshot("rings-16-dialogue-start");
		choose(context, "ring");
		context.waitTicks(80);
		context.takeScreenshot("rings-16-dialogue-ring");
		choose(context, "accept");
		connection.waitForServerboundPackets();
		context.waitTicks(60);
		context.takeScreenshot("rings-16-dialogue-taken");
		check(server, p -> p.getInventory().contains(stack -> stack.is(Rings.ring(RingColor.GREEN)))
				&& p.getInventory().countItem(Rings.lantern(RingColor.GREEN).asItem()) == 1, "the pilot hands on their ring and lantern");
		check(server, p -> BlockPos.betweenClosedStream(new BlockPos(-24, GROUND - 8, -24), new BlockPos(24, GROUND + 10, 24))
				.noneMatch(pos -> p.level().getBlockState(pos).is(Rings.lantern(RingColor.GREEN))), "the lantern handed on is gone from the crash site");
		check(server, p -> Mastery.isHeir(p) && SuitStyle.PILOT.unlocked(p), "taking the ring unlocks the pilot's uniform");
		check(server, p -> p.getInventory().contains(stack -> stack.is(Rings.ring(RingColor.GREEN)) && RingItem.charge(stack) < RingItem.MAX_CHARGE),
				"the pilot's ring is low after the crash");
		choose(context, "promise");
		context.waitTicks(40);
		context.setScreen(() -> null);
		context.waitTicks(80);
		check(server, p -> pilot(p) == null, "the pilot passes after handing their ring on");

		// The console's flight recorder
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos console = console(p);
			p.level().getBlockState(console).useWithoutItem(p.level(), p, new BlockHitResult(Vec3.atCenterOf(console), Direction.UP, console, false));
		});
		connection.waitForClientboundPackets();
		context.waitTicks(80);
		checkClient(context, mc -> mc.gui.screen() instanceof DialogueScreen screen && Dialogues.LOG_AFTER.equals(screen.node().id()),
				"the console opens its flight recorder at the final entry once the ring is taken");
		context.takeScreenshot("rings-17-ship-log");
		context.setScreen(() -> null);
	}

	private static void choose(ClientGameTestContext context, String option) {
		context.runOnClient(mc -> {
			DialogueScreen screen = (DialogueScreen) mc.gui.screen();
			screen.choose(screen.node().options().stream().filter(o -> o.id().equals(option)).findFirst()
					.orElseThrow(() -> new AssertionError("Expected the answer " + option + " in " + screen.node().id())));
		});
	}

	private static CrashPilot pilot(ServerPlayer player) {
		List<CrashPilot> pilots = player.level().getEntitiesOfClass(CrashPilot.class, new AABB(player.blockPosition()).inflate(40.0), CrashPilot::isAlive);
		return pilots.isEmpty() ? null : pilots.getFirst();
	}

	private static BlockPos console(ServerPlayer player) {
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-24, GROUND - 8, -24), new BlockPos(24, GROUND + 10, 24))) {
			if (player.level().getBlockState(pos).is(ShipBlocks.SHIP_CONSOLE)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static void look(ServerPlayer player, Vec3 at) {
		Vec3 eye = player.getEyePosition();
		Vec3 d = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
		player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
	}

	/** The new lanterns, by day and by night. */
	private static void lanterns(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("fill -20 " + (GROUND + 1) + " -20 -12 " + (GROUND + 6) + " -12 air");
		RingColor[] colors = RingColor.values();
		for (int i = 0; i < colors.length; i++) {
			server.runCommand("setblock " + (-19 + i) + " " + (GROUND + 1) + " -16 rings:" + colors[i].lanternId());
		}
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(-15.5, GROUND + 1.6, -13.2);
			look(p, new Vec3(-15.5, GROUND + 1.4, -15.5));
		});
		connection.waitForClientboundPackets();
		context.waitTicks(30);
		context.takeScreenshot("rings-18-lanterns-day");
		server.runCommand("time set midnight");
		connection.waitForClientboundPackets();
		context.waitTicks(30);
		context.takeScreenshot("rings-18-lanterns-night");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(-17.5, GROUND + 1.3, -14.6);
			look(p, new Vec3(-18.5, GROUND + 1.4, -15.5));
		});
		connection.waitForClientboundPackets();
		context.waitTicks(20);
		context.takeScreenshot("rings-18-lantern-close");
		server.runCommand("time set noon");
	}

	/** A ship falls out of the sky and crashes near by, and its pilot waits there. */
	private static void fallingShip(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("fill -24 " + (GROUND + 1) + " -24 24 " + (GROUND + 20) + " 24 air");
		server.runCommand("fill -24 " + (GROUND - 6) + " -24 24 " + GROUND + " 24 stone");
		server.runCommand("fill -24 " + GROUND + " -24 24 " + GROUND + " 24 grass_block");
		server.runCommand("kill @e[type=rings:crash_pilot]");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(0.5, GROUND + 1.0, 0.5);
			Crashes.fall(p.level(), p, RingColor.YELLOW, 3.0, 4.0);
		});
		context.waitTicks(30);
		server.runOnServer(s -> look(player(s), player(s).position().add(0.0, 60.0, 30.0)));
		context.waitTicks(20);
		context.takeScreenshot("rings-19-falling");
		for (int i = 0; i < 20 && server.computeOnServer(s -> pilot(player(s)) == null); i++) {
			context.waitTicks(10);
		}
		check(server, p -> pilot(p) != null && pilot(p).color() == RingColor.YELLOW, "a falling ship crashes with its pilot");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(14.0, GROUND + 12.0, 14.0);
			look(p, pilot(p).position());
		});
		connection.waitForClientboundPackets();
		context.waitTicks(30);
		context.takeScreenshot("rings-19-crashed");
	}
}
