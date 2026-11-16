# SpawnCommands

SpawnCommands is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds commands for teleporting to the world spawnpoint, your spawnpoint, and spawnpoint of other players (if they have shared them with you).

## Features

- Teleportation to world spawnpoint
- Teleportation to your spawnpoint
- Teleportation to the spawnpoints of other players who have shared their spawnpoints with you

## Requirements

- Fabric Loader >=0.19.5 w/ Fabric API or NeoForge >=26.3.0.99
- BrainageLib >=2.0.0

## Commands

- `/spawn` - teleports you to the world spawnpoint (dimension, position and rotation)
- `/myspawn` - teleports you to your spawnpoint
- `/spawnof <player>` - teleports you to the spawnpoint of another player if they have granted you access
- `/spawnshare <players>` - grants access to your spawnpoint to the players specified
- `/spawnshare revoke <players>` - revokes access to your spawnpoint from the players specified
- `/spawncommands` or `/spawncommands help` - lists these commands

## Safety

`/spawn`, `/myspawn` and `/spawnof` won't teleport you if the destination is:

- blocked (no room to stand or even crawl)
- in lava (checked against your whole body, so lava at head height counts - skipped in creative, spectator, with infinite Fire Resistance, or when the `fire_damage` gamerule is off)
- over the void (no block below it down to the bottom of the world)
- high enough up that the fall would hurt you (going by the game's fall damage rule - no ground within 128 blocks counts as a fall that hurts)

You stay where you are and get a message instead. A spawn in an unloaded chunk still works - the chunk loads first and you arrive a moment later.

If you're already falling when you teleport, the fall resets when you arrive (like an ender pearl) unless the `spawncommands:reset_fall` gamerule is off.

## Known limits

- `/spawnshare revoke` only looks typed names up in the server's name cache (`usercache.json`, never Mojang). The cache keeps at most 1000 recently seen players, so a guest who has dropped out of it can't be named until they join again. Operators can still pick online players with selectors such as `@p`.
- If two cached players have the same name (one was renamed and someone else took the name), revoking that name revokes both.

## Config

- `/gamerule spawncommands:reset_fall <true|false>` - `true` (default) resets a player's fall on arrival like an ender pearl, `false` keeps the fall like vanilla `/tp`

## Documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - build and GameTest commands
- [docs/RELEASE.md](docs/RELEASE.md) - release automation
- [docs/MODRINTH.md](docs/MODRINTH.md) - optional Modrinth publishing
