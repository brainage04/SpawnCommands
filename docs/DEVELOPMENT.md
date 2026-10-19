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

| Test | Class | Reason |
| --- | --- | --- |
| Unit `registersEveryPublicCommand` | Redundant, removed | `allSpawnCommandsAreRegistered` checks the same four roots after real loader startup on both loaders. |
| Unit `keepsSpawnSharesOwnerScopedAndRevocable` | Keep | Idempotent grants, revocation and owner isolation exercise the real access store. |
| Server `allSpawnCommandsAreRegistered` | Keep | Checks registration by each loader's actual command lifecycle. |
| Server `worldAndPersonalSpawnsTeleportToTheirConfiguredPositions` | Keep | World destination and repeated exact forced personal destination. |
| Server `bedSpawnsResolveToTheVanillaStandUpPosition` | Keep | Real bed safety resolution differs from a forced spawn. |
| Server `spawnSharingIsPerOwnerAndEnablesGuestTeleport` | Keep | Shared destination belongs to the owner, not the guest. |
| Client `runTest` | Keep | Checks server commands reach the connected client's command tree. Recording steps alone are not assertions. |

No tautological tests were removed.
