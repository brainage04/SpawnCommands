# SpawnCommands

SpawnCommands adds server-side commands for world, personal, and explicitly shared spawn points on Fabric and NeoForge for Minecraft 26.3.

Personal spawn points continue to use vanilla beds, respawn anchors, dimensions, yaw, and pitch. The mod does not create a second spawn system. `/myspawn` and `/spawnof` land where a vanilla respawn would: the stand-up position beside a bed or respawn anchor, or exactly on a `/spawnpoint` position. The `respawn_radius` gamerule's random spread belongs to the world spawn and never moves a personal spawn. Teleporting does not use a respawn anchor charge. If the bed or anchor is gone or obstructed, the command fails instead of falling back to the world spawn.

## Features

- World and personal spawn commands.
- Spawn sharing is persistent and owner-scoped. Granting access to one player's spawn never grants access to another player's spawn.

## Getting started

- Fabric Loader 0.19.5 or newer with Fabric API, or NeoForge 26.3.0.99 or newer.
- BrainageLib 2.0.0 or newer, below 3.0.0, for the matching loader.
- Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge.

## Commands

- `/spawn` – teleports you to the world's configured spawn dimension, position and rotation. You land on top of the floor in the spawn block: farmland, a slab, a floor built over the spawn, or the top of a fence or wall just below it. If there is no room for you there, even to crawl, the command fails and you stay where you are.
- `/myspawn` – teleports you to your vanilla personal respawn point.
- `/spawnof <player>` – teleports you to an online player's personal spawn if they shared access with you.
- `/spawnshare <players>` – grants the selected online players access to your personal spawn.
- `/spawnshare revoke <players>` – revokes that access, also from players who are offline. It suggests the players who have access.
- `/spawncommands` or `/spawncommands help` – lists these commands.

Messages use BrainageLib's style: a grey `[SpawnCommands]` prefix with a green body for success and a red body for errors. SpawnCommands is listed in BrainageLib's `/servermods` help and first-join notice.

## Known limits

- `/spawnshare revoke` looks a typed name up only in the server's name cache (`usercache.json`), never through Mojang. The cache holds the players the server has seen recently and saves at most 1000 of them, so a guest who has dropped out of it cannot be named until they join again. Selectors such as `@p` still match online players.
- When two cached guests have the same name (one was renamed and another took the name), `/spawnshare revoke` with that name revokes both.

## Configuration

No configuration yet.

## Compatibility

Vanilla clients can join without installing the mod.

## Documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) – build and GameTest commands.
- [docs/RELEASE.md](docs/RELEASE.md) – release automation.
- [docs/MODRINTH.md](docs/MODRINTH.md) – optional Modrinth publishing.
