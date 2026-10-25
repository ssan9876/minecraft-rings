package xyz.ssander.rings.gametest;

import java.lang.reflect.Method;
import java.util.function.Function;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.HardLightBlock;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingComponents;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingSlot;
import xyz.ssander.rings.Space;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.client.ConstructWheelScreen;
import xyz.ssander.rings.client.RingAnimations;
import xyz.ssander.rings.client.RingFlight;
import xyz.ssander.rings.client.RingsClient;
import xyz.ssander.rings.client.SuperFlight;

/** Plays through the ring: equip, activate, fly, land, costume, charge, every colour, take off, death drop, creative inventory. */
public class RingsClientGameTest implements FabricClientGameTest {
	private static final Identifier LANTERN = RingColor.GREEN.costume();
	private static final RingItem GREEN_RING = Rings.ring(RingColor.GREEN);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("RINGS_POSES") != null) {
			filmPoses(context);
			return;
		}
		if (System.getenv("RINGS_FLIGHT") != null) {
			filmFlight(context);
			return;
		}
		if (System.getenv("RINGS_SPACE") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				exploreSpace(context, world.getConnection(), world.getServer());
			}
			return;
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			TestServerContext server = world.getServer();
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("give @a rings:hero_ring");
			connection.waitForClientboundPackets();

			// Equip by shift-clicking the ring out of the first hotbar slot
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(2);
			context.takeScreenshot("rings-1-inventory");
			quickMove(context, 36);
			connection.waitForServerboundPackets();
			check(server, p -> RingAttachments.getRing(p).is(GREEN_RING), "shift-click puts the ring in the ring slot");
			context.waitTicks(2);
			context.takeScreenshot("rings-2-equipped");
			context.setScreen(() -> null);

			// Activate: flight and costume, synced to the client
			activate(context, connection);
			check(server, p -> RingAttachments.isActive(p) && p.getAbilities().mayfly, "active ring grants flight");
			check(server, p -> LANTERN.equals(p.getAttached(RingAttachments.COSTUME)), "active ring sets the costume");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> LANTERN.equals(mc.player.getAttached(RingAttachments.COSTUME)), "costume syncs to the client");
			checkClient(context, mc -> RingAnimations.isPlaying(mc.player, RingAnimations.ACTIVATE), "activating plays the activation animation");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("rings-2-activate-front");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.FIRST_PERSON);
			context.waitTicks(20);

			// Fly up a few blocks and look at the costume from every side
			server.runCommand("execute as @a at @s run tp @s ~ ~4 ~");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			connection.waitForClientboundPackets();
			context.waitTicks(10);
			check(server, p -> p.getAttachedOrElse(RingAttachments.FLYING, false), "flying on the ring is flagged");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> mc.player.getAttachedOrElse(RingAttachments.FLYING, false), "the flying flag syncs to the client");
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("rings-3-hover-front");
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-4-hover-back");
			camera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("rings-5-first-person");

			// Film from the side (see CameraMixin): hovering, then flying forward fast, then banking into a turn
			TestCamera.yawOffset = 90.0F;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-6-hover-side");
			context.getInput().holdKey(options -> options.keyJump);
			context.waitTicks(10);
			checkClient(context, mc -> RingFlight.dive(mc.player, 1.0F) == 0.0F, "flying straight up stays upright");
			context.takeScreenshot("rings-6-rising-side");
			context.getInput().releaseKey(options -> options.keyJump);
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(10);
			checkClient(context, mc -> RingFlight.dive(mc.player, 1.0F) == 0.0F && mc.player.getDeltaMovement().horizontalDistance() > 0.2,
					"flying sideways stays upright");
			context.takeScreenshot("rings-6-strafing-side");
			context.getInput().releaseKey(options -> options.keyLeft);
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(10);
			checkClient(context, mc -> RingFlight.dive(mc.player, 1.0F) == 0.0F, "flying forward without sprinting stays upright");
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.waitTicks(12);
			checkClient(context, mc -> mc.player.isFallFlying() && SuperFlight.isGliding() && !mc.player.getAbilities().flying,
					"sprinting forward switches into elytra mode");
			check(server, p -> p.isFallFlying() && p.getAttachedOrElse(RingAttachments.FLYING, false), "the server sees elytra mode");
			context.takeScreenshot("rings-7-dive-side");
			TestCamera.yawOffset = 140.0F;
			context.waitTick();
			context.takeScreenshot("rings-7-dive-front-quarter");
			TestCamera.yawOffset = 90.0F;
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(8);
			TestCamera.yawOffset = 0.0F;
			context.takeScreenshot("rings-8-dive-banking");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.getInput().releaseKey(options -> options.keyLeft);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(20);
			checkClient(context, mc -> !mc.player.isFallFlying() && mc.player.getAbilities().flying, "letting go of forward goes back to hovering");
			check(server, p -> !p.isFallFlying() && p.getAbilities().flying, "the server sees the hover");

			// Come down to land, filmed from the side
			context.getInput().holdKey(options -> options.keyShift);
			for (int i = 0; i < 200 && !context.computeOnClient(mc -> mc.player.onGround()); i++) {
				context.waitTick();
			}
			context.getInput().releaseKey(options -> options.keyShift);
			connection.waitForServerboundPackets();
			context.waitTicks(3);
			checkClient(context, mc -> RingAnimations.isPlaying(mc.player, RingAnimations.LAND), "landing from flight plays the superhero landing");
			TestCamera.yawOffset = 90.0F;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.takeScreenshot("rings-8-landing-side");
			TestCamera.yawOffset = 150.0F;
			context.waitTick();
			context.takeScreenshot("rings-8-landing-front-quarter");
			TestCamera.yawOffset = 0.0F;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.waitTicks(30);
			checkClient(context, mc -> !RingAnimations.isPlaying(mc.player, RingAnimations.LAND), "the landing animation ends");

			camera(context, CameraType.FIRST_PERSON);
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) < RingItem.MAX_CHARGE, "the ring uses power while on");
			server.runCommand("execute as @a at @s run tp @s ~ ~4 ~");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			connection.waitForClientboundPackets();
			context.waitTicks(5);

			// Super flight: sneak and forward builds up to several times flying speed, into the sonic pose
			context.runOnClient(mc -> mc.player.setXRot(-8.0F));
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(30);
			checkClient(context, mc -> SuperFlight.isActive(), "sneak and forward while ring flying is super flight");
			checkClient(context, mc -> new Vec3(mc.player.getX() - mc.player.xo, mc.player.getY() - mc.player.yo, mc.player.getZ() - mc.player.zo).length() > 1.6,
					"super flight builds up to sonic speed");
			context.waitTicks(10);
			checkClient(context, mc -> new Vec3(mc.player.getX() - mc.player.xo, mc.player.getY() - mc.player.yo, mc.player.getZ() - mc.player.zo).length() > 3.0,
					"super flight builds up to over 3 blocks a tick");
			checkClient(context, mc -> RingFlight.sonic(mc.player, 1.0F) > 0.5F, "sonic speed takes the sonic pose");
			context.takeScreenshot("rings-7-super-first-person");
			TestCamera.yawOffset = 90.0F;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-7-super-side");
			TestCamera.yawOffset = 150.0F;
			context.waitTick();
			context.takeScreenshot("rings-7-super-front-quarter");
			check(server, p -> p.getKnownMovement().length() > 1.0, "the server sees super flight");

			// Straight up: fist to the sky
			TestCamera.yawOffset = 90.0F;
			context.runOnClient(mc -> mc.player.setXRot(-89.0F));
			context.waitTicks(10);
			checkClient(context, mc -> RingFlight.rise(mc.player, 1.0F) > 0.5F, "rising fast raises the fist to the sky");
			context.takeScreenshot("rings-7-super-rise-side");

			// Dive into the ground: head first, then a shockwave on landing
			context.runOnClient(mc -> mc.player.setXRot(75.0F));
			context.waitTicks(4);
			context.takeScreenshot("rings-7-super-dive-side");
			for (int i = 0; i < 200 && !context.computeOnClient(mc -> mc.player.onGround()); i++) {
				context.waitTick();
			}
			context.getInput().releaseKey(options -> options.keyShift);
			context.getInput().releaseKey(options -> options.keyUp);
			connection.waitForServerboundPackets();
			context.waitTicks(4);
			checkClient(context, mc -> !SuperFlight.isActive(), "letting go ends super flight");
			checkClient(context, mc -> RingAnimations.isPlaying(mc.player, RingAnimations.LAND), "a super flight landing plays the superhero landing");
			TestCamera.yawOffset = 150.0F;
			context.runOnClient(mc -> mc.player.setXRot(10.0F));
			context.waitTick();
			context.takeScreenshot("rings-7-super-impact");
			TestCamera.yawOffset = 0.0F;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.player.setXRot(0.0F));
			context.waitTicks(30);
			server.runCommand("execute as @a at @s run tp @s ~ ~4 ~");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			connection.waitForClientboundPackets();
			context.waitTicks(5);

			// Deactivate in mid-air: flight goes, slow falling catches the player
			activate(context, connection);
			check(server, p -> !RingAttachments.isActive(p) && !p.getAbilities().mayfly, "deactivating removes flight");
			check(server, p -> p.getAttached(RingAttachments.COSTUME) == null, "deactivating removes the costume");
			check(server, p -> p.hasEffect(MobEffects.SLOW_FALLING), "losing flight mid-air gives slow falling");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> mc.player.getAttached(RingAttachments.COSTUME) == null, "costume removal syncs to the client");
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-8-deactivated");
			camera(context, CameraType.FIRST_PERSON);

			// Taking the ring off while active switches it off
			activate(context, connection);
			check(server, RingAttachments::isActive, "ring activates again");
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTick();
			int ringSlot = context.computeOnClient(mc -> RingSlot.indexIn(mc.player.inventoryMenu));
			quickMove(context, ringSlot);
			connection.waitForServerboundPackets();
			context.waitTick();
			check(server, p -> RingAttachments.getRing(p).isEmpty(), "shift-click takes the ring out");
			check(server, p -> !RingAttachments.isActive(p) && !p.getAbilities().mayfly, "removing the ring switches it off");
			context.setScreen(() -> null);

			// Running out of power switches the ring off, and it won't switch on again until charged
			server.runCommand("clear @a");
			server.runCommand("tp @a 0 -60 0 0 30");
			server.runOnServer(s -> {
				ItemStack almostEmpty = new ItemStack(GREEN_RING);
				almostEmpty.set(RingComponents.CHARGE, 2);
				RingAttachments.setRing(player(s), almostEmpty);
			});
			context.waitTicks(5);
			activate(context, connection);
			check(server, RingAttachments::isActive, "a ring with power switches on");
			context.waitTicks(45);
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == 0 && !RingAttachments.isActive(p), "an empty ring switches off");
			activate(context, connection);
			check(server, p -> !RingAttachments.isActive(p), "an empty ring won't switch on");

			// A lantern of another colour won't charge it
			server.runCommand("setblock 0 -60 2 rings:blue_power_lantern");
			context.waitTick();
			context.getInput().lookAt(new BlockPos(0, -60, 2));
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(10);
			context.getInput().releaseKey(options -> options.keyUse);
			connection.waitForServerboundPackets();
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == 0, "a lantern of another colour doesn't charge the ring");
			context.takeScreenshot("rings-9-wrong-lantern");

			// Holding right-click on a power lantern of its colour charges it back up
			server.runCommand("setblock 0 -60 2 rings:power_lantern");
			context.waitTick();
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(16);
			context.takeScreenshot("rings-9-charging");
			checkClient(context, mc -> RingAnimations.isPlaying(mc.player, RingAnimations.CHARGE), "charging holds the ring out to the lantern");
			TestCamera.yawOffset = 90.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-9-charging-side");
			for (int i = 0; i < 160 && !server.computeOnServer(s -> RingItem.charge(RingAttachments.getRing(player(s))) == RingItem.MAX_CHARGE); i++) {
				context.waitTick();
			}
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == RingItem.MAX_CHARGE, "the power lantern charges the ring");
			connection.waitForClientboundPackets();
			context.waitTicks(8);
			checkClient(context, mc -> RingAnimations.isPlaying(mc.player, RingAnimations.CHARGE_FULL), "a full ring bursts with power");
			context.takeScreenshot("rings-9-full-burst-side");
			context.getInput().releaseKey(options -> options.keyUse);
			connection.waitForServerboundPackets();
			context.waitTicks(40);
			checkClient(context, mc -> !RingAnimations.isPlaying(mc.player, RingAnimations.CHARGE)
					&& !RingAnimations.isPlaying(mc.player, RingAnimations.CHARGE_FULL), "the charging animations end after charging");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.waitTicks(25);
			context.takeScreenshot("rings-9-charged");

			// The beam: only while the ring is on, it hurts what it hits, uses extra power, and the ring adds armor
			server.runCommand("summon minecraft:pig 0.5 -60 8.5 {NoAI:1b}");
			server.runCommand("tp @a 0.5 -60 0.5");
			context.waitTicks(2);
			context.getInput().lookAt(new BlockPos(0, -60, 8));
			context.getInput().holdKey(options -> RingsClient.beamKey);
			context.waitTicks(3);
			connection.waitForServerboundPackets();
			check(server, p -> !RingBeam.isFiring(p), "the beam won't fire while the ring is off");
			context.getInput().releaseKey(options -> RingsClient.beamKey);
			context.waitTicks(2);
			check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 0.0, "no ring armor while the ring is off");
			activate(context, connection);
			check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 8.0 && p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 2.0,
					"an active ring gives armor");
			server.runOnServer(s -> RingAttachments.getRing(player(s)).set(RingComponents.CHARGE, RingItem.MAX_CHARGE));
			context.getInput().holdKey(options -> RingsClient.beamKey);
			context.waitTicks(3);
			connection.waitForServerboundPackets();
			check(server, RingBeam::isFiring, "holding the beam key fires the beam");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> RingBeam.isFiring(mc.player), "firing syncs to the client");
			context.waitTicks(10);
			context.takeScreenshot("rings-13-beam-first-person");
			TestCamera.yawOffset = 90.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-13-beam-side");
			TestCamera.yawOffset = 160.0F;
			context.waitTick();
			context.takeScreenshot("rings-13-beam-front-quarter");
			TestCamera.yawOffset = 0.0F;
			context.waitTicks(20);
			check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(16), pig -> pig.getHealth() >= pig.getMaxHealth()).isEmpty(),
					"the beam hurts what it hits");
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) <= RingItem.MAX_CHARGE - 5, "the beam uses extra power");
			context.getInput().releaseKey(options -> RingsClient.beamKey);
			context.waitTicks(2);
			connection.waitForServerboundPackets();
			check(server, p -> !RingBeam.isFiring(p), "letting go stops the beam");
			camera(context, CameraType.FIRST_PERSON);
			activate(context, connection);
			check(server, p -> p.getAttributeValue(Attributes.ARMOR) == 0.0, "the ring armor goes when the ring switches off");
			server.runCommand("kill @e[type=minecraft:pig]");

			// Constructs. The wheel: hold the key, point at Wall (the top), let go
			activate(context, connection);
			server.runOnServer(s -> RingAttachments.getRing(player(s)).set(RingComponents.CHARGE, RingItem.MAX_CHARGE));
			server.runCommand("summon minecraft:pig 0.5 -60 6.5 {NoAI:1b}");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			context.waitTicks(2);
			context.getInput().holdKey(options -> RingsClient.constructKey);
			context.waitTicks(3);
			checkClient(context, mc -> mc.gui.screen() instanceof ConstructWheelScreen, "the construct key opens the wheel");
			double[] wallSlot = context.computeOnClient(mc -> {
				double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
				return new double[] {mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0 - (78 + 12) * scale};
			});
			context.getInput().setCursorPos(wallSlot[0], wallSlot[1]);
			context.waitTicks(2);
			context.takeScreenshot("rings-15-construct-wheel");
			context.getInput().releaseKey(options -> RingsClient.constructKey);
			context.waitTicks(3);
			connection.waitForServerboundPackets();
			checkClient(context, mc -> mc.gui.screen() == null, "letting go of the key closes the wheel");
			check(server, p -> isHardLight(p, new BlockPos(0, -60, 3), RingColor.GREEN) && isHardLight(p, new BlockPos(2, -57, 3), RingColor.GREEN),
					"picking Wall makes a wall of hard light");
			check(server, p -> RingItem.charge(RingAttachments.getRing(p)) < RingItem.MAX_CHARGE, "constructs use power");

			// Cage the pig beyond the wall, from above it
			server.runCommand("setblock 0 -60 3 air");
			server.runCommand("tp @a 0.5 -48 0.5");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			context.waitTicks(3);
			context.getInput().lookAt(new BlockPos(0, -60, 6));
			context.waitTicks(12);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.CAGE));
			check(server, p -> isHardLight(p, new BlockPos(1, -60, 6), RingColor.GREEN) && isHardLight(p, new BlockPos(-1, -60, 6), RingColor.GREEN)
					&& isHardLight(p, new BlockPos(0, -59, 6), RingColor.GREEN), "the cage closes around the pig");

			// A bridge out from where the player hovers
			context.waitTicks(12);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.BRIDGE));
			check(server, p -> isHardLight(p, new BlockPos(0, -49, 8), RingColor.GREEN) && isHardLight(p, new BlockPos(1, -49, 15), RingColor.GREEN),
					"the bridge reaches out ahead");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			TestCamera.yawOffset = 140.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-15-constructs");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.gui.hud.toggle());

			// Stairs climbing ahead, a dome over the player, a grab and a hammer at pigs below
			context.waitTicks(12);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.STAIRS));
			check(server, p -> isHardLight(p, new BlockPos(0, -42, 7), RingColor.GREEN) && isHardLight(p, new BlockPos(1, -40, 9), RingColor.GREEN),
					"the stairs climb ahead");
			context.waitTicks(12);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.DOME));
			check(server, p -> isHardLight(p, new BlockPos(4, -48, 0), RingColor.GREEN) && isHardLight(p, new BlockPos(0, -44, 0), RingColor.GREEN),
					"the dome closes over the player");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			TestCamera.yawOffset = 140.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-15-stairs-dome");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.gui.hud.toggle());

			server.runCommand("tp @a 0.5 -40 30.5");
			context.waitTicks(12);
			server.runCommand("summon minecraft:pig 9.5 -60 9.5");
			context.waitTicks(2);
			context.getInput().lookAt(new BlockPos(9, -60, 9));
			context.waitTicks(2);
			double before = server.computeOnServer(s -> player(s).level().getEntitiesOfClass(Pig.class, new AABB(4, -64, 4, 14, -40, 14)).getFirst().distanceTo(player(s)));
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.GRAB));
			context.waitTicks(8);
			check(server, p -> p.level().getEntitiesOfClass(Pig.class, new AABB(-20, -64, -20, 40, -30, 40), pig -> pig.distanceTo(p) < before - 3.0).size() == 1,
					"the grab yanks the pig toward the player");
			context.waitTicks(4);
			server.runCommand("summon minecraft:pig -7.5 -60 12.5");
			context.waitTicks(2);
			context.getInput().lookAt(new BlockPos(-8, -60, 12));
			context.waitTicks(2);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.HAMMER));
			context.runOnClient(mc -> mc.gui.hud.toggle());
			TestCamera.yawOffset = 60.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-15-hammer");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.waitTicks(10);
			check(server, p -> p.level().getEntitiesOfClass(Pig.class, new AABB(-14, -64, 6, -2, -40, 18), pig -> pig.getHealth() >= pig.getMaxHealth()).isEmpty(),
					"the hammer smashes the pig");
			server.runCommand("kill @e[type=minecraft:pig]");

			// The giant fist, at a pig behind
			server.runCommand("summon minecraft:pig 0.5 -60 -8.5 {NoAI:1b}");
			server.runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(3);
			context.getInput().lookAt(new BlockPos(0, -60, -9));
			context.waitTicks(12);
			context.runOnClient(mc -> mc.gui.hud.toggle());
			TestCamera.yawOffset = 70.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.FIST));
			context.waitTicks(2);
			context.takeScreenshot("rings-15-fist");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.waitTicks(10);
			check(server, p -> p.level().getEntitiesOfClass(Pig.class, new AABB(-20, -64, -20, 20, -40, 0), pig -> pig.getHealth() >= pig.getMaxHealth()).isEmpty(),
					"the giant fist smashes the pig");

			// Construct tools: in hand, and gone when dropped or when the ring switches off
			server.runCommand("kill @e[type=minecraft:pig]");
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.SWORD));
			check(server, p -> p.getMainHandItem().is(Rings.CONSTRUCT_SWORD), "the sword construct goes in hand");
			context.waitTicks(12);
			server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.PICKAXE));
			check(server, p -> p.getInventory().contains(stack -> stack.is(Rings.CONSTRUCT_PICKAXE)), "the pickaxe construct goes in the inventory");
			context.waitTicks(3);
			context.takeScreenshot("rings-15-sword");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.level().addFreshEntity(new ItemEntity(p.level(), p.getX(), p.getY() + 1.0, p.getZ() + 2.0, p.getMainHandItem().copyAndClear()));
			});
			context.waitTicks(3);
			check(server, p -> !p.getInventory().contains(stack -> stack.is(Rings.CONSTRUCT_SWORD))
					&& p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8), e -> e.getItem().is(Rings.CONSTRUCT_SWORD)).isEmpty(),
					"a dropped construct fades away");
			activate(context, connection);
			context.waitTicks(2);
			check(server, p -> !p.getInventory().contains(stack -> stack.is(Rings.CONSTRUCT_PICKAXE)), "construct tools fade when the ring switches off");

			// Hard light fades on its own: the cage after 15 seconds, the wall after 30
			server.runCommand("tick sprint 700");
			for (int i = 0; i < 100 && server.computeOnServer(s -> isHardLight(player(s), new BlockPos(2, -57, 3), RingColor.GREEN)); i++) {
				context.waitTicks(10);
			}
			check(server, p -> !isHardLight(p, new BlockPos(2, -57, 3), RingColor.GREEN) && !isHardLight(p, new BlockPos(1, -60, 6), RingColor.GREEN),
					"hard light fades away");
			server.runCommand("tick sprint stop");

			// Every colour: its rings, its lantern, and its suit with its outline and power meter
			server.runCommand("clear @a");
			server.runCommand("setblock 0 -60 2 air");
			for (RingColor color : RingColor.values()) {
				server.runCommand("give @a rings:" + color.ringId());
				server.runCommand("setblock " + (color.ordinal() - 3) + " -60 3 rings:" + color.lanternId());
			}
			server.runCommand("tp @a 0 -60 0 0 25");
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			context.takeScreenshot("rings-11-colours-inventory");
			context.setScreen(() -> null);
			context.waitTicks(3);
			context.takeScreenshot("rings-11-colours-lanterns");
			server.runCommand("tp @a 0 -60 0 180 0");
			for (RingColor color : RingColor.values()) {
				server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.ring(color))));
				activate(context, connection);
				check(server, p -> color.costume().equals(p.getAttached(RingAttachments.COSTUME)), color.id() + " ring wears the " + color.id() + " suit");
				connection.waitForClientboundPackets();
				camera(context, CameraType.THIRD_PERSON_FRONT);
				context.waitTicks(30);
				context.takeScreenshot("rings-12-suit-" + color.id());
				context.getInput().holdKey(options -> RingsClient.beamKey);
				TestCamera.yawOffset = 70.0F;
				camera(context, CameraType.THIRD_PERSON_BACK);
				context.waitTicks(5);
				context.takeScreenshot("rings-14-beam-" + color.id());
				context.getInput().releaseKey(options -> RingsClient.beamKey);
				TestCamera.yawOffset = 0.0F;
				activate(context, connection);
				camera(context, CameraType.FIRST_PERSON);
			}
			server.runCommand("clear @a");

			// Up into space, to each planet and home again
			exploreSpace(context, connection, server);
			server.runCommand("clear @a");

			// Dying drops the ring like the rest of the inventory
			server.runCommand("clear @a");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
			server.runCommand("kill @a");
			context.waitTicks(2);
			check(server, p -> RingAttachments.getRing(p).isEmpty(), "the ring leaves the ring slot on death");
			check(server, p -> !p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16),
					e -> e.getItem().is(GREEN_RING)).isEmpty(), "the ring drops on death");
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(5);

			// Creative inventory: the ring slot sits right of the armor, and the server accepts it
			server.runCommand("gamemode creative @a");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
			connection.waitForClientboundPackets();
			context.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				return new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false);
			});
			context.waitTick();
			context.runOnClient(RingsClientGameTest::selectInventoryTab);
			context.waitTicks(2);
			checkClient(context, mc -> {
				for (Slot slot : ((CreativeModeInventoryScreen) mc.gui.screen()).getMenu().slots) {
					if (slot.container instanceof xyz.ssander.rings.RingContainer) {
						return slot.x == 127 && slot.y == 20;
					}
				}
				return false;
			}, "creative inventory places the ring slot right of the armor");
			context.takeScreenshot("rings-10-creative-inventory");

			context.runOnClient(mc -> mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(ringSlot, new ItemStack(Items.DIRT))));
			connection.waitForServerboundPackets();
			check(server, p -> RingAttachments.getRing(p).is(GREEN_RING), "creative can't put a non-ring in the ring slot");
			context.runOnClient(mc -> mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(ringSlot, ItemStack.EMPTY)));
			connection.waitForServerboundPackets();
			check(server, p -> RingAttachments.getRing(p).isEmpty(), "creative can clear the ring slot");
			context.setScreen(() -> null);
		}
	}

	/**
	 * Space: up from the Earth into space, into the Moon, Mars and Oa, back out and home. Checks gravity and air on
	 * the way, and films each place. Run on its own with RINGS_SPACE=1.
	 */
	private static void exploreSpace(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("clear @a");
		server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(3);
		activate(context, connection);
		check(server, RingAttachments::isActive, "the ring is on for the trip");

		// Up and out: flying above the edge of space over the Earth
		server.runCommand("tp @a 0.5 455 0.5");
		fly(context, connection, server);
		context.waitTicks(5);
		check(server, p -> p.level().dimension() == Space.SPACE, "flying high enough leaves for space");
		check(server, p -> p.getAttributeValue(Attributes.GRAVITY) < 0.03, "space has little gravity");
		connection.waitForChunksRender();
		context.runOnClient(mc -> mc.gui.hud.toggle());
		context.runOnClient(mc -> { mc.player.setYRot(180.0F); mc.player.setXRot(-10.0F); });
		context.waitTicks(5);
		context.takeScreenshot("space-1-moon-ahead");
		context.runOnClient(mc -> mc.player.setXRot(75.0F));
		context.waitTicks(3);
		context.takeScreenshot("space-2-earth-below");
		context.runOnClient(mc -> { mc.player.setYRot(-70.0F); mc.player.setXRot(-5.0F); });
		context.waitTicks(3);
		context.takeScreenshot("space-3-mars");
		context.runOnClient(mc -> { mc.player.setYRot(60.0F); mc.player.setXRot(-8.0F); });
		context.waitTicks(3);
		context.takeScreenshot("space-4-oa");
		context.runOnClient(mc -> mc.gui.hud.toggle());

		// Into each planet, look around, and back up to space
		for (Space.Planet planet : Space.Planet.values()) {
			Vec3 centre = planet.centre();
			server.runCommand("execute in rings:space run tp @a " + centre.x + " " + centre.y + " " + centre.z);
			context.waitTicks(5);
			check(server, p -> p.level().dimension() == planet.dimension(), "flying into " + planet.id() + " lands on it");
			fly(context, connection, server);
			server.runOnServer(s -> player(s).teleportTo(0.5, 200.0, 0.5));
			for (int i = 0; i < 400 && !server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ()) > p.level().getMinY() + 1;
			}); i++) {
				context.waitTick();
			}
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int ground = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
				p.teleportTo(p.getX(), ground + 12, p.getZ());
			});
			connection.waitForChunksRender();
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.runOnClient(mc -> { mc.player.setYRot(0.0F); mc.player.setXRot(15.0F); });
			context.waitTicks(20);
			context.takeScreenshot("space-5-" + planet.id() + "-ground");
			context.runOnClient(mc -> mc.player.setXRot(-40.0F));
			for (float yaw : new float[] {0.0F, 120.0F, 240.0F}) {
				context.runOnClient(mc -> mc.player.setYRot(yaw));
				context.waitTicks(3);
				context.takeScreenshot("space-6-" + planet.id() + "-sky-" + (int) yaw);
			}
			context.runOnClient(mc -> mc.gui.hud.toggle());
			if (planet == Space.Planet.MOON) {
				check(server, p -> Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08 / 6.0) < 0.001, "the Moon has a sixth of the Earth's gravity");
				// No air without the ring
				activate(context, connection);
				context.waitTicks(40);
				check(server, p -> p.getAirSupply() < p.getMaxAirSupply() - 20, "without the ring there's no air on the Moon");
				activate(context, connection);
				fly(context, connection, server);
			}
			server.runCommand("execute as @a at @s run tp @s ~ 405 ~");
			context.waitTicks(5);
			check(server, p -> p.level().dimension() == Space.SPACE, "flying up from " + planet.id() + " goes back to space");
		}

		// Home: down through the Earth's atmosphere, to where the trip started
		server.runCommand("execute in rings:space run tp @a 0.5 -2 0.5");
		context.waitTicks(5);
		check(server, p -> p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && Math.abs(p.getX() - 0.5) < 2 && Math.abs(p.getZ() - 0.5) < 2,
				"diving down to the Earth comes home where the trip started");
		activate(context, connection);
		server.runCommand("tp @a 0.5 -60 0.5");
		context.waitTicks(3);
	}

	/** The ring on the hand, off and on, and elytra mode and super flight. Run on its own with RINGS_FLIGHT=1. */
	private static void filmFlight(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			TestServerContext server = world.getServer();
			connection.waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
			server.runCommand("tp @a 0 -60 0 0 0");
			context.waitTicks(10);
			context.takeScreenshot("flight-ring-first-person");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			// Close up on the ring hand
			context.runOnClient(mc -> mc.options.fov().set(30));
			TestCamera.yawOffset = -35.0F;
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("flight-ring-off");
			activate(context, connection);
			context.waitTicks(30);
			context.takeScreenshot("flight-ring-on");
			camera(context, CameraType.FIRST_PERSON);
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.takeScreenshot("flight-ring-on-first-person");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			context.runOnClient(mc -> mc.options.fov().set(70));
			TestCamera.yawOffset = 90.0F;
			camera(context, CameraType.THIRD_PERSON_BACK);
			server.runCommand("tp @a 0 -40 0 0 0");
			fly(context, connection, server);
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.waitTicks(15);
			checkClient(context, mc -> mc.player.isFallFlying(), "sprinting forward switches into elytra mode");
			context.takeScreenshot("flight-elytra-side");
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(30);
			context.takeScreenshot("flight-super-side");
			TestCamera.yawOffset = 150.0F;
			context.waitTick();
			context.takeScreenshot("flight-super-front-quarter");
			TestCamera.yawOffset = 0.0F;
			camera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("flight-super-first-person");
			context.getInput().releaseKey(options -> options.keyShift);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(10);
			checkClient(context, mc -> !mc.player.isFallFlying() && mc.player.getAbilities().flying, "letting go goes back to hovering");
		}
	}

	/** Flying on the ring, as if the player had taken off. */
	private static void fly(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		connection.waitForClientboundPackets();
		context.waitTicks(2);
	}

	/** Quick look at each ring animation, mid-pose, from the side, front and back. Run with RINGS_POSES=1. */
	private static void filmPoses(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			TestServerContext server = world.getServer();
			connection.waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
			server.runCommand("tp @a 0 -60 0 0 0");
			activate(context, connection);
			context.runOnClient(mc -> mc.gui.hud.toggle());
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(30);

			film(context, RingAnimations.ACTIVATE, 10);
			film(context, RingAnimations.CHARGE, 8);
			film(context, RingAnimations.CHARGE_FULL, 9);
			film(context, RingAnimations.LAND, 4);
			TestCamera.yawOffset = 0.0F;
			// At night a suited-up player lights up their surroundings (LambDynamicLights)
			server.runCommand("time set midnight");
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.waitTicks(40);
			context.takeScreenshot("pose-night-suited");
			activate(context, connection);
			context.waitTicks(40);
			context.takeScreenshot("pose-night-unsuited");
		}
	}

	private static void film(ClientGameTestContext context, Identifier animation, int ticks) {
		context.runOnClient(mc -> RingAnimations.play(mc.player, animation));
		context.waitTicks(ticks);
		for (float yaw : new float[] {90.0F, 180.0F, 270.0F, 0.0F}) {
			TestCamera.yawOffset = yaw;
			context.waitTick();
			context.takeScreenshot("pose-" + animation.getPath() + "-" + (int) yaw);
		}
		TestCamera.yawOffset = 0.0F;
		context.waitTicks(40);
	}

	private static boolean isHardLight(ServerPlayer player, BlockPos pos, RingColor color) {
		BlockState state = player.level().getBlockState(pos);
		return state.is(Rings.HARD_LIGHT) && state.getValue(HardLightBlock.COLOR) == color;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void selectInventoryTab(Minecraft mc) {
		try {
			Method selectTab = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
			selectTab.setAccessible(true);
			selectTab.invoke(mc.gui.screen(), BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INVENTORY));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void quickMove(ClientGameTestContext context, int slot) {
		context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player));
	}

	private static void activate(ClientGameTestContext context, TestServerConnection connection) {
		context.getInput().pressKey(RingsClient.activateKey);
		connection.waitForServerboundPackets();
		context.waitTick();
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(mc -> mc.options.setCameraType(type));
		context.waitTicks(3);
	}

	private static void check(TestServerContext server, Function<ServerPlayer, Boolean> condition, String what) {
		if (!server.computeOnServer(s -> condition.apply(player(s)))) {
			throw new AssertionError("Expected: " + what);
		}
	}

	private static void checkClient(ClientGameTestContext context, Function<Minecraft, Boolean> condition, String what) {
		if (!context.computeOnClient(condition::apply)) {
			throw new AssertionError("Expected: " + what);
		}
	}
}
