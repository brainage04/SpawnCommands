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

    @GameTest
    public void playerOnlyCommandsAnswerNonPlayersThroughModFeedback(GameTestHelper helper) {
        SpawnCommandsGameTests.playerOnlyCommandsAnswerNonPlayersThroughModFeedback(helper);
    }

    @GameTest
    public void commandsClickForThePlayerThroughModFeedback(GameTestHelper helper) {
        SpawnCommandsGameTests.commandsClickForThePlayerThroughModFeedback(helper);
    }

    // The GameTest server ticks as fast as it can, so a tick budget cannot bound a wait for a chunk
    // load (20000 ticks took 3 s on one host and 90 s on another, 14 Nov 2026). These tests check
    // 2 min of wall time themselves, and the budget is only a backstop above that.
    @GameTest(maxTicks = 1000000)
    public void worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt(GameTestHelper helper) {
        SpawnCommandsGameTests.worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt(helper);
    }

    @GameTest
    public void spawnsInLavaOrOverTheVoidFailWithoutMovingThePlayer(GameTestHelper helper) {
        SpawnCommandsGameTests.spawnsInLavaOrOverTheVoidFailWithoutMovingThePlayer(helper);
    }

    @GameTest
    public void aSpawnInWaterStillTeleports(GameTestHelper helper) {
        SpawnCommandsGameTests.aSpawnInWaterStillTeleports(helper);
    }

    @GameTest
    public void aSpawnWithLavaAtHeadHeightFails(GameTestHelper helper) {
        SpawnCommandsGameTests.aSpawnWithLavaAtHeadHeightFails(helper);
    }

    @GameTest
    public void lavaDoesNotStopACreativeOrSpectatorPlayer(GameTestHelper helper) {
        SpawnCommandsGameTests.lavaDoesNotStopACreativeOrSpectatorPlayer(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aWorldSpawnInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(GameTestHelper helper) {
        SpawnCommandsGameTests.aWorldSpawnInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails(GameTestHelper helper) {
        SpawnCommandsGameTests.aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aSecondSpawnRequestWhileTheChunkLoadsWaitsForTheLoadToo(GameTestHelper helper) {
        SpawnCommandsGameTests.aSecondSpawnRequestWhileTheChunkLoadsWaitsForTheLoadToo(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(GameTestHelper helper) {
        SpawnTicketGameTests.aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(helper);
    }

    @GameTest
    public void aReleaseWithoutALoadLeavesLaterTicketsAlone(GameTestHelper helper) {
        SpawnTicketGameTests.aReleaseWithoutALoadLeavesLaterTicketsAlone(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(GameTestHelper helper) {
        SpawnTicketGameTests.aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aFailedArrivalIsReportedAndGivesTheTicketBack(GameTestHelper helper) {
        SpawnTicketGameTests.aFailedArrivalIsReportedAndGivesTheTicketBack(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(GameTestHelper helper) {
        SpawnTicketGameTests.aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(helper);
    }

    @GameTest(maxTicks = 1000000)
    public void aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(GameTestHelper helper) {
        SpawnTicketGameTests.aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(helper);
    }

    @GameTest
    public void vanillaTpLeavesTheDestinationChunkUnloaded(GameTestHelper helper) {
        SpawnCommandsGameTests.vanillaTpLeavesTheDestinationChunkUnloaded(helper);
    }
}
