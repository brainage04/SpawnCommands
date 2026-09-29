package io.github.brainage04.spawncommands.gametest;

import io.github.brainage04.spawncommands.SpawnAccessData;
import io.github.brainage04.spawncommands.SpawnTeleportService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/// GameTest bodies shared by the Fabric and NeoForge GameTest registrations.
public final class SpawnCommandsGameTests {
	/// Large enough that vanilla's world-spawn spread would move the destination on almost every teleport.
	private static final int RESPAWN_RADIUS = 10;
	private static final int TELEPORTS = 5;

	private SpawnCommandsGameTests() {
	}

	public static void allSpawnCommandsAreRegistered(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		for (String command : List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
			helper.assertTrue(root.getChild(command) != null, "/" + command + " must be registered");
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	public static void worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var level = helper.getLevel();
		LevelData.RespawnData worldSpawn = level.getLevelData().getRespawnData();
		player.teleportTo(20.0D, worldSpawn.pos().getY(), 20.0D);
		helper.assertTrue(SpawnTeleportService.teleportToWorldSpawn(player) == 1, "/spawn must teleport successfully");
		helper.assertTrue(player.blockPosition().equals(worldSpawn.pos()), "/spawn must use the configured world spawn position");

		// A forced (/spawnpoint) spawn respawns exactly on the configured block, like vanilla respawning.
		BlockPos personalSpawn = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(level.dimension(), personalSpawn, 45.0F, 0.0F),
				true
		), false);
		Vec3 expected = new Vec3(personalSpawn.getX() + 0.5D, personalSpawn.getY() + 0.1D, personalSpawn.getZ() + 0.5D);
		withRespawnRadius(helper, () -> assertAlwaysLandsOn(helper, player, player, expected, "/myspawn"));
		helper.succeed();
	}

	/// A bed spawn resolves to the bed's vanilla stand-up position, not a random point within the respawn radius.
	public static void bedSpawnsResolveToTheVanillaStandUpPosition(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		BlockPos foot = new BlockPos(2, 1, 1);
		BlockPos head = new BlockPos(2, 1, 2);
		helper.setBlock(foot, Blocks.BED.red().defaultBlockState()
				.setValue(BedBlock.FACING, Direction.SOUTH)
				.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, Blocks.BED.red().defaultBlockState()
				.setValue(BedBlock.FACING, Direction.SOUTH)
				.setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos bed = helper.absolutePos(head);
		float yaw = 30.0F;
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(level.dimension(), bed, yaw, 0.0F),
				false
		), false);
		Vec3 expected = BedBlock.findStandUpPosition(EntityTypes.PLAYER, level, bed, Direction.SOUTH, yaw)
				.orElseThrow(() -> new AssertionError("The test bed must have a stand-up position"));
		withRespawnRadius(helper, () -> assertAlwaysLandsOn(helper, player, player, expected, "/myspawn to a bed"));
		helper.succeed();
	}

	@SuppressWarnings("removal")
	public static void spawnSharingIsPerOwnerAndEnablesGuestTeleport(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer guest = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!owner.getUUID().equals(guest.getUUID()), "Mock players must have distinct identities");
		var level = helper.getLevel();
		BlockPos ownerSpawn = helper.absolutePos(new BlockPos(3, 2, 3));
		owner.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(level.dimension(), ownerSpawn, 90.0F, 0.0F),
				true
		), false);

		SpawnAccessData access = SpawnAccessData.get(level.getServer());
		access.revoke(owner.getUUID(), List.of(guest.getUUID()));
		helper.assertTrue(!access.allows(owner.getUUID(), guest.getUUID()),
				"Guests must not inherit global spawn access");
		access.grant(owner.getUUID(), List.of(guest.getUUID()));
		helper.assertTrue(access.allows(owner.getUUID(), guest.getUUID()),
				"/spawnshare must grant only the owner's access entry");
		Vec3 expected = new Vec3(ownerSpawn.getX() + 0.5D, ownerSpawn.getY() + 0.1D, ownerSpawn.getZ() + 0.5D);
		withRespawnRadius(helper, () -> assertAlwaysLandsOn(helper, guest, owner, expected, "/spawnof"));
		helper.succeed();
	}

	@SuppressWarnings("removal")
	private static void assertAlwaysLandsOn(
			GameTestHelper helper,
			ServerPlayer player,
			ServerPlayer spawnOwner,
			Vec3 expected,
			String command
	) {
		for (int attempt = 0; attempt < TELEPORTS; attempt++) {
			player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z() + 20.0D);
			helper.assertTrue(SpawnTeleportService.teleportToPersonalSpawn(player, spawnOwner) == 1,
					command + " must teleport successfully");
			helper.assertTrue(player.position().equals(expected),
					command + " must land on " + expected + " but landed on " + player.position());
		}
	}

	private static void withRespawnRadius(GameTestHelper helper, Runnable body) {
		MinecraftServer server = helper.getLevel().getServer();
		int previous = server.getGameRules().get(GameRules.RESPAWN_RADIUS);
		server.getGameRules().set(GameRules.RESPAWN_RADIUS, RESPAWN_RADIUS, server);
		try {
			body.run();
		} finally {
			server.getGameRules().set(GameRules.RESPAWN_RADIUS, previous, server);
		}
	}
}
