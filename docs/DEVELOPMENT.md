# Development

Build both loader artifacts:

```shell
heavy -- ./gradlew --no-daemon build -Pfabricmoddingconventions.devClientOptions=/nonexistent
```

Run command registration, teleport, and access-control GameTests on both loaders:

```shell
heavy -- ./gradlew --no-daemon runAllGameTests -Pfabricmoddingconventions.devClientOptions=/nonexistent
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [FabricModdingConventions](https://github.com/brainage04/FabricModdingConventions) for shared build, GameTest, recording, and publishing conventions.

## Test review (2026-10-19)

Frozen pruning rubric:

- Keep a test that checks production behaviour through a real API and can catch a distinct regression.
- Redundant means another named test covers the same failure modes at an equal or stronger boundary.
- Tautological means the assertion only checks a copied implementation, mock echo, constant or isolated wiring, without a behavioural boundary.
- Loader registrations of shared tests are one scenario on two runtimes, not duplicates.

Baseline classification:

- Unit `registersEveryPublicCommand`: redundant, removed. `allSpawnCommandsAreRegistered` checks the same four roots after real loader startup on both loaders.
- Unit `keepsSpawnSharesOwnerScopedAndRevocable`: keep. Idempotent grants, revocation and owner isolation exercise the real access store.
- Server `allSpawnCommandsAreRegistered`: keep. Checks registration by each loader's actual command lifecycle.
- Server `worldAndPersonalSpawnsTeleportToTheirConfiguredPositions`: keep. World destination and repeated exact forced personal destination.
- Server `bedSpawnsResolveToTheVanillaStandUpPosition`: keep. Real bed safety resolution differs from a forced spawn.
- Server `spawnSharingIsPerOwnerAndEnablesGuestTeleport`: keep. Shared destination belongs to the owner, not the guest.
- Client `runTest`: keep. Checks server commands reach the connected client's command tree. Recording steps alone are not assertions.

No tautological tests were removed.

## Coverage added

- Unit tests round-trip the production saved-data codec and check owner isolation, persisted revocation and dirty-state tracking.
- Shared server GameTests execute every public command, deny access before sharing and after revocation, reject console use, and preserve forced-spawn rotation.
- Shared server GameTests reject absent, removed and obstructed personal spawns without moving the player.
- A shared server GameTest visits a Nether respawn anchor, checks that its charge is unchanged, and rejects it after its charge is removed. Changed Nether blocks are restored in `finally`.
- The connected-client scenario now asserts command destinations rather than relying on recording captions. Its former self-sharing caption was misleading: self-access does not grant a share.

Remaining boundaries: the codec is tested, but a full server restart and on-disk reload is not simulated. Missing custom dimensions and teleport cancellation by another mod are not covered. There are no mod gamerules, configuration screens, renderers or background workers to test.
