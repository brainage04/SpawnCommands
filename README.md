# SpawnCommands

SpawnCommands adds server-side commands for world, personal, and explicitly shared spawn points.

## Commands

| Command | Behavior |
| --- | --- |
| `/spawn` | Teleports you to the world's configured spawn. |
| `/myspawn` | Teleports you to your vanilla personal respawn point. |
| `/spawnof <player>` | Teleports you to an online player's personal spawn if they shared access with you. |
| `/spawnshare <players>` | Grants the selected online players access to your personal spawn. |
| `/spawnshare revoke <players>` | Revokes that access. |

Personal spawn points continue to use vanilla beds, respawn anchors, dimensions, yaw, pitch, and safe-position resolution. The mod does not create a second spawn system.

Spawn sharing is persistent and owner-scoped. Granting access to one player's spawn never grants access to another player's spawn.

## Loaders and installation

Fabric and NeoForge builds are provided for Minecraft 26.2.

Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge. Vanilla clients can join without installing the mod.

## Development

Build both loader artifacts:

```shell
./gradlew build
```

Run command registration, teleport, and access-control GameTests on both loaders:

```shell
./gradlew runAllProductionGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [FabricModdingConventions](https://github.com/brainage04/FabricModdingConventions) for shared build, GameTest, recording, and publishing conventions.

Release automation is documented in [docs/RELEASE.md](docs/RELEASE.md). Optional Modrinth publishing is documented in [docs/MODRINTH.md](docs/MODRINTH.md).
