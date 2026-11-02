package xyz.ssander.rings.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import xyz.ssander.rings.CrashPilot;
import xyz.ssander.rings.Devourer;
import xyz.ssander.rings.RingAttachments;
import xyz.ssander.rings.RingColor;
import xyz.ssander.rings.RingStyles;
import xyz.ssander.rings.Rings;
import xyz.ssander.rings.Space;
import xyz.ssander.rings.StrandedExplorer;

import static xyz.ssander.rings.gametest.RingsClientGameTest.activate;
import static xyz.ssander.rings.gametest.RingsClientGameTest.camera;
import static xyz.ssander.rings.gametest.RingsClientGameTest.check;
import static xyz.ssander.rings.gametest.RingsClientGameTest.player;

/** The ship's new shape, emblems, the Devourer's own model, the stranded explorer and /rings ruins. Run with RINGS_POLISH=1. */
final class PolishTest {
	private static final int GROUND = -44;

	private PolishTest() {
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

		emblem(context, connection, server);
		ship(context, connection, server);
		creatures(context, connection, server);
		ruins(context, server);
	}

	private static void emblem(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("tp @a 0.5 -60 0.5 180 0");
		server.runCommand("execute as @a run ring emblem star");
		check(server, p -> RingStyles.emblem(p) == RingStyles.Emblem.STAR, "an emblem can be chosen");
		connection.waitForClientboundPackets();
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(10);
		context.takeScreenshot("polish-1-emblem-star");
		server.runCommand("execute as @a run ring emblem skull");
		connection.waitForClientboundPackets();
		context.waitTicks(5);
		context.takeScreenshot("polish-1-emblem-skull");
		camera(context, CameraType.FIRST_PERSON);
		server.runCommand("execute as @a run ring emblem lantern");
	}

	private static void ship(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("fill -24 -60 -24 24 " + GROUND + " 24 stone");
		server.runCommand("fill -24 " + GROUND + " -24 24 " + GROUND + " 24 grass_block");
		server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 0");
		server.runCommand("execute as @a at @s run rings crashsite green");
		context.waitTicks(10);
		check(server, p -> !p.level().getEntitiesOfClass(CrashPilot.class, new AABB(p.blockPosition()).inflate(40)).isEmpty(), "the crash site is built");
		server.runCommand("gamemode spectator @a");
		for (int view = 0; view < 3; view++) {
			Vec3 eye = switch (view) {
				case 0 -> new Vec3(16.5, GROUND + 12, 16.5);
				case 1 -> new Vec3(-18.5, GROUND + 6, 2.5);
				default -> new Vec3(2.5, GROUND + 7, -18.5);
			};
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(eye.x, eye.y, eye.z);
				p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, GROUND - 1, 0.5));
			});
			connection.waitForClientboundPackets();
			context.waitTicks(30);
			context.takeScreenshot("polish-2-ship-" + view);
		}
		server.runCommand("gamemode survival @a");
	}

	private static void creatures(ClientGameTestContext context, TestServerConnection connection, TestServerContext server) {
		server.runCommand("kill @e[type=rings:crash_pilot]");
		server.runCommand("fill -24 " + (GROUND + 1) + " -24 24 " + (GROUND + 20) + " 24 air");
		server.runCommand("fill -24 " + (GROUND - 6) + " -24 24 " + GROUND + " 24 stone");
		server.runCommand("fill -24 " + GROUND + " -24 24 " + GROUND + " 24 grass_block");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			Devourer devourer = Rings.DEVOURER.create(level, EntitySpawnReason.COMMAND);
			devourer.snapTo(0.5, GROUND + 2, 8.5, 180.0F, 0.0F);
			devourer.setNoAi(true);
			level.addFreshEntity(devourer);
			StrandedExplorer explorer = Rings.STRANDED_EXPLORER.create(level, EntitySpawnReason.COMMAND);
			explorer.snapTo(4.5, GROUND + 1, 4.5, 200.0F, 0.0F);
			level.addFreshEntity(explorer);
			p.teleportTo(0.5, GROUND + 3, -2.5);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(1.5, GROUND + 3, 6.5));
		});
		connection.waitForClientboundPackets();
		context.waitTicks(30);
		context.takeScreenshot("polish-3-devourer-and-explorer");
		server.runCommand("kill @e[type=rings:devourer]");
	}

	private static void ruins(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute in rings:moon run rings ruins moon 3 48");
		check(server, p -> {
			ServerLevel moon = p.level().getServer().getLevel(Space.Planet.MOON.dimension());
			for (int x = -56; x <= 56; x++) {
				for (int z = -56; z <= 56; z++) {
					int top = moon.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
					for (int y = top; y > top - 8; y--) {
						if (moon.getBlockState(new BlockPos(x, y, z)).is(Blocks.CHEST)) {
							return true;
						}
					}
				}
			}
			return false;
		}, "/rings ruins puts shrines on the Moon");
	}
}
