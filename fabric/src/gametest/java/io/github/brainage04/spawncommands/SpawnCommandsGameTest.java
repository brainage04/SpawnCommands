package io.github.brainage04.spawncommands;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

import java.util.List;

public final class SpawnCommandsGameTest {
	@GameTest
	public void allSpawnCommandsAreRegistered(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		for (String command : List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
			helper.assertTrue(root.getChild(command) != null, "/" + command + " must be registered");
		}
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var level = helper.getLevel();
		LevelData.RespawnData worldSpawn = level.getLevelData().getRespawnData();
		player.teleportTo(20.0D, worldSpawn.pos().getY(), 20.0D);
		helper.assertTrue(SpawnTeleportService.teleportToWorldSpawn(player) == 1, "/spawn must teleport successfully");
		helper.assertTrue(player.blockPosition().equals(worldSpawn.pos()), "/spawn must use the configured world spawn position");

		BlockPos personalSpawn = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(level.dimension(), personalSpawn, 45.0F, 0.0F),
				true
		), false);
		player.teleportTo(20.0D, personalSpawn.getY(), 20.0D);
		helper.assertTrue(SpawnTeleportService.teleportToPersonalSpawn(player, player) == 1,
				"/myspawn must teleport successfully");
		helper.assertTrue(player.blockPosition().distSqr(personalSpawn) <= 400.0D,
				"/myspawn must resolve a safe position near the player's configured respawn");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void spawnSharingIsPerOwnerAndEnablesGuestTeleport(GameTestHelper helper) {
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
		helper.assertTrue(SpawnTeleportService.teleportToPersonalSpawn(guest, owner) == 1,
				"An allowed guest must be able to use /spawnof");
		helper.assertTrue(guest.blockPosition().distSqr(ownerSpawn) <= 400.0D,
				"/spawnof must resolve a safe position near the owner's personal spawn");
		helper.succeed();
	}
}
