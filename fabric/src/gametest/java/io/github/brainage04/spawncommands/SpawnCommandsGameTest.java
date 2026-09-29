package io.github.brainage04.spawncommands;

import io.github.brainage04.spawncommands.gametest.SpawnCommandsGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SpawnCommandsGameTest {
	@GameTest
	public void allSpawnCommandsAreRegistered(GameTestHelper helper) {
		SpawnCommandsGameTests.allSpawnCommandsAreRegistered(helper);
	}

	@GameTest
	public void worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(GameTestHelper helper) {
		SpawnCommandsGameTests.worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(helper);
	}

	@GameTest
	public void bedSpawnsResolveToTheVanillaStandUpPosition(GameTestHelper helper) {
		SpawnCommandsGameTests.bedSpawnsResolveToTheVanillaStandUpPosition(helper);
	}

	@GameTest
	public void spawnSharingIsPerOwnerAndEnablesGuestTeleport(GameTestHelper helper) {
		SpawnCommandsGameTests.spawnSharingIsPerOwnerAndEnablesGuestTeleport(helper);
	}
}
