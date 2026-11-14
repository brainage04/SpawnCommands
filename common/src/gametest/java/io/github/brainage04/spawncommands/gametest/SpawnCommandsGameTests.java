package io.github.brainage04.spawncommands.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.brainage04.brainagelib.gametest.CapturedCommandOutput;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.spawncommands.SpawnAccessData;
import io.github.brainage04.spawncommands.SpawnCommands;
import io.github.brainage04.spawncommands.SpawnTeleportService;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public final class SpawnCommandsGameTests {
    /// Large enough that vanilla's world-spawn spread would move the destination on almost every
    /// teleport.
    private static final int RESPAWN_RADIUS = 10;

    private static final int TELEPORTS = 5;

    private SpawnCommandsGameTests() {}

    public static void allSpawnCommandsAreRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String command : List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
            helper.assertTrue(
                    root.getChild(command) != null, "/" + command + " must be registered");
        }
        helper.succeed();
    }

    public static void worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(
            GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            var level = helper.getLevel();
            BlockPos worldSpawn = helper.absolutePos(new BlockPos(4, 2, 4));
            withWorldSpawn(
                    helper,
                    worldSpawn,
                    () -> {
                        player.teleportTo(20.0D, worldSpawn.getY(), 20.0D);
                        helper.assertTrue(
                                SpawnTeleportService.teleportToWorldSpawn(player) == 1,
                                "/spawn must teleport successfully");
                        helper.assertTrue(
                                player.position().equals(Vec3.atBottomCenterOf(worldSpawn)),
                                "/spawn must use the configured world spawn position");
                    });

            BlockPos personalSpawn = helper.absolutePos(new BlockPos(2, 2, 2));
            player.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(level.dimension(), personalSpawn, 45.0F, 0.0F),
                            true),
                    false);
            Vec3 expected =
                    new Vec3(
                            personalSpawn.getX() + 0.5D,
                            personalSpawn.getY() + 0.1D,
                            personalSpawn.getZ() + 0.5D);
            withRespawnRadius(
                    helper,
                    () -> assertAlwaysLandsOn(helper, player, player, expected, "/myspawn"));
        }
        helper.succeed();
    }

    public static void bedSpawnsResolveToTheVanillaStandUpPosition(GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            var level = helper.getLevel();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            BlockPos foot = new BlockPos(2, 1, 1);
            BlockPos head = new BlockPos(2, 1, 2);
            helper.setBlock(foot, bed(BedPart.FOOT));
            helper.setBlock(head, bed(BedPart.HEAD));
            BlockPos bed = helper.absolutePos(head);
            float yaw = 30.0F;
            player.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(level.dimension(), bed, yaw, 0.0F), false),
                    false);
            Vec3 expected =
                    BedBlock.findStandUpPosition(
                                    EntityTypes.PLAYER, level, bed, Direction.SOUTH, yaw)
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "The test bed must have a stand-up position"));
            withRespawnRadius(
                    helper,
                    () ->
                            assertAlwaysLandsOn(
                                    helper, player, player, expected, "/myspawn to a bed"));
        }
        helper.succeed();
    }

    public static void spawnSharingIsPerOwnerAndEnablesGuestTeleport(GameTestHelper helper) {
        try (GameTestPlayer ownerPlayer = GameTestPlayer.mock(helper);
                GameTestPlayer guestPlayer = GameTestPlayer.mock(helper)) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            helper.assertTrue(
                    !owner.getUUID().equals(guest.getUUID()),
                    "Mock players must have distinct identities");
            var level = helper.getLevel();
            BlockPos ownerSpawn = helper.absolutePos(new BlockPos(3, 2, 3));
            owner.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(level.dimension(), ownerSpawn, 90.0F, 0.0F),
                            true),
                    false);

            SpawnAccessData access = SpawnAccessData.get(level.getServer());
            access.revoke(owner.getUUID(), List.of(guest.getUUID()));
            helper.assertTrue(
                    !access.allows(owner.getUUID(), guest.getUUID()),
                    "Guests must not inherit global spawn access");
            access.grant(owner.getUUID(), List.of(guest.getUUID()));
            helper.assertTrue(
                    access.allows(owner.getUUID(), guest.getUUID()),
                    "/spawnshare must grant only the owner's access entry");
            Vec3 expected =
                    new Vec3(
                            ownerSpawn.getX() + 0.5D,
                            ownerSpawn.getY() + 0.1D,
                            ownerSpawn.getZ() + 0.5D);
            withRespawnRadius(
                    helper, () -> assertAlwaysLandsOn(helper, guest, owner, expected, "/spawnof"));
        }
        helper.succeed();
    }

    public static void worldSpawnUsesItsConfiguredDimensionAndRotation(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be available");
        LevelData.RespawnData previous = overworld.getLevelData().getRespawnData();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = new BlockPos(origin.getX(), 80, origin.getZ());
        // The spawn's chunk is loaded here, so the teleport is immediate; a spawn whose chunk
        // nobody has loaded is covered by the unloaded-chunk tests.
        nether.getChunk(
                SectionPos.blockToSectionCoord(destination.getX()),
                SectionPos.blockToSectionCoord(destination.getZ()));
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            nether.setRespawnData(
                    LevelData.RespawnData.of(nether.dimension(), destination, 123.0F, 14.0F));
            helper.assertTrue(
                    execute(player, "spawn") == 1,
                    "/spawn must reach a world spawn outside the Overworld");
            helper.assertTrue(player.level() == nether, "/spawn must use the configured dimension");
            helper.assertTrue(
                    player.position().equals(Vec3.atBottomCenterOf(destination)),
                    "/spawn must use the configured position");
            helper.assertTrue(
                    player.getYRot() == 123.0F && player.getXRot() == 14.0F,
                    "/spawn must preserve configured rotation");
        } finally {
            overworld.setRespawnData(previous);
        }
        helper.succeed();
    }

    public static void missingPersonalSpawnsFailWithoutMovingThePlayer(GameTestHelper helper) {
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            Vec3 before = player.position();
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                    "A player without a personal spawn must not be teleported");
            helper.assertTrue(
                    player.position().equals(before), "A missing spawn must not move the player");

            BlockPos missingBed = helper.absolutePos(new BlockPos(2, 2, 2));
            player.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(
                                    helper.getLevel().dimension(), missingBed, 0.0F, 0.0F),
                            false),
                    false);
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                    "A removed bed must not fall back to the world spawn");
            helper.assertTrue(
                    player.position().equals(before), "An invalid bed must not move the player");

            for (int x = 1; x <= 3; x++) {
                for (int y = 1; y <= 4; y++) {
                    for (int z = 0; z <= 3; z++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
            helper.setBlock(new BlockPos(2, 2, 1), bed(BedPart.FOOT));
            helper.setBlock(new BlockPos(2, 2, 2), bed(BedPart.HEAD));
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                    "An obstructed bed must not fall back to the world spawn");
            helper.assertTrue(
                    player.position().equals(before), "An obstructed bed must not move the player");
        }
        helper.succeed();
    }

    public static void sharedSpawnCommandsEnforceGrantAndRevoke(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        SpawnAccessData access = SpawnAccessData.get(server);
        try (GameTestPlayer ownerPlayer = GameTestPlayer.mock(helper);
                GameTestPlayer guestPlayer = GameTestPlayer.mock(helper)) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            String ownerTag = "spawncommands-owner-" + owner.getUUID();
            String guestTag = "spawncommands-guest-" + guest.getUUID();
            owner.addTag(ownerTag);
            guest.addTag(guestTag);
            var ownerSource = withServerPermissions(owner);
            var guestSource = withServerPermissions(guest);
            BlockPos destination = helper.absolutePos(new BlockPos(3, 2, 3));
            owner.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(
                                    helper.getLevel().dimension(), destination, 75.0F, 12.0F),
                            true),
                    false);
            String target = "@a[tag=" + ownerTag + ",limit=1]";
            String guests = "@a[tag=" + guestTag + "]";
            try {
                Vec3 initial = guest.position();
                helper.assertTrue(
                        execute(guestSource, "spawnof " + target) == 0,
                        "Unshared /spawnof must fail");
                helper.assertTrue(
                        guest.position().equals(initial), "Denied access must not move the guest");
                helper.assertTrue(
                        execute(ownerSource, "spawnshare " + guests) == 1,
                        "/spawnshare must grant access");
                helper.assertTrue(
                        execute(ownerSource, "spawnshare " + guests) == 0,
                        "Repeated grants must not add access twice");
                helper.assertTrue(
                        execute(guestSource, "spawnof " + target) == 1,
                        "Shared /spawnof must succeed");
                helper.assertTrue(
                        guest.blockPosition().equals(destination),
                        "The guest must land at the owner's spawn");
                helper.assertTrue(
                        guest.getYRot() == 75.0F && guest.getXRot() == 12.0F,
                        "Spawn rotation must be preserved");
                helper.assertTrue(
                        execute(ownerSource, "spawnshare revoke " + guests) == 1,
                        "Revocation must remove access");
                Vec3 afterTeleport = guest.position();
                helper.assertTrue(
                        execute(guestSource, "spawnof " + target) == 0,
                        "Revoked /spawnof must fail");
                helper.assertTrue(
                        guest.position().equals(afterTeleport),
                        "Revoked access must not move the guest");
                helper.assertTrue(
                        execute(owner, "myspawn") == 1, "/myspawn must execute for a player");
                withWorldSpawn(
                        helper,
                        helper.absolutePos(new BlockPos(1, 2, 1)),
                        () ->
                                helper.assertTrue(
                                        execute(guest, "spawn") == 1,
                                        "/spawn must execute for a player"));
                var consoleOutput = new CapturedCommandOutput();
                server.getCommands()
                        .performPrefixedCommand(consoleOutput.source(server), "myspawn");
                helper.assertTrue(
                        consoleOutput
                                .text()
                                .equals(
                                        "[SpawnCommands] This command can only be used by"
                                                + " players!"),
                        "Console /myspawn must get BrainageLib's shared players-only message, got"
                                + " "
                                + consoleOutput.text());
            } finally {
                access.revoke(owner.getUUID(), List.of(guest.getUUID()));
                owner.removeTag(ownerTag);
                guest.removeTag(guestTag);
            }
        }
        helper.succeed();
    }

    public static void anchorSpawnsCrossDimensionsWithoutUsingACharge(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be available");
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos anchor = new BlockPos(origin.getX(), 80, origin.getZ());
        Map<BlockPos, BlockState> previous = new HashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(anchor.offset(-2, -1, -2), anchor.offset(2, 2, 2))) {
            BlockPos saved = pos.immutable();
            previous.put(saved, nether.getBlockState(saved));
        }
        try (GameTestPlayer ownerPlayer = GameTestPlayer.mock(helper);
                GameTestPlayer guestPlayer = GameTestPlayer.mock(helper)) {
            ServerPlayer owner = ownerPlayer.player();
            ServerPlayer guest = guestPlayer.player();
            previous.keySet()
                    .forEach(
                            pos ->
                                    nether.setBlockAndUpdate(
                                            pos,
                                            pos.getY() == anchor.getY() - 1
                                                    ? Blocks.STONE.defaultBlockState()
                                                    : Blocks.AIR.defaultBlockState()));
            nether.setBlockAndUpdate(
                    anchor,
                    Blocks.RESPAWN_ANCHOR
                            .defaultBlockState()
                            .setValue(RespawnAnchorBlock.CHARGE, 3));
            owner.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(nether.dimension(), anchor, 35.0F, 0.0F),
                            false),
                    false);
            Vec3 expected =
                    RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, nether, anchor)
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "The anchor must have a safe stand-up position"));
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(guest, owner) == 1,
                    "Shared anchor teleport must succeed");
            helper.assertTrue(
                    guest.level() == nether && guest.position().equals(expected),
                    "The guest must reach the Nether anchor");
            helper.assertTrue(
                    nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE) == 3,
                    "Teleport must not consume an anchor charge");
            nether.setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState());
            Vec3 beforeFailure = guest.position();
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(guest, owner) == 0,
                    "An uncharged anchor must fail");
            helper.assertTrue(
                    guest.position().equals(beforeFailure),
                    "An uncharged anchor must not move the guest");
        } finally {
            previous.forEach(nether::setBlockAndUpdate);
        }
        helper.succeed();
    }

    public static void messagesUseModFeedbackAndHelpIsRegistered(GameTestHelper helper) {
        try (var owner = GameTestPlayer.join(helper, "SpawnOwner").gameMode(GameType.SPECTATOR);
                var guest =
                        GameTestPlayer.join(helper, "SpawnGuest").gameMode(GameType.SPECTATOR)) {
            var ownerSource = withServerPermissions(owner.player());
            String guestSelector = "@a[name=" + guest.player().getScoreboardName() + "]";
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guestSelector) == 1,
                    "Sharing must return the number of players granted");
            assertMessage(
                    helper, owner, "[SpawnCommands] Shared your spawn with 1 player(s).", 0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guestSelector) == 0,
                    "Repeated sharing must return 0");
            assertMessage(
                    helper, owner, "[SpawnCommands] Shared your spawn with 0 player(s).", 0xFFFF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare revoke " + guestSelector) == 1,
                    "Revoking must return the number of players revoked");
            assertMessage(
                    helper,
                    owner,
                    "[SpawnCommands] Revoked spawn access for 1 player(s).",
                    0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(owner.player(), "myspawn") == 0, "A missing spawn must return 0");
            assertMessage(
                    helper,
                    owner,
                    "[SpawnCommands] You do not have a personal spawn point.",
                    0xFF5555);
            guest.clearMessages();
            helper.assertTrue(
                    execute(guest.player(), "spawnof SpawnOwner") == 0,
                    "An unshared spawn must return 0");
            assertMessage(
                    helper,
                    guest,
                    "[SpawnCommands] SpawnOwner has not shared their spawn with you.",
                    0xFF5555);
            guest.clearMessages();
            withWorldSpawn(
                    helper,
                    helper.absolutePos(new BlockPos(1, 2, 1)),
                    () ->
                            helper.assertTrue(
                                    execute(guest.player(), "spawn") == 1, "/spawn must return 1"));
            assertMessage(helper, guest, "[SpawnCommands] Teleported to world spawn.", 0x55FF55);
            owner.clearMessages();
            owner.command("spawncommands help");
            helper.assertTrue(
                    owner.received("[SpawnCommands] /spawn - teleport to the world spawn"),
                    "/spawncommands help must list the commands, got " + owner.messageTexts());
        }
        helper.assertTrue(
                ServerModHelpRegistry.entries().stream()
                        .anyMatch(
                                entry ->
                                        entry.modId().equals(SpawnCommands.MOD_ID)
                                                && entry.helpCommand().equals("/spawncommands help")
                                                && entry.adminConfigCommand() == null),
                "SpawnCommands must be registered in /servermods");
        var output = new CapturedCommandOutput();
        helper.getLevel()
                .getServer()
                .getCommands()
                .performPrefixedCommand(output.source(helper.getLevel().getServer()), "servermods");
        helper.assertTrue(
                output.text()
                        .contains(
                                "SpawnCommands: Adds world, personal, and shared player spawn"
                                        + " commands. /spawncommands help"),
                "/servermods must list SpawnCommands, got " + output.text());
        helper.succeed();
    }

    public static void worldSpawnLandsOnTopOfItsBlock(GameTestHelper helper) {
        List<Block> blocks =
                List.of(Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.SOUL_SAND, Blocks.STONE);
        List<Double> heights = List.of(15.0D / 16.0D, 15.0D / 16.0D, 14.0D / 16.0D, 1.0D);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos block = new BlockPos(1 + 2 * (i % 2), 1, 1 + 2 * (i / 2));
                helper.setBlock(block, blocks.get(i));
                BlockPos spawn = helper.absolutePos(block);
                Vec3 expected = Vec3.upFromBottomCenterOf(spawn, heights.get(i));
                String landed = blocks.get(i).toString();
                withWorldSpawn(
                        helper,
                        spawn,
                        () -> {
                            player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z());
                            helper.assertTrue(
                                    execute(player, "spawn") == 1, "/spawn must work on " + landed);
                            helper.assertTrue(
                                    player.position().equals(expected),
                                    "/spawn must land on top of "
                                            + landed
                                            + ": expected "
                                            + expected
                                            + ", landed on "
                                            + player.position());
                        });
            }
        }
        helper.succeed();
    }

    public static void obstructedWorldSpawnFailsWithoutMovingThePlayer(GameTestHelper helper) {
        try (GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnBuiltOver")) {
            ServerPlayer player = joined.player();
            BlockPos buried = new BlockPos(2, 1, 2);
            helper.setBlock(buried, Blocks.STONE);
            helper.setBlock(buried.above(), Blocks.STONE);
            joined.moveTo(helper, new BlockPos(5, 1, 5));
            Vec3 before = player.position();
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    helper.absolutePos(buried),
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "An obstructed world spawn must fail"));
            helper.assertTrue(
                    player.position().equals(before),
                    "An obstructed world spawn must not move the player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to world spawn because it is obstructed.",
                    0xFF5555);

            BlockPos gap = new BlockPos(4, 1, 2);
            helper.setBlock(gap.above(), Blocks.STONE);
            withWorldSpawn(
                    helper,
                    helper.absolutePos(gap),
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 1,
                                    "A one-block gap must still work, since the player can crawl"
                                            + " there"));
        }
        helper.succeed();
    }

    public static void worldSpawnOverAFenceOrWallLandsOnItsTop(GameTestHelper helper) {
        List<Block> blocks = List.of(Blocks.OAK_FENCE, Blocks.COBBLESTONE_WALL);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos block = new BlockPos(1 + 2 * i, 1, 1);
                helper.setBlock(block, blocks.get(i));
                // /setworldspawn while standing on the fence stores the block above it
                // (SetWorldSpawnCommand.java:21).
                BlockPos spawn = helper.absolutePos(block.above());
                Vec3 expected = Vec3.upFromBottomCenterOf(spawn, 0.5D);
                String landed = blocks.get(i).toString();
                withWorldSpawn(
                        helper,
                        spawn,
                        () -> {
                            player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z());
                            helper.assertTrue(
                                    execute(player, "spawn") == 1,
                                    "/spawn must work over " + landed);
                            helper.assertTrue(
                                    player.position().equals(expected),
                                    "/spawn must land on top of "
                                            + landed
                                            + ": expected "
                                            + expected
                                            + ", landed on "
                                            + player.position());
                        });
            }
        }
        helper.succeed();
    }

    public static void worldSpawnInPowderSnowIsJudgedWhereItIs(GameTestHelper helper) {
        BlockPos block = new BlockPos(2, 2, 2);
        helper.setBlock(block.below(), Blocks.STONE);
        helper.setBlock(block, Blocks.POWDER_SNOW);
        BlockPos spawn = helper.absolutePos(block);
        Vec3 expected = Vec3.atBottomCenterOf(spawn);
        try (GameTestPlayer mock = GameTestPlayer.mock(helper)) {
            ServerPlayer player = mock.player();
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            // From above, powder snow is solid to a player in leather boots where they stand
            // now, but not at the spawn.
            player.teleportTo(expected.x() + 20.0D, expected.y() + 10.0D, expected.z());
            withWorldSpawn(
                    helper,
                    spawn,
                    () -> {
                        helper.assertTrue(
                                execute(player, "spawn") == 1,
                                "/spawn into powder snow must work from above it");
                        helper.assertTrue(
                                player.position().equals(expected),
                                "/spawn must land in the powder snow: expected "
                                        + expected
                                        + ", landed on "
                                        + player.position());
                    });
        }
        helper.succeed();
    }

    public static void ownersRevokeGuestsWhoAreOffline(GameTestHelper helper) {
        GameProfile leaver = new GameProfile(UUID.randomUUID(), "SpawnLeaver");
        try (GameTestPlayer owner = GameTestPlayer.join(helper, "SpawnKeeper")) {
            try (GameTestPlayer guest = GameTestPlayer.join(helper, leaver)) {
                String share = "spawnshare " + guest.player().getScoreboardName();
                helper.assertTrue(
                        execute(owner.player(), share) == 1,
                        "Sharing with an online player by name must work");
            }
            List<String> suggested = suggestions(owner.player(), "spawnshare revoke ");
            helper.assertTrue(
                    suggested.contains("SpawnLeaver"),
                    "Revoke must suggest the offline guest, got " + suggested);
            helper.assertTrue(
                    execute(owner.player(), "spawnshare revoke SpawnLeaver") == 1,
                    "An owner must be able to revoke a guest who is offline");
            try (GameTestPlayer rejoined = GameTestPlayer.join(helper, leaver)) {
                helper.assertTrue(
                        execute(rejoined.player(), "spawnof SpawnKeeper") == 0,
                        "A revoked guest must be denied after rejoining");
            }
        }
        helper.succeed();
    }

    public static void playerOnlyCommandsAnswerNonPlayersThroughModFeedback(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String command :
                List.of(
                        "spawn",
                        "myspawn",
                        "spawnshare @a",
                        "spawnof Someone",
                        "spawnshare revoke Someone")) {
            var output = new CapturedCommandOutput();
            server.getCommands().performPrefixedCommand(output.source(server), command);
            helper.assertTrue(
                    output.text()
                            .equals("[SpawnCommands] This command can only be used by players!"),
                    "/"
                            + command
                            + " from the console must get BrainageLib's shared players-only"
                            + " message, got "
                            + output.text());
        }
        helper.succeed();
    }

    public static void commandsClickForThePlayerThroughModFeedback(GameTestHelper helper) {
        withWorldSpawn(
                helper,
                helper.absolutePos(new BlockPos(2, 1, 2)),
                () -> {
                    try (GameTestPlayer clicked = GameTestPlayer.join(helper, "SpawnClicked")) {
                        clicked.clearMessages();
                        clicked.command("spawn");
                        Sent sent = drain(clicked);
                        helper.assertTrue(
                                sent.messages()
                                        .equals(
                                                List.of(
                                                        "[SpawnCommands] Teleported to world spawn.")),
                                "The command must answer the player, got " + sent.messages());
                        helper.assertTrue(
                                sent.sounds().size() == 1,
                                "A player command must send one click sound, got "
                                        + sent.sounds().size());
                        ClientboundSoundPacket click = sent.sounds().getFirst();
                        helper.assertTrue(
                                click.getSound()
                                                .value()
                                                .location()
                                                .equals(
                                                        SoundEvents.UI_BUTTON_CLICK
                                                                .value()
                                                                .location())
                                        && click.getSource() == SoundSource.UI,
                                "The click must be vanilla's UI button click, got "
                                        + click.getSound().value().location()
                                        + " on "
                                        + click.getSource());
                    }
                });
        helper.succeed();
    }

    private record Sent(List<String> messages, List<ClientboundSoundPacket> sounds) {}

    /** Everything the player's client was sent since the last drain, waiting for nothing. */
    private static Sent drain(GameTestPlayer player) {
        List<String> messages = new ArrayList<>();
        List<ClientboundSoundPacket> sounds = new ArrayList<>();
        player.channel().runPendingTasks();
        player.channel().flushOutbound();
        Object outbound;
        while ((outbound = player.channel().readOutbound()) != null) {
            if (outbound
                    instanceof ClientboundSystemChatPacket(Component content, boolean overlay)) {
                if (!overlay) messages.add(content.getString());
            } else if (outbound instanceof ClientboundSoundPacket sound) {
                sounds.add(sound);
            }
            ReferenceCountUtil.release(outbound);
        }
        return new Sent(messages, sounds);
    }

    public static void vanillaTpLeavesTheDestinationChunkUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer vanilla = GameTestPlayer.join(helper, "SpawnVanilla")) {
            ServerPlayer moved = vanilla.player();
            vanilla.moveTo(helper, new BlockPos(3, 1, 3));
            BlockPos other = helper.absolutePos(new BlockPos(3, 1, 3)).offset(100_000, 0, 20_000);
            int otherChunkX = SectionPos.blockToSectionCoord(other.getX());
            int otherChunkZ = SectionPos.blockToSectionCoord(other.getZ());
            helper.assertTrue(
                    !level.hasChunk(otherChunkX, otherChunkZ),
                    "The /tp destination's chunk must not be loaded before /tp");
            Vec3 teleported = Vec3.atBottomCenterOf(other);
            // Vec3Argument does not take the exponent notation Double.toString uses past 10^7.
            execute(
                    withServerPermissions(moved),
                    String.format(
                            Locale.ROOT,
                            "tp %s %.1f %.1f %.1f",
                            moved.getScoreboardName(),
                            teleported.x(),
                            teleported.y(),
                            teleported.z()));
            helper.assertTrue(
                    moved.position().equals(teleported),
                    "Vanilla /tp must move the player to the unloaded chunk: expected "
                            + teleported
                            + ", landed on "
                            + moved.position());
            helper.assertTrue(
                    !level.hasChunk(otherChunkX, otherChunkZ),
                    "Vanilla /tp must not load the destination chunk either: it moves the player"
                            + " and lets the player's own chunk ticket load the chunk");
        }
        helper.succeed();
    }

    // The GameTest server runs its ticks as fast as it can, measured between 20 and 7000 ticks a
    // second on the machines that ran these tests on 14 Nov 2026, so a tick budget cannot bound a
    // wait for a chunk load: the load needs wall time, and the test itself checks that instead.
    // Two minutes, because one far chunk took longer than 30 s to load while the two other tests
    // waiting on far chunks in the same batch finished.
    private static final long LOAD_TIMEOUT_NANOS = 120_000_000_000L;

    private static void assertStillLoading(GameTestHelper helper, long started) {
        if (System.nanoTime() - started > LOAD_TIMEOUT_NANOS) {
            helper.fail("The chunk did not load or unload within 2 min of wall time");
        }
    }

    public static void worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnFaraway");
        ServerPlayer player = joined.player();
        joined.moveTo(helper, new BlockPos(5, 1, 5));
        BlockPos here = helper.absolutePos(new BlockPos(5, 1, 5));
        // The GameTest world is flat (WorldPresets.FLAT_ALL_DIMENSIONS, GameTestServer.java:122),
        // so the surface of a chunk far away is at the surface height here.
        BlockPos far =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ());
        int farChunkX = SectionPos.blockToSectionCoord(far.getX());
        int farChunkZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(
                !level.hasChunk(farChunkX, farChunkZ),
                "The spawn's chunk must not be loaded before /spawn");
        Vec3 expected = Vec3.atBottomCenterOf(far);
        withWorldSpawn(
                helper,
                far,
                () ->
                        helper.assertTrue(
                                execute(player, "spawn") == 1,
                                "A world spawn in a chunk nobody loaded must be accepted: its"
                                        + " chunk is loaded first, as vanilla's spawn search"
                                        + " does"));
        // The chunk ticket is given back once the teleport attempt is done, and a joined test
        // player never adds the ticket of its own position (its connection does not tick), so the
        // chunk being loaded again is not what this waits for: the arrival is.
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            player.position().equals(expected),
                            "The player must arrive at the world spawn: expected "
                                    + expected
                                    + ", landed on "
                                    + player.position());
                    joined.close();
                });
    }

    public static void aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnBuriedFaraway");
        ServerPlayer player = joined.player();
        joined.moveTo(helper, new BlockPos(5, 1, 5));
        BlockPos buried =
                helper.absolutePos(new BlockPos(5, 1, 5))
                        .offset(100_000, 0, 192_000)
                        .atY(level.getMinY());
        int chunkX = SectionPos.blockToSectionCoord(buried.getX());
        int chunkZ = SectionPos.blockToSectionCoord(buried.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The buried spawn's chunk must not be loaded before /spawn");
        Vec3 before = player.position();
        joined.clearMessages();
        withWorldSpawn(
                helper,
                buried,
                () ->
                        helper.assertTrue(
                                execute(player, "spawn") == 1,
                                "A world spawn in a chunk nobody loaded is accepted while its"
                                        + " chunk loads"));
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            joined.received(
                                    "[SpawnCommands] Could not teleport to world spawn because it"
                                            + " is obstructed."),
                            "A world spawn inside the ground of a chunk nobody loaded must fail"
                                    + " once the chunk is loaded, got "
                                    + joined.messageTexts());
                    helper.assertTrue(
                            player.position().equals(before),
                            "A world spawn in an unloaded chunk must not move the player");
                    joined.close();
                });
    }

    public static void spawnsInLavaOrOverTheVoidFailWithoutMovingThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer joined =
                GameTestPlayer.join(helper, "SpawnDangerous").gameMode(GameType.SURVIVAL)) {
            ServerPlayer player = joined.player();
            joined.moveTo(helper, new BlockPos(5, 1, 5));
            Vec3 before = player.position();

            BlockPos lava = new BlockPos(2, 1, 2);
            helper.setBlock(lava, Blocks.LAVA);
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    helper.absolutePos(lava),
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "A world spawn in lava must fail"));
            helper.assertTrue(
                    player.position().equals(before), "A spawn in lava must not move the player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to world spawn because it is in lava.",
                    0xFF5555);

            BlockPos overVoid = helper.absolutePos(new BlockPos(5, 1, 5)).offset(2_000, 0, 2_000);
            level.getChunk(
                    SectionPos.blockToSectionCoord(overVoid.getX()),
                    SectionPos.blockToSectionCoord(overVoid.getZ()));
            for (int y = level.getMinY(); y <= overVoid.getY(); y++) {
                level.setBlockAndUpdate(
                        new BlockPos(overVoid.getX(), y, overVoid.getZ()),
                        Blocks.AIR.defaultBlockState());
            }
            // A forced respawn point is where an operator's /spawnpoint puts it
            // (SetSpawnCommand.java:21, ServerPlayer.java:1121-1130).
            player.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(level.dimension(), overVoid, 0.0F, 0.0F),
                            true),
                    false);
            joined.clearMessages();
            helper.assertTrue(
                    execute(player, "myspawn") == 0, "A personal spawn over the void must fail");
            helper.assertTrue(
                    player.position().equals(before),
                    "A personal spawn over the void must not move the player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to your personal spawn because it is over"
                            + " the void.",
                    0xFF5555);
        }
        helper.succeed();
    }

    /** A spawn in water is survivable, so the lava and void refusals leave it usable. */
    public static void aSpawnInWaterStillTeleports(GameTestHelper helper) {
        try (GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnWater")) {
            ServerPlayer player = joined.player();
            joined.moveTo(helper, new BlockPos(5, 1, 5));
            BlockPos water = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    water,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 1,
                                    "A world spawn in water must teleport, got "
                                            + joined.messageTexts()));
            helper.assertTrue(
                    player.blockPosition().equals(water),
                    "The player must arrive in the water, got " + player.position());
        }
        helper.succeed();
    }

    /**
     * A world spawn with lava at head height, where the column below the feet holds none, fails,
     * also for a player who crawls now and could stand up there; with the lava a block higher,
     * above the player's 1.8 block box, it still works. Lava beside the spawn fails a player whose
     * box reaches 0.0005 blocks into it, and not one whose box stops 0.0005 blocks short.
     */
    public static void aSpawnWithLavaAtHeadHeightFails(GameTestHelper helper) {
        try (GameTestPlayer joined =
                GameTestPlayer.join(helper, "SpawnLavaHead").gameMode(GameType.SURVIVAL)) {
            ServerPlayer player = joined.player();
            joined.moveTo(helper, new BlockPos(5, 1, 5));
            Vec3 before = player.position();
            BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.LAVA);
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    spawn,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "A world spawn with lava at head height must fail"));
            helper.assertTrue(
                    player.position().equals(before),
                    "A spawn with lava at head height must not move the player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to world spawn because it is in lava.",
                    0xFF5555);

            // A crawling player's box is 0.6 blocks tall (Avatar.java:28), under the lava.
            player.setPose(Pose.SWIMMING);
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    spawn,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "A world spawn with lava at head height must fail for a"
                                            + " crawling player"));
            helper.assertTrue(
                    player.position().equals(before),
                    "A spawn with lava at head height must not move a crawling player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to world spawn because it is in lava.",
                    0xFF5555);
            player.setPose(Pose.STANDING);

            helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 3, 2), Blocks.LAVA);
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    spawn,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 1,
                                    "A world spawn with lava above the player's head must still"
                                            + " work, got "
                                            + joined.messageTexts()));

            // Lava hurts a box that overlaps it by more than 1e-7 (Entity.java:1346, 1376,
            // 1426-1428; AABB.java:401-409). The scale makes the box 1.001 blocks wide, and the
            // attribute only resizes the box on the player's next tick (LivingEntity.java:1116,
            // 1142-1143).
            helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.LAVA);
            player.getAttribute(Attributes.SCALE).setBaseValue(1.001 / 0.6);
            player.refreshDimensions();
            Vec3 atSpawn = player.position();
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    spawn,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "A world spawn whose box reaches 0.0005 blocks into lava must"
                                            + " fail"));
            helper.assertTrue(
                    player.position().equals(atSpawn),
                    "A spawn whose box reaches into lava must not move the player");
            assertMessage(
                    helper,
                    joined,
                    "[SpawnCommands] Could not teleport to world spawn because it is in lava.",
                    0xFF5555);
            player.getAttribute(Attributes.SCALE).setBaseValue(0.999 / 0.6);
            player.refreshDimensions();
            joined.clearMessages();
            withWorldSpawn(
                    helper,
                    spawn,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 1,
                                    "A world spawn whose box stops 0.0005 blocks short of lava must"
                                            + " still work, got "
                                            + joined.messageTexts()));
        }
        helper.succeed();
    }

    /**
     * Lava cannot hurt a creative or spectator player, or one whose Fire Resistance never ends, so
     * a spawn in lava still takes them there, while a spawn over the void, whose damage gets past
     * their protection, is still refused. Fire Resistance that runs out does not count.
     */
    public static void lavaDoesNotStopACreativeOrSpectatorPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (GameTestPlayer creative =
                        GameTestPlayer.join(helper, "SpawnCreative").gameMode(GameType.CREATIVE);
                GameTestPlayer spectator =
                        GameTestPlayer.join(helper, "SpawnSpectator").gameMode(GameType.SPECTATOR);
                GameTestPlayer fireproof =
                        GameTestPlayer.join(helper, "SpawnFireproof").gameMode(GameType.SURVIVAL)) {
            BlockPos lava = helper.absolutePos(new BlockPos(4, 1, 2));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.LAVA);
            for (GameTestPlayer joined : List.of(creative, spectator)) {
                ServerPlayer player = joined.player();
                joined.moveTo(helper, new BlockPos(1, 1, 1));
                joined.clearMessages();
                withWorldSpawn(
                        helper,
                        lava,
                        () ->
                                helper.assertTrue(
                                        execute(player, "spawn") == 1,
                                        "A world spawn in lava must still take "
                                                + player.getScoreboardName()
                                                + " there, got "
                                                + joined.messageTexts()));
                helper.assertTrue(
                        player.blockPosition().equals(lava),
                        "The player must arrive in the lava, got " + player.position());
            }

            ServerPlayer survivor = fireproof.player();
            fireproof.moveTo(helper, new BlockPos(1, 1, 1));
            survivor.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 9600));
            Vec3 outside = survivor.position();
            fireproof.clearMessages();
            withWorldSpawn(
                    helper,
                    lava,
                    () ->
                            helper.assertTrue(
                                    execute(survivor, "spawn") == 0,
                                    "A world spawn in lava must fail while Fire Resistance can run"
                                            + " out"));
            helper.assertTrue(
                    survivor.position().equals(outside),
                    "A spawn in lava must not move a player whose Fire Resistance can run out");
            assertMessage(
                    helper,
                    fireproof,
                    "[SpawnCommands] Could not teleport to world spawn because it is in lava.",
                    0xFF5555);
            survivor.removeEffect(MobEffects.FIRE_RESISTANCE);
            survivor.addEffect(
                    new MobEffectInstance(
                            MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
            fireproof.clearMessages();
            withWorldSpawn(
                    helper,
                    lava,
                    () ->
                            helper.assertTrue(
                                    execute(survivor, "spawn") == 1,
                                    "A world spawn in lava must take a player whose Fire"
                                            + " Resistance never ends there, got "
                                            + fireproof.messageTexts()));
            helper.assertTrue(
                    survivor.blockPosition().equals(lava),
                    "The fire resistant player must arrive in the lava, got "
                            + survivor.position());

            ServerPlayer player = creative.player();
            creative.moveTo(helper, new BlockPos(1, 1, 1));
            Vec3 before = player.position();
            BlockPos overVoid = helper.absolutePos(new BlockPos(2, 1, 2)).offset(3_000, 0, 3_000);
            level.getChunk(
                    SectionPos.blockToSectionCoord(overVoid.getX()),
                    SectionPos.blockToSectionCoord(overVoid.getZ()));
            for (int y = level.getMinY(); y <= overVoid.getY(); y++) {
                level.setBlockAndUpdate(
                        new BlockPos(overVoid.getX(), y, overVoid.getZ()),
                        Blocks.AIR.defaultBlockState());
            }
            creative.clearMessages();
            withWorldSpawn(
                    helper,
                    overVoid,
                    () ->
                            helper.assertTrue(
                                    execute(player, "spawn") == 0,
                                    "A world spawn over the void must still be refused for a"
                                            + " creative player"));
            helper.assertTrue(
                    player.position().equals(before),
                    "A spawn over the void must not move a creative player");
            assertMessage(
                    helper,
                    creative,
                    "[SpawnCommands] Could not teleport to world spawn because it is over the"
                            + " void.",
                    0xFF5555);
        }
        helper.succeed();
    }

    public static void aSecondSpawnRequestWhileTheChunkLoadsWaitsForTheLoadToo(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final assertion, which runs after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnTwice");
        ServerPlayer player = joined.player();
        joined.moveTo(helper, new BlockPos(5, 1, 5));
        BlockPos here = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos far =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ() + 48_000);
        int chunkX = SectionPos.blockToSectionCoord(far.getX());
        int chunkZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The spawn's chunk must not be loaded before /spawn");
        Vec3 expected = Vec3.atBottomCenterOf(far);
        Vec3 before = player.position();
        joined.clearMessages();
        withWorldSpawn(
                helper,
                far,
                () -> {
                    helper.assertTrue(
                            execute(player, "spawn") == 1,
                            "A world spawn in a chunk nobody loaded must be accepted while its"
                                    + " chunk loads");
                    helper.assertTrue(
                            execute(player, "spawn") == 1,
                            "A repeated /spawn while the chunk loads must be accepted too");
                });
        helper.assertTrue(
                level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "A repeated /spawn while the chunk loads must wait for the load as well, not load"
                        + " the chunk on the server thread");
        helper.assertTrue(
                player.position().equals(before),
                "Neither /spawn may move the player before the chunk has loaded");
        helper.assertTrue(
                joined.messageTexts()
                        .equals(
                                List.of(
                                        "[SpawnCommands] Loading world spawn...",
                                        "[SpawnCommands] Loading world spawn...")),
                "Each /spawn must say once that the world spawn is loading, got "
                        + joined.messageTexts());
        long started = System.nanoTime();
        helper.succeedWhen(
                () -> {
                    assertStillLoading(helper, started);
                    helper.assertTrue(
                            player.position().equals(expected),
                            "The player must arrive at the world spawn: expected "
                                    + expected
                                    + ", landed on "
                                    + player.position());
                    helper.assertTrue(
                            joined.received("[SpawnCommands] Teleported to world spawn."),
                            "The arrival must be reported, got " + joined.messageTexts());
                    joined.close();
                });
    }

    public static void aWorldSpawnInLavaInAnUnloadedChunkFailsOnceTheChunkLoads(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer joined =
                GameTestPlayer.join(helper, "SpawnLavaFar").gameMode(GameType.SURVIVAL);
        ServerPlayer player = joined.player();
        joined.moveTo(helper, new BlockPos(5, 1, 5));
        BlockPos here = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos lava =
                new BlockPos(
                        here.getX() + 100_000,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()),
                        here.getZ() + 96_000);
        int chunkX = SectionPos.blockToSectionCoord(lava.getX());
        int chunkZ = SectionPos.blockToSectionCoord(lava.getZ());
        helper.assertTrue(
                !level.hasChunk(chunkX, chunkZ),
                "The spawn's chunk must not be loaded before /spawn");
        // Placing the lava loads the chunk, so the steps below wait for it to unload before
        // /spawn.
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        Vec3 before = player.position();
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                                    "The spawn's chunk must unload before /spawn");
                        })
                .thenExecute(
                        () -> {
                            joined.clearMessages();
                            withWorldSpawn(
                                    helper,
                                    lava,
                                    () ->
                                            helper.assertTrue(
                                                    execute(player, "spawn") == 1,
                                                    "A world spawn in a chunk nobody loaded must"
                                                            + " be accepted while its chunk"
                                                            + " loads"));
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    joined.received(
                                            "[SpawnCommands] Could not teleport to world spawn"
                                                    + " because it is in lava."),
                                    "A world spawn in lava in a chunk nobody loaded must fail"
                                            + " once the chunk is loaded, got "
                                            + joined.messageTexts());
                            helper.assertTrue(
                                    player.position().equals(before),
                                    "A world spawn in lava must not move the player");
                        })
                .thenExecute(joined::close)
                .thenSucceed();
    }

    private static List<String> suggestions(ServerPlayer player, String input) {
        var dispatcher = player.level().getServer().getCommands().getDispatcher();
        var parse = dispatcher.parse(input, player.createCommandSourceStack());
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static void withWorldSpawn(GameTestHelper helper, BlockPos spawn, Runnable body) {
        var overworld = helper.getLevel().getServer().overworld();
        LevelData.RespawnData previous = overworld.getLevelData().getRespawnData();
        overworld.setRespawnData(
                LevelData.RespawnData.of(helper.getLevel().dimension(), spawn, 0.0F, 0.0F));
        try {
            body.run();
        } finally {
            overworld.setRespawnData(previous);
        }
    }

    private static BlockState bed(BedPart part) {
        return Blocks.BED
                .red()
                .defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, part);
    }

    private static CommandSourceStack withServerPermissions(ServerPlayer player) {
        return player.createCommandSourceStack()
                .withPermission(
                        player.level().getServer().createCommandSourceStack().permissions());
    }

    private static int execute(ServerPlayer player, String command) {
        return execute(player.createCommandSourceStack(), command);
    }

    private static int execute(CommandSourceStack source, String command) {
        try {
            return source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Could not execute /" + command, exception);
        }
    }

    private static void assertMessage(
            GameTestHelper helper, GameTestPlayer player, String text, int bodyColor) {
        List<Component> messages = player.messages();
        helper.assertTrue(
                messages.size() == 1 && messages.getFirst().getString().equals(text),
                "Expected only \"" + text + "\", got " + player.messageTexts());
        List<Integer> colors = new ArrayList<>();
        messages.getFirst()
                .visit(
                        (style, segment) -> {
                            if (!segment.isEmpty()) colors.add(style.getColor().getValue());
                            return Optional.empty();
                        },
                        Style.EMPTY);
        helper.assertTrue(
                colors.equals(List.of(0xAAAAAA, bodyColor)),
                "Expected a grey prefix and a coloured body for \"" + text + "\", got " + colors);
    }

    private static void assertAlwaysLandsOn(
            GameTestHelper helper,
            ServerPlayer player,
            ServerPlayer spawnOwner,
            Vec3 expected,
            String command) {
        for (int attempt = 0; attempt < TELEPORTS; attempt++) {
            player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z() + 20.0D);
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(player, spawnOwner) == 1,
                    command + " must teleport successfully");
            helper.assertTrue(
                    player.position().equals(expected),
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
