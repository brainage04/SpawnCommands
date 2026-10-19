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

	@GameTest
	public void missingPersonalSpawnsFailWithoutMovingThePlayer(GameTestHelper helper) {
		SpawnCommandsGameTests.missingPersonalSpawnsFailWithoutMovingThePlayer(helper);
	}

	@GameTest
	public void sharedSpawnCommandsEnforceGrantAndRevoke(GameTestHelper helper) {
		SpawnCommandsGameTests.sharedSpawnCommandsEnforceGrantAndRevoke(helper);
	}

	@GameTest
	public void anchorSpawnsCrossDimensionsWithoutUsingACharge(GameTestHelper helper) {
		SpawnCommandsGameTests.anchorSpawnsCrossDimensionsWithoutUsingACharge(helper);
	}

	@GameTest
	public void worldSpawnUsesItsConfiguredDimensionAndRotation(GameTestHelper helper) {
		SpawnCommandsGameTests.worldSpawnUsesItsConfiguredDimensionAndRotation(helper);
	}
}
