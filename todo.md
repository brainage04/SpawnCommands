# SpawnCommands todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [ ] **Medium, both loaders:** `/myspawn` and `/spawnof` land up to `respawn_radius` blocks away from the personal spawn at random (e.g. set 40,-60,-40, landed 48.5,-40.5 then 31.5,-44.5), while vanilla respawn lands exactly on it. `SpawnTeleportService.java:39` passes it through `ServerPlayer.adjustSpawnLocation`, which is the world-spawn spread.
