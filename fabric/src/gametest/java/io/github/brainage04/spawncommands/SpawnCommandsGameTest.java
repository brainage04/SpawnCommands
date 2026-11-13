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

    @GameTest
    public void messagesUseModFeedbackAndHelpIsRegistered(GameTestHelper helper) {
        SpawnCommandsGameTests.messagesUseModFeedbackAndHelpIsRegistered(helper);
    }

    @GameTest
    public void worldSpawnLandsOnTopOfItsBlock(GameTestHelper helper) {
        SpawnCommandsGameTests.worldSpawnLandsOnTopOfItsBlock(helper);
    }

    @GameTest
    public void obstructedWorldSpawnFailsWithoutMovingThePlayer(GameTestHelper helper) {
        SpawnCommandsGameTests.obstructedWorldSpawnFailsWithoutMovingThePlayer(helper);
    }

    @GameTest
    public void ownersRevokeGuestsWhoAreOffline(GameTestHelper helper) {
        SpawnCommandsGameTests.ownersRevokeGuestsWhoAreOffline(helper);
    }

    @GameTest
    public void worldSpawnOverAFenceOrWallLandsOnItsTop(GameTestHelper helper) {
        SpawnCommandsGameTests.worldSpawnOverAFenceOrWallLandsOnItsTop(helper);
    }

    @GameTest
    public void worldSpawnInPowderSnowIsJudgedWhereItIs(GameTestHelper helper) {
        SpawnCommandsGameTests.worldSpawnInPowderSnowIsJudgedWhereItIs(helper);
    }

    // The GameTest server ticks as fast as it can, 625 ticks a second on GitHub's runners and about
    // 20 on a busy development machine (measured 14 Nov 2026), so the tick budget has to be large
    // enough for the chunk load on the faster of the two: 20000 ticks are about half a minute
    // there.
    @GameTest(maxTicks = 20000)
    public void worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt(GameTestHelper helper) {
        SpawnCommandsGameTests.worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt(helper);
    }

    @GameTest(maxTicks = 20000)
    public void aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails(GameTestHelper helper) {
        SpawnCommandsGameTests.aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails(helper);
    }

    @GameTest
    public void vanillaTpLeavesTheDestinationChunkUnloaded(GameTestHelper helper) {
        SpawnCommandsGameTests.vanillaTpLeavesTheDestinationChunkUnloaded(helper);
    }
}
