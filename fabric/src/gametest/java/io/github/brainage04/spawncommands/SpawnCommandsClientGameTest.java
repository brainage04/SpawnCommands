package io.github.brainage04.spawncommands;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

import java.util.Properties;

@SuppressWarnings({"UnstableApiUsage", "removal"})
public final class SpawnCommandsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties serverProperties = ClientGameTestServers.flatServerProperties();
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties)) {
			ClientGameTestServers.connectToDedicatedServer(context, server, "SpawnCommands visual GameTest");
			try {
				server.runOnServer(minecraftServer -> {
					ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
					BlockPos personalSpawn = player.blockPosition().offset(4, 0, 0);
					player.setRespawnPosition(new ServerPlayer.RespawnConfig(
							LevelData.RespawnData.of(player.level().dimension(), personalSpawn, 90.0F, 0.0F),
							true
					), false);
				});
				ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
				assertCommandTree(context);
				context.runOnClient(client -> client.setScreenAndShow(new ChatScreen("", false)));
				ClientGameTestRecorder.startRecording(context);

				runCommand(context, "spawn");
				context.waitTicks(20);
				ClientGameTestRecorder.showStep(
						context,
						"spawncommands.world",
						"World spawn teleport",
						"/spawn resolves the configured world spawn and reports success in chat"
				);
				context.waitTicks(50);

				runCommand(context, "myspawn");
				context.waitTicks(20);
				ClientGameTestRecorder.showStep(
						context,
						"spawncommands.personal",
						"Vanilla personal spawn",
						"/myspawn uses the player's vanilla respawn dimension, position, yaw, pitch, and safety resolution"
				);
				context.waitTicks(50);

				runCommand(context, "spawnshare @s");
				context.waitTicks(20);
				ClientGameTestRecorder.showStep(
						context,
						"spawncommands.sharing",
						"Owner-scoped spawn sharing",
						"/spawnshare grants explicit per-owner access used by /spawnof"
				);
				context.waitTicks(50);
			} finally {
				context.runOnClient(client -> client.setScreenAndShow(null));
				ClientGameTestServers.disconnectFromDedicatedServer(context);
			}
		}
	}

	private static void assertCommandTree(ClientGameTestContext context) {
		context.computeOnClient(client -> {
			if (client.getConnection() == null) throw new AssertionError("Expected a connected client.");
			var root = client.getConnection().getCommands().getRoot();
			for (String command : java.util.List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
				if (root.getChild(command) == null) throw new AssertionError("Expected /" + command + " in the client command tree.");
			}
			return null;
		});
	}

	private static void runCommand(ClientGameTestContext context, String command) {
		context.runOnClient(client -> {
			if (client.getConnection() == null) throw new AssertionError("Expected a connected client to run /" + command + '.');
			client.getConnection().sendCommand(command);
		});
	}
}
