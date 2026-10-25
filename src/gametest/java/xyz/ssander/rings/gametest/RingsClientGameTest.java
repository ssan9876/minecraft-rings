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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingSlot;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.client.RingsClient;

/** Plays through the ring: equip, activate, fly, costume, take off, death drop, creative inventory. */
public class RingsClientGameTest implements FabricClientGameTest {
	private static final Identifier LANTERN = Rings.id("lantern");

	@Override
	public void runTest(ClientGameTestContext context) {
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
			check(server, p -> RingAttachments.getRing(p).is(Rings.HERO_RING), "shift-click puts the ring in the ring slot");
			context.waitTicks(2);
			context.takeScreenshot("rings-2-equipped");
			context.setScreen(() -> null);

			// Activate: flight and costume, synced to the client
			activate(context, connection);
			check(server, p -> RingAttachments.isActive(p) && p.getAbilities().mayfly, "active ring grants flight");
			check(server, p -> LANTERN.equals(p.getAttached(RingAttachments.COSTUME)), "active ring sets the costume");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> LANTERN.equals(mc.player.getAttached(RingAttachments.COSTUME)), "costume syncs to the client");

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
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.waitTicks(12);
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
			camera(context, CameraType.FIRST_PERSON);

			// Deactivate in mid-air: flight goes, slow falling catches the player
			activate(context, connection);
			check(server, p -> !RingAttachments.isActive(p) && !p.getAbilities().mayfly, "deactivating removes flight");
			check(server, p -> p.getAttached(RingAttachments.COSTUME) == null, "deactivating removes the costume");
			check(server, p -> p.hasEffect(MobEffects.SLOW_FALLING), "losing flight mid-air gives slow falling");
			connection.waitForClientboundPackets();
			checkClient(context, mc -> mc.player.getAttached(RingAttachments.COSTUME) == null, "costume removal syncs to the client");
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rings-9-deactivated");
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

			// Dying drops the ring like the rest of the inventory
			server.runCommand("clear @a");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.HERO_RING)));
			server.runCommand("kill @a");
			context.waitTicks(2);
			check(server, p -> RingAttachments.getRing(p).isEmpty(), "the ring leaves the ring slot on death");
			check(server, p -> !p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16),
					e -> e.getItem().is(Rings.HERO_RING)).isEmpty(), "the ring drops on death");
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(5);

			// Creative inventory: the ring slot sits right of the armor, and the server accepts it
			server.runCommand("gamemode creative @a");
			server.runOnServer(s -> RingAttachments.setRing(player(s), new ItemStack(Rings.HERO_RING)));
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
			check(server, p -> RingAttachments.getRing(p).is(Rings.HERO_RING), "creative can't put a non-ring in the ring slot");
			context.runOnClient(mc -> mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(ringSlot, ItemStack.EMPTY)));
			connection.waitForServerboundPackets();
			check(server, p -> RingAttachments.getRing(p).isEmpty(), "creative can clear the ring slot");
			context.setScreen(() -> null);
		}
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
