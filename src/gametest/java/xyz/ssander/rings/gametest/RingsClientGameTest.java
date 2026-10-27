package xyz.ssander.rings.gametest;

import java.lang.reflect.Method;
import java.util.List;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.ColorPowers;
import xyz.ssander.rings.ConstructBeast;
import xyz.ssander.rings.ConstructJet;
import xyz.ssander.rings.EnergyBolt;
import xyz.ssander.rings.Manhunter;
import xyz.ssander.rings.Nekron;
import xyz.ssander.rings.OaCitadel;
import xyz.ssander.rings.OaGuardian;
import xyz.ssander.rings.Parallax;
import xyz.ssander.rings.ForceBubble;
import xyz.ssander.rings.MobMoods;
import xyz.ssander.rings.Thralls;
import xyz.ssander.rings.HardLightBlock;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingBeam;
import xyz.ssander.rings.PlanetBlocks;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingComponents;
import xyz.ssander.rings.RingConstructs;
import xyz.ssander.rings.RingItem;
import xyz.ssander.rings.RingProtection;
import xyz.ssander.rings.RingSlot;
import xyz.ssander.rings.Space;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.WillShardItem;
import xyz.ssander.rings.client.ConstructWheelScreen;
import xyz.ssander.rings.client.FriendScreen;
import xyz.ssander.rings.client.RingAnimations;
import xyz.ssander.rings.client.RingFlight;
import xyz.ssander.rings.client.RingScan;
import xyz.ssander.rings.client.RingsClient;
import xyz.ssander.rings.client.SuperFlight;

/** Plays through the ring: equip, activate, fly, land, costume, charge, every colour, take off, death drop, creative inventory. */
public class RingsClientGameTest implements FabricClientGameTest {
	private static final Identifier LANTERN = RingColor.GREEN.costume();
	private static final RingItem GREEN_RING = Rings.ring(RingColor.GREEN);

	@Override
	public void runTest(ClientGameTestContext context) {
		// Silent: the tests run on a real desktop
		context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		if (System.getenv("RINGS_POSES") != null) {
			filmPoses(context);
			return;
		}
		if (System.getenv("RINGS_OA") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				oaAndFoes(context, world.getConnection(), world.getServer());
			}
			return;
		}
		if (System.getenv("RINGS_CREATURES") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				constructCreatures(context, world.getConnection(), world.getServer());
			}
			return;
		}
		if (System.getenv("RINGS_COLORS") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				colorPowers(context, world.getConnection(), world.getServer());
			}
			return;
		}
		if (System.getenv("RINGS_POWERS") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				ringPowers(context, world.getConnection(), world.getServer());
			}
			return;
		}
		if (System.getenv("RINGS_GEAR") != null) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				constructGear(context, world.getConnection(), world.getServer());
			}
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
			context.waitTicks(70);
			context.takeScreenshot("rings-9-charging-oath");
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
				return new double[] {mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0 - (88 + 12) * scale};
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

			// Force field, bubble, scan, the save from death, and rings finding their way back
			ringPowers(context, connection, server);

			// Each colour's own power
			colorPowers(context, connection, server);

			// Construct creatures and the jet
			constructCreatures(context, connection, server);

			// Oa, the Guardians, and the enemies among the stars
			oaAndFoes(context, connection, server);

			// With nobody else about for the ring to choose, it stays with its wearer through death
			server.runCommand("clear @a");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
			server.runCommand("kill @a");
			context.waitTicks(2);
			check(server, p -> p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16),
					e -> e.getItem().is(GREEN_RING)).isEmpty(), "the ring doesn't drop on death");
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(5);
			check(server, p -> RingAttachments.getRing(p).is(GREEN_RING), "the ring is still worn after respawning");

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
				// The ground: the first solid block down from the sky (the heightmap can still be catching up)
				int ground = p.level().getMaxY();
				while (ground > p.level().getMinY() && p.level().getBlockState(new BlockPos(p.getBlockX(), ground, p.getBlockZ())).isAir()) {
					ground--;
				}
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
			// From high above, looking straight down: the Moon's craters, Mars's spires, Oa's crystals
			server.runOnServer(s -> player(s).teleportTo(player(s).getX(), player(s).getY() + 45.0, player(s).getZ()));
			connection.waitForChunksRender();
			context.runOnClient(mc -> mc.player.setXRot(90.0F));
			context.waitTicks(20);
			context.takeScreenshot("space-7-" + planet.id() + "-from-above");
			context.runOnClient(mc -> mc.gui.hud.toggle());
			checkPlanetBlocks(server, planet);
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

	/** Each planet is made of its own rock, with its own ores in it. */
	private static void checkPlanetBlocks(TestServerContext server, Space.Planet planet) {
		List<Block> expected = switch (planet) {
			case MOON -> List.of(PlanetBlocks.MOON_DUST, PlanetBlocks.MOON_ROCK, PlanetBlocks.MOON_IRON_ORE);
			case MARS -> List.of(PlanetBlocks.MARS_ROCK, PlanetBlocks.MARS_IRON_ORE);
			case OA -> List.of(PlanetBlocks.OAN_STONE, PlanetBlocks.OAN_EMERALD_ORE);
		};
		for (Block block : expected) {
			check(server, p -> {
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (int x = -32; x <= 32; x++) {
					for (int z = -32; z <= 32; z++) {
						for (int y = p.level().getMinY(); y < p.level().getMaxY(); y++) {
							if (p.level().getBlockState(pos.set(p.getBlockX() + x, y, p.getBlockZ() + z)).is(block)) {
								return true;
							}
						}
					}
				}
				return false;
			}, planet.id() + " has " + block.getDescriptionId());
		}
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

	/**
	 * The ring's powers on their own keys, and the ring looking after itself: the force field, a bubble carrying a
	 * pig into space, the scan, the save from a fatal hit, and a ring finding its way back. Run on its own with
	 * RINGS_POWERS=1.
	 */
	private static void ringPowers(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");
		server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(GREEN_RING)));
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(3);
		activate(context, connection);
		check(server, RingAttachments::isActive, "the ring is on");

		// The force field takes hits in place of the wearer, out of the ring's charge
		context.getInput().pressKey(RingsClient.forceFieldKey);
		connection.waitForServerboundPackets();
		context.waitTick();
		check(server, RingProtection::isShielded, "the force field key puts the force field up");
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
		context.waitTicks(20);
		int before = server.computeOnServer(s -> RingItem.charge(RingAttachments.getRing(player(s))));
		server.runOnServer(s -> player(s).hurtServer(player(s).level(), player(s).damageSources().generic(), 6.0F));
		check(server, p -> p.getHealth() == p.getMaxHealth(), "the force field stops the hit");
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) < before, "stopping it uses the ring's charge");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		context.takeScreenshot("powers-1-shield-first-person");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 140.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("powers-1-shield");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		context.getInput().pressKey(RingsClient.forceFieldKey);
		connection.waitForServerboundPackets();
		context.waitTick();
		check(server, p -> !RingProtection.isShielded(p), "pressing it again takes the force field down");

		// A bubble round a pig, carried up into space
		server.runCommand("summon minecraft:pig 0.5 -60 5.5 {NoAI:1b}");
		context.waitTicks(2);
		context.getInput().lookAt(new BlockPos(0, -60, 5));
		context.waitTicks(2);
		context.getInput().pressKey(RingsClient.forceFieldKey);
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(12), pig -> pig.getVehicle() instanceof ForceBubble).size() == 1,
				"looking at a creature puts it in a bubble");
		server.runCommand("tp @a 0.5 -50 0.5 0 0");
		fly(context, connection, server);
		context.waitTicks(20);
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(4), pig -> pig.getVehicle() instanceof ForceBubble).size() == 1,
				"the bubble follows the wearer");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 160.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("powers-2-bubble");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runCommand("tp @a 0.5 455 0.5");
		fly(context, connection, server);
		context.waitTicks(10);
		check(server, p -> p.level().dimension() == Space.SPACE, "the wearer flies into space");
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(32), pig -> pig.getVehicle() instanceof ForceBubble).size() == 1,
				"the pig in its bubble comes too");
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(32)).getFirst().getAirSupply() > 0, "the bubble keeps the pig breathing");
		context.getInput().pressKey(RingsClient.forceFieldKey);
		connection.waitForServerboundPackets();
		context.waitTicks(2);
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(32), pig -> pig.getVehicle() == null).size() == 1,
				"pressing it again lets the pig out");
		server.runCommand("kill @e[type=minecraft:pig]");
		server.runCommand("execute in minecraft:overworld run tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(5);

		// The scan: creatures outlined through walls, ores glowing through the rock, and a readout of what's looked at
		server.runCommand("fill -2 -64 6 2 -62 8 minecraft:stone");
		server.runCommand("setblock 0 -63 7 minecraft:diamond_ore");
		server.runCommand("setblock 1 -63 7 minecraft:gold_ore");
		server.runCommand("summon minecraft:zombie 0.5 -60 9.5 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(3);
		context.getInput().lookAt(new BlockPos(0, -60, 9));
		context.waitTicks(2);
		context.getInput().pressKey(RingsClient.scanKey);
		connection.waitForServerboundPackets();
		connection.waitForClientboundPackets();
		context.waitTicks(25);
		checkClient(context, mc -> RingScan.isScanning(), "the scan key sends out a scan");
		checkClient(context, mc -> RingScan.oreCount() >= 2, "the scan finds the ores nearby");
		checkClient(context, mc -> mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(16)).stream().allMatch(z -> RingScan.outline(z) == 0xFF4040),
				"the scan outlines hostile creatures in red");
		context.takeScreenshot("powers-3-scan-readout");
		context.runOnClient(mc -> mc.player.setXRot(50.0F));
		context.waitTicks(2);
		context.takeScreenshot("powers-3-scan-ores");
		server.runCommand("kill @e[type=minecraft:zombie]");

		// A fatal hit: the ring saves its wearer, at a cost
		server.runOnServer(s -> RingAttachments.getRing(player(s)).set(RingComponents.CHARGE, RingItem.MAX_CHARGE));
		server.runOnServer(s -> player(s).hurtServer(player(s).level(), player(s).damageSources().generic(), 1000.0F));
		check(server, p -> p.isAlive() && p.getHealth() > 0.0F, "the ring saves its wearer from a fatal hit");
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) <= RingItem.MAX_CHARGE - RingProtection.SAVE_COST, "saving them uses a quarter of the ring");
		context.waitTicks(10);

		// A ring dropped into the void comes straight back, and one left lying comes back after a while
		activate(context, connection);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ItemEntity drop = new ItemEntity(p.level(), p.getX(), p.level().getMinY() - 2.0, p.getZ(), RingAttachments.getRing(p).copy());
			drop.setThrower(p);
			RingAttachments.setRing(p, ItemStack.EMPTY);
			p.level().addFreshEntity(drop);
		});
		context.waitTicks(80);
		check(server, p -> RingAttachments.getRing(p).is(GREEN_RING), "a ring dropped into the void flies back to its wearer");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ItemEntity drop = new ItemEntity(p.level(), p.getX() + 6.0, p.getY() + 0.5, p.getZ(), RingAttachments.getRing(p).copy());
			drop.setThrower(p);
			drop.setPickUpDelay(40);
			RingAttachments.setRing(p, ItemStack.EMPTY);
			p.level().addFreshEntity(drop);
		});
		context.waitTicks(100);
		check(server, p -> RingAttachments.getRing(p).isEmpty(), "a dropped ring lies a while first");
		context.waitTicks(160);
		check(server, p -> RingAttachments.getRing(p).is(GREEN_RING), "a dropped ring left lying flies back to its wearer");

		// The yellow ring and its suit
		server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.ring(RingColor.YELLOW))));
		activate(context, connection);
		check(server, p -> RingColor.YELLOW.costume().equals(p.getAttached(RingAttachments.COSTUME)), "the yellow ring wears the yellow suit");
		activate(context, connection);
		server.runCommand("clear @a");
	}

	/** Each colour's own power and gift. Run on its own with RINGS_COLORS=1. */
	private static void colorPowers(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");

		// Green: a burst of will throws back what's near; constructs cost less
		wear(context, connection, server, RingColor.GREEN);
		server.runCommand("summon minecraft:pig 3.5 -60 0.5 {NoAI:1b}");
		server.runCommand("summon minecraft:pig -2.5 -60 2.5 {NoAI:1b}");
		context.waitTicks(3);
		power(context, connection);
		context.waitTicks(3);
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(16), pig -> pig.getHealth() >= pig.getMaxHealth()).isEmpty(),
				"the green ring's burst of will strikes what's near");
		filmPower(context, "green");
		check(server, p -> ColorPowers.constructCost(RingConstructs.Construct.WALL, RingColor.GREEN) < RingConstructs.Construct.WALL.cost(),
				"green rings make constructs for less");
		server.runCommand("kill @e[type=minecraft:pig]");

		// Yellow: a wave of terror sends creatures running
		wear(context, connection, server, RingColor.YELLOW);
		server.runCommand("summon minecraft:zombie 4.5 -60 4.5 {PersistenceRequired:1b}");
		context.waitTicks(3);
		power(context, connection);
		context.waitTicks(10);
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(32)).stream().allMatch(MobMoods::isAfraid),
				"the yellow ring's wave of terror frightens creatures");
		filmPower(context, "yellow");
		context.waitTicks(30);
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(32)).stream().allMatch(z -> z.distanceTo(p) > 7.0),
				"frightened creatures run away");
		server.runCommand("kill @e[type=minecraft:zombie]");

		// Red: burning plasma; rage gives strength and halves the ring's armor
		wear(context, connection, server, RingColor.RED);
		server.runCommand("summon minecraft:pig 0.5 -60 10.5 {NoAI:1b}");
		context.waitTicks(3);
		context.getInput().lookAt(new BlockPos(0, -60, 10));
		context.waitTicks(2);
		power(context, connection);
		context.waitTicks(3);
		filmPower(context, "red");
		context.waitTicks(15);
		check(server, p -> p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(16), pig -> pig.getHealth() < pig.getMaxHealth()).size() == 1,
				"the red ring's plasma hits what it's aimed at");
		check(server, p -> p.hasEffect(MobEffects.STRENGTH) && p.getAttributeValue(Attributes.ARMOR) == 4.0, "a red ring's rage gives strength for half the armor");
		server.runCommand("kill @e[type=minecraft:pig]");

		// Blue: hope heals
		wear(context, connection, server, RingColor.BLUE);
		server.runOnServer(s -> player(s).setHealth(8.0F));
		power(context, connection);
		check(server, p -> p.getHealth() > 12.0F, "the blue ring's hope heals");
		filmPower(context, "blue");

		// Orange: defeated creatures are claimed, and copies called up fight for the wearer
		wear(context, connection, server, RingColor.ORANGE);
		server.runCommand("summon minecraft:zombie 3.5 -60 3.5 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(3);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Zombie zombie = p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16)).getFirst();
			zombie.hurtServer(p.level(), p.damageSources().playerAttack(p), 1000.0F);
		});
		context.waitTicks(25);
		check(server, p -> RingAttachments.getRing(p).getOrDefault(RingComponents.CLAIMS, java.util.List.of()).contains(Identifier.withDefaultNamespace("zombie")),
				"the orange ring claims what its wearer defeats");
		server.runCommand("kill @e[type=minecraft:item]");
		power(context, connection);
		context.waitTicks(5);
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16), z -> Thralls.of(z) != null && Thralls.of(z).color() == RingColor.ORANGE).size() >= 1,
				"the orange ring calls up copies of what it's claimed");
		context.waitTicks(20);
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16), z -> z.getTarget() == p).isEmpty(),
				"the copies don't go for their master");
		connection.waitForClientboundPackets();
		checkClient(context, mc -> mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(16), z -> z.getAttached(Thralls.THRALL_COLOR) == RingColor.ORANGE).size() >= 1,
				"the copies' colour syncs to the client for their outline");
		filmPower(context, "orange");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (Zombie zombie : p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16))) {
				zombie.hurtServer(p.level(), p.damageSources().generic(), 1000.0F);
			}
		});
		context.waitTicks(3);
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16)).isEmpty()
				&& p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16), e -> e.getItem().is(Items.ROTTEN_FLESH)).isEmpty(),
				"raised creatures fade when they die, leaving nothing");
		server.runCommand("kill @e[type=minecraft:item]");

		// Pink: a crystal that charms; sneak for the list of friends to go to
		wear(context, connection, server, RingColor.PINK);
		server.runCommand("summon minecraft:pig 0.5 -60 6.5 {NoAI:1b}");
		context.waitTicks(3);
		context.getInput().lookAt(new BlockPos(0, -60, 6));
		context.waitTicks(2);
		power(context, connection);
		check(server, p -> isHardLight(p, new BlockPos(1, -60, 6), RingColor.PINK) && isHardLight(p, new BlockPos(0, -59, 6), RingColor.PINK),
				"the pink ring traps a creature in crystal");
		filmPower(context, "pink");
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(2);
		context.getInput().pressKey(RingsClient.powerKey);
		context.waitTicks(3);
		checkClient(context, mc -> mc.gui.screen() instanceof FriendScreen, "sneak and the ring power key opens the list of friends");
		context.takeScreenshot("colors-pink-friends");
		context.setScreen(() -> null);
		context.getInput().releaseKey(options -> options.keyShift);
		server.runCommand("kill @e[type=minecraft:pig]");

		// White: heals the wearer when nobody needs bringing back
		wear(context, connection, server, RingColor.WHITE);
		server.runOnServer(s -> player(s).setHealth(5.0F));
		power(context, connection);
		check(server, p -> p.getHealth() == p.getMaxHealth(), "the white ring heals its wearer");
		filmPower(context, "white");

		// Black: drains life, and what it kills rises to fight for it
		wear(context, connection, server, RingColor.BLACK);
		server.runCommand("summon minecraft:pig 3.5 -60 0.5 {NoAI:1b}");
		server.runCommand("summon minecraft:skeleton 0.5 -60 4.5 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(3);
		server.runOnServer(s -> player(s).setHealth(10.0F));
		power(context, connection);
		check(server, p -> p.getHealth() > 10.0F, "the black ring drains life into its wearer");
		filmPower(context, "black");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (LivingEntity victim : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> e != p)) {
				victim.hurtServer(p.level(), p.damageSources().playerAttack(p), 1000.0F);
			}
		});
		context.waitTicks(50);
		check(server, p -> p.level().getEntitiesOfClass(Skeleton.class, p.getBoundingBox().inflate(16), z -> Thralls.of(z) != null).size() == 1,
				"a skeleton killed by a black ring rises as a Black Lantern");
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16), z -> Thralls.of(z) != null).size() == 1,
				"a pig killed by a black ring rises as a zombie Black Lantern");
		connection.waitForClientboundPackets();
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 150.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("colors-black-lanterns");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		activate(context, connection);
		context.waitTicks(5);
		check(server, p -> p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> Thralls.of(m) != null).size() == 2,
				"Black Lanterns stay while their master's about");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");
		server.runCommand("clear @a");
	}

	/** Construct creatures fighting for their maker, and the jet flown up to space and back. Run on its own with RINGS_CREATURES=1. */
	private static void constructCreatures(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");
		wear(context, connection, server, RingColor.GREEN);

		// A wolf and a golem, which go for a zombie near by
		server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.WOLF));
		context.waitTicks(12);
		server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.GOLEM));
		check(server, p -> p.level().getEntitiesOfClass(ConstructBeast.class, p.getBoundingBox().inflate(8)).size() == 2, "the wheel makes a wolf and a golem");
		server.runOnServer(s -> player(s).level().getEntitiesOfClass(ConstructBeast.class, player(s).getBoundingBox().inflate(8))
				.forEach(beast -> beast.setNoAi(true)));
		context.runOnClient(mc -> mc.gui.hud.toggle());
		context.runOnClient(mc -> mc.player.setXRot(25.0F));
		context.waitTicks(5);
		context.takeScreenshot("creatures-0-made");
		TestCamera.yawOffset = 120.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("creatures-0-made-side");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.player.setXRot(0.0F));
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> player(s).level().getEntitiesOfClass(ConstructBeast.class, player(s).getBoundingBox().inflate(8))
				.forEach(beast -> beast.setNoAi(false)));
		server.runCommand("summon minecraft:zombie 0.5 -60 9.5 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(30);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 150.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("creatures-1-wolf-golem");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		for (int i = 0; i < 200 && !server.computeOnServer(s -> player(s).level().getEntitiesOfClass(Zombie.class, player(s).getBoundingBox().inflate(32)).isEmpty()); i++) {
			context.waitTick();
		}
		check(server, p -> p.level().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(32)).isEmpty(), "construct creatures fight for their maker");
		server.runCommand("kill @e[type=rings:construct_wolf]");
		server.runCommand("kill @e[type=rings:construct_golem]");
		server.runCommand("kill @e[type=minecraft:item]");

		// The jet: the maker climbs straight in to fly it, with a pig along for the ride
		server.runCommand("tp @a 0.5 -55 0.5 0 0");
		context.waitTicks(3);
		server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.JET));
		context.waitTicks(3);
		check(server, p -> p.getVehicle() instanceof ConstructJet, "making a jet puts its maker at the controls");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Pig pig = new Pig(net.minecraft.world.entity.EntityTypes.PIG, p.level());
			pig.snapTo(p.getX(), p.getY(), p.getZ());
			p.level().addFreshEntity(pig);
			pig.startRiding(p.getVehicle(), true, true);
		});
		context.waitTicks(3);
		check(server, p -> p.getVehicle().getPassengers().size() == 2, "the jet has room for more");
		connection.waitForClientboundPackets();
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 140.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("creatures-2-jet");
		double startY = server.computeOnServer(s -> player(s).getY());
		context.runOnClient(mc -> mc.player.setXRot(-30.0F));
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(40);
		TestCamera.yawOffset = 90.0F;
		context.waitTick();
		context.takeScreenshot("creatures-3-jet-flying");
		check(server, p -> p.getY() > startY + 10.0, "the jet flies where its pilot looks");

		// Straight up, sprinting, into space, and back down to the Earth
		context.runOnClient(mc -> mc.player.setXRot(-89.0F));
		context.getInput().holdKey(options -> options.keySprint);
		for (int i = 0; i < 600 && server.computeOnServer(s -> player(s).level().dimension() != Space.SPACE); i++) {
			context.waitTick();
		}
		check(server, p -> p.level().dimension() == Space.SPACE && p.getVehicle() instanceof ConstructJet, "the jet flies up into space with its pilot");
		check(server, p -> p.getVehicle().getPassengers().size() == 2, "the jet's passengers come too");
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("creatures-4-jet-space");
		context.runOnClient(mc -> mc.player.setXRot(89.0F));
		for (int i = 0; i < 600 && server.computeOnServer(s -> player(s).level().dimension() != net.minecraft.world.level.Level.OVERWORLD); i++) {
			context.waitTick();
		}
		check(server, p -> p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && p.getVehicle() instanceof ConstructJet, "the jet flies home");
		context.getInput().releaseKey(options -> options.keySprint);
		context.getInput().releaseKey(options -> options.keyUp);
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> player(s).stopRiding());
		context.waitTicks(3);
		check(server, p -> !p.isPassenger(), "the pilot can climb out");
		for (int i = 0; i < 300 && !server.computeOnServer(s -> player(s).level().getEntitiesOfClass(ConstructJet.class, player(s).getBoundingBox().inflate(64)).isEmpty()); i++) {
			context.waitTick();
		}
		check(server, p -> p.level().getEntitiesOfClass(ConstructJet.class, p.getBoundingBox().inflate(64)).isEmpty(), "a jet left alone fades");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");
		server.runCommand("tp @a 0.5 -60 0.5");
		activate(context, connection);
	}

	/**
	 * Oa's citadel and the Central Power Battery, the Guardians, Parallax set free, Manhunters in space, and Nekron
	 * woken on the Moon. Run on its own with RINGS_OA=1.
	 */
	private static void oaAndFoes(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=!minecraft:player]");
		// A clear test spot, whatever earlier tests left there
		server.runCommand("execute in minecraft:overworld run fill -12 -60 -12 12 -50 12 minecraft:air");
		wear(context, connection, server, RingColor.GREEN);

		// The first visit to Oa finds the citadel there
		server.runCommand("tp @a 0.5 455 0.5");
		fly(context, connection, server);
		context.waitTicks(5);
		Vec3 oa = Space.Planet.OA.centre();
		server.runCommand("execute in rings:space run tp @a " + oa.x + " " + oa.y + " " + oa.z);
		context.waitTicks(10);
		check(server, p -> p.level().dimension() == Space.Planet.OA.dimension(), "flying into Oa lands on it");
		int ground = server.computeOnServer(s -> OaCitadel.groundY(player(s).level()));
		check(server, p -> p.level().getBlockState(new BlockPos(0, ground + 6, 0)).is(Rings.POWER_BATTERY), "landing on Oa finds the Central Power Battery");
		check(server, p -> p.level().getEntitiesOfClass(OaGuardian.class, new AABB(-40, ground - 10, -40, 40, ground + 40, 40)).size() == 3,
				"the Guardians walk the citadel");
		server.runOnServer(s -> player(s).teleportTo(0.5, ground + 24, -52.5));
		fly(context, connection, server);
		server.runOnServer(s -> player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, ground + 10, 0.5)));
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.runOnClient(mc -> mc.gui.hud.toggle());
		context.takeScreenshot("oa-1-citadel");
		server.runOnServer(s -> player(s).teleportTo(0.5, ground + 1, -12.5));
		server.runOnServer(s -> player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, ground + 8, 0.5)));
		context.waitTicks(10);
		context.takeScreenshot("oa-2-battery");
		context.runOnClient(mc -> mc.gui.hud.toggle());

		// A Guardian gives a ring to a visitor who has none
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.removeAttached(RingAttachments.ACTIVE);
			RingAttachments.setRing(p, ItemStack.EMPTY);
			OaGuardian guardian = p.level().getEntitiesOfClass(OaGuardian.class, p.getBoundingBox().inflate(48)).getFirst();
			guardian.interact(p, InteractionHand.MAIN_HAND, guardian.position());
		});
		check(server, p -> p.getInventory().contains(stack -> stack.is(GREEN_RING)), "a Guardian gives a ring to a visitor without one");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			OaGuardian guardian = p.level().getEntitiesOfClass(OaGuardian.class, p.getBoundingBox().inflate(48)).getFirst();
			guardian.setNoAi(true);
			guardian.teleportTo(0.5, ground + 1, -9.5);
			guardian.setYRot(180.0F);
			guardian.setYHeadRot(180.0F);
			guardian.yBodyRot = 180.0F;
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, guardian.getEyePosition().subtract(0.0, 0.4, 0.0));
		});
		context.waitTicks(10);
		context.takeScreenshot("oa-2-guardian");
		context.runOnClient(mc -> mc.gui.hud.toggle());

		// The battery fills any ring at once
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			ItemStack ring = new ItemStack(Rings.ring(RingColor.RED));
			ring.set(RingComponents.CHARGE, 100);
			RingAttachments.setRing(p, ring);
			useBattery(p, ground);
		});
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == RingItem.MAX_CHARGE, "the Central Power Battery charges a ring of any colour full");

		// A yellow ring's touch sets Parallax free
		wear(context, connection, server, RingColor.YELLOW);
		server.runOnServer(s -> {
			player(s).teleportTo(0.5, ground + 1, -6.5);
			useBattery(player(s), ground);
		});
		context.waitTicks(5);
		check(server, p -> p.level().getEntitiesOfClass(Parallax.class, p.getBoundingBox().inflate(64)).size() == 1, "a yellow ring sets Parallax free");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Parallax parallax = p.level().getEntitiesOfClass(Parallax.class, p.getBoundingBox().inflate(64)).getFirst();
			parallax.hurtServer(p.level(), p.damageSources().playerAttack(p), 20.0F);
		});
		check(server, p -> p.level().getEntitiesOfClass(Parallax.class, p.getBoundingBox().inflate(64)).getFirst().getHealth() == 300.0F,
				"a yellow ring can't hurt Parallax");
		context.waitTicks(40);
		connection.waitForClientboundPackets();
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Parallax parallax = p.level().getEntitiesOfClass(Parallax.class, p.getBoundingBox().inflate(64)).getFirst();
			parallax.setNoAi(true);
			parallax.teleportTo(-10.5, ground + 30, 0.5);
			p.teleportTo(10.5, ground + 22, -14.5);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, parallax.position().add(0.0, 1.5, 0.0));
		});
		context.waitTicks(60);
		context.takeScreenshot("oa-3-parallax");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		check(server, p -> p.hasEffect(MobEffects.SLOWNESS) == false, "Parallax's fear spares yellow rings");
		server.runCommand("kill @e[type=rings:parallax]");
		context.waitTicks(30);
		check(server, p -> !p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64), e -> e.getItem().is(Rings.lantern(RingColor.YELLOW).asItem())).isEmpty(),
				"Parallax drops a yellow lantern");
		server.runCommand("kill @e[type=minecraft:item]");

		// Manhunters hunt ring-bearers in space
		wear(context, connection, server, RingColor.GREEN);
		server.runCommand("execute in rings:space run tp @a 0.5 150 0.5");
		context.waitTicks(5);
		fly(context, connection, server);
		server.runCommand("execute in rings:space run summon rings:manhunter 0.5 152 12.5");
		context.waitTicks(80);
		check(server, p -> p.level().getEntitiesOfClass(Manhunter.class, p.getBoundingBox().inflate(32)).stream().anyMatch(m -> m.getTarget() == p),
				"a Manhunter hunts the ring-bearer");
		check(server, p -> !p.level().getEntitiesOfClass(EnergyBolt.class, p.getBoundingBox().inflate(32)).isEmpty() || p.getHealth() < p.getMaxHealth(),
				"Manhunters shoot");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Manhunter manhunter = p.level().getEntitiesOfClass(Manhunter.class, p.getBoundingBox().inflate(32)).getFirst();
			manhunter.setNoAi(true);
			manhunter.teleportTo(p.getX(), p.getY(), p.getZ() + 4.0);
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, manhunter.getEyePosition().subtract(0.0, 0.6, 0.0));
		});
		context.waitTicks(5);
		context.takeScreenshot("oa-4-manhunter");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runCommand("execute in rings:space run kill @e[type=rings:manhunter]");
		for (int i = 0; i < 1500 && server.computeOnServer(s -> player(s).level().getEntitiesOfClass(Manhunter.class, player(s).getBoundingBox().inflate(64)).isEmpty()); i++) {
			context.waitTick();
		}
		check(server, p -> !p.level().getEntitiesOfClass(Manhunter.class, p.getBoundingBox().inflate(64)).isEmpty(), "Manhunters turn up in space");
		server.runCommand("execute in rings:space run kill @e[type=rings:manhunter]");

		// Charging a black ring full at a black lantern on the Moon wakes Nekron
		wear(context, connection, server, RingColor.BLACK);
		Vec3 moon = Space.Planet.MOON.centre();
		server.runCommand("execute in rings:space run tp @a " + moon.x + " " + moon.y + " " + moon.z);
		context.waitTicks(10);
		check(server, p -> p.level().dimension() == Space.Planet.MOON.dimension(), "back to the Moon");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			int y = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
			// A flat spot to stand on, with the lantern in front
			for (BlockPos pos : BlockPos.betweenClosed(-3, y - 1, -3, 3, y + 4, 5)) {
				p.level().setBlock(pos, pos.getY() == y - 1 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			}
			p.teleportTo(0.5, y, 0.5);
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.level().setBlock(new BlockPos(0, y, 2), Rings.lantern(RingColor.BLACK).defaultBlockState(), 3);
			RingAttachments.getRing(p).set(RingComponents.CHARGE, RingItem.MAX_CHARGE - 30);
		});
		connection.waitForChunksRender();
		context.waitTicks(20);
		int moonY = server.computeOnServer(s -> player(s).getBlockY());
		// Manhunters roam the Moon too, and one passing in front would take the click
		for (int attempt = 0; attempt < 3 && server.computeOnServer(s -> player(s).level().getEntitiesOfClass(Nekron.class, player(s).getBoundingBox().inflate(64)).isEmpty()); attempt++) {
			server.runCommand("execute in rings:moon run kill @e[type=rings:manhunter]");
			server.runOnServer(s -> RingAttachments.getRing(player(s)).set(RingComponents.CHARGE, RingItem.MAX_CHARGE - 30));
			context.getInput().lookAt(new BlockPos(0, moonY, 2));
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(20);
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(10);
		}
		check(server, p -> p.level().getEntitiesOfClass(Nekron.class, p.getBoundingBox().inflate(64)).size() == 1, "a black ring charged full on the Moon wakes Nekron");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Nekron nekron = p.level().getEntitiesOfClass(Nekron.class, p.getBoundingBox().inflate(64)).getFirst();
			nekron.hurtServer(p.level(), p.damageSources().playerAttack(p), 20.0F);
		});
		check(server, p -> p.level().getEntitiesOfClass(Nekron.class, p.getBoundingBox().inflate(64)).getFirst().getHealth() == 300.0F,
				"a black ring can't hurt Nekron");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Nekron nekron = p.level().getEntitiesOfClass(Nekron.class, p.getBoundingBox().inflate(64)).getFirst();
			nekron.setNoAi(true);
			nekron.teleportTo(p.getX(), p.getY(), p.getZ() + 8.0);
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, nekron.position().add(0.0, 2.5, 0.0));
		});
		context.waitTicks(20);
		context.takeScreenshot("oa-5-nekron");
		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runCommand("execute in rings:moon run kill @e[type=rings:nekron]");
		context.waitTicks(30);
		check(server, p -> !p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64), e -> e.getItem().is(Rings.ring(RingColor.WHITE))).isEmpty(),
				"Nekron drops a white ring");
		server.runCommand("execute in rings:moon run kill @e[type=!minecraft:player]");
		server.runCommand("execute in minecraft:overworld run tp @a 0.5 -60 0.5");
		context.waitTicks(5);
		activate(context, connection);
	}

	/** Lays the player's hand on the Central Power Battery's core. */
	private static void useBattery(ServerPlayer player, int ground) {
		BlockPos core = new BlockPos(0, ground + 6, 0);
		player.level().getBlockState(core).useWithoutItem(player.level(), player,
				new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(core), net.minecraft.core.Direction.NORTH, core, false));
	}

	/** The construct sword's slash, the construct shield, and a will shard topping up a ring. Run on its own with RINGS_GEAR=1. */
	private static void constructGear(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode survival @a");
		wear(context, connection, server, RingColor.GREEN);
		// Facing south, down the line the targets stand on
		context.runOnClient(mc -> {
			mc.player.setYRot(0.0F);
			mc.player.setXRot(0.0F);
		});
		context.waitTicks(5);
		server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.SWORD));
		context.waitTicks(12);
		server.runOnServer(s -> RingConstructs.make(player(s), RingConstructs.Construct.SHIELD));
		check(server, p -> p.getMainHandItem().is(Rings.CONSTRUCT_SWORD) && p.getOffhandItem().is(Rings.CONSTRUCT_SHIELD),
				"the sword goes in hand and the shield on the off hand");

		// The slash: cuts down a line of husks ahead (husks, as zombies would burn in the sun), wherever the wearer looks
		server.runCommand("summon minecraft:husk 0.5 -60 6.5 {NoAI:1b}");
		server.runCommand("summon minecraft:husk 1.5 -60 10.5 {NoAI:1b}");
		context.waitTicks(5);
		int charge = server.computeOnServer(s -> RingItem.charge(RingAttachments.getRing(player(s))));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setShiftKeyDown(true);
			Rings.CONSTRUCT_SWORD.use(p.level(), p, InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
		});
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 120.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("gear-1-slash");
		context.waitTicks(10);
		check(server, p -> p.level().getEntitiesOfClass(Husk.class, p.getBoundingBox().inflate(16), z -> z.getHealth() >= z.getMaxHealth()).isEmpty(),
				"the slash cuts both husks");
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) < charge, "the slash costs the ring some charge");
		server.runCommand("kill @e[type=minecraft:husk]");

		// The shield: up at once, takes a hit whole, and throws the attacker back
		// Holding right-click with the sword in hand raises the shield, rather than slashing
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(2);
		check(server, ServerPlayer::isBlocking, "the construct shield blocks straight away");
		// (Free to move this time: a mob with no AI can't be knocked about)
		server.runCommand("summon minecraft:husk 0.5 -60 2.0");
		context.waitTicks(2);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Husk zombie = p.level().getEntitiesOfClass(Husk.class, p.getBoundingBox().inflate(4)).getFirst();
			p.hurtServer(p.level(), p.damageSources().mobAttack(zombie), 6.0F);
		});
		context.waitTicks(1);
		context.takeScreenshot("gear-2-shield");
		TestCamera.yawOffset = 200.0F;
		context.waitTick();
		context.takeScreenshot("gear-2-shield-front");
		check(server, p -> p.getHealth() >= p.getMaxHealth(), "the shield takes the hit");
		context.waitTicks(4);
		check(server, p -> p.level().getEntitiesOfClass(Husk.class, p.getBoundingBox().inflate(16), z -> z.isAlive() && z.distanceTo(p) > 3.0F).size() == 1,
				"the shield throws the attacker back");
		server.runCommand("kill @e[type=minecraft:husk]");
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.takeScreenshot("gear-3-first-person-blocking");
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("gear-3-first-person");
		context.runOnClient(mc -> mc.gui.hud.toggle());

		// A will shard tops a ring up a quarter
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			RingAttachments.getRing(p).set(RingComponents.CHARGE, 100);
			p.getInventory().setItem(8, new ItemStack(PlanetBlocks.WILL_SHARD, 2));
			p.getInventory().setSelectedSlot(8);
			PlanetBlocks.WILL_SHARD.use(p.level(), p, InteractionHand.MAIN_HAND);
		});
		check(server, p -> RingItem.charge(RingAttachments.getRing(p)) == 100 + WillShardItem.CHARGE
				&& p.getInventory().getItem(8).getCount() == 1, "a will shard charges the ring a quarter and is used up");
	}

	/** Wears a ring of the colour, fully charged and switched on, standing at the test spot. */
	private static void wear(ClientGameTestContext context, TestServerConnection connection, TestServerContext server, RingColor color) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.removeAttached(RingAttachments.ACTIVE);
			p.setHealth(p.getMaxHealth());
			RingAttachments.setRing(p, new ItemStack(Rings.ring(color)));
			if (p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
				p.teleportTo(0.5, -60.0, 0.5);
			}
			p.setYRot(0.0F);
			p.setXRot(0.0F);
		});
		context.waitTicks(3);
		activate(context, connection);
		check(server, p -> RingAttachments.activeColor(p) == color, "the " + color.id() + " ring is on");
		context.waitTicks(25);
	}

	private static void power(ClientGameTestContext context, TestServerConnection connection) {
		context.getInput().pressKey(RingsClient.powerKey);
		connection.waitForServerboundPackets();
		context.waitTick();
	}

	private static void filmPower(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.toggle());
		TestCamera.yawOffset = 150.0F;
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.takeScreenshot("colors-" + name);
		TestCamera.yawOffset = 0.0F;
		camera(context, CameraType.FIRST_PERSON);
		context.runOnClient(mc -> mc.gui.hud.toggle());
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
			film(context, RingAnimations.CONSTRUCT, 5);
			film(context, RingAnimations.POWER, 7);
			film(context, RingAnimations.FIELD, 7);
			film(context, RingAnimations.SCAN, 6);
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
