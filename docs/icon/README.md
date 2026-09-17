# SpawnCommands icon

## What this is

The mod's icon: `icon.png` — 340x340 RGB PNG, 2 485 bytes,
sha256 `8feea9f46190f7f66bc84bef2091104b77486cdd96da58de43d01210aeeeef73`.

It is a real in-game screenshot of the mod's `/spawn` tab-completion, cropped to a square at
native GUI scale 4. No resize, no resampling, no interpolation, no compositing, no padding.

## How it was made

**Method: real Minecraft capture.**

| | |
|---|---|
| Client | Minecraft 26.2, Fabric Loader 0.19.3, OpenJDK 25.0.4.1+1 |
| Mods loaded | fabric-api 0.156.0+26.2, spawncommands 1.0.0, simpletpa 1.2.0, simplehomes 1.0.0, brainagehud 1.0.3, hudrendererlib 1.0.7, cloth-config-fabric 26.2.155, brainagelib 1.0.1 |
| Display | own Xvfb `:197`, 5120x2160x24; the primary desktop was never used |
| Audio | own PulseAudio null sink `round3_crops2` (`soundDevice=Round3Crops2`); the sink route was verified with `pactl` before every interaction |
| Renderer | software OpenGL (Mesa llvmpipe), `-XX:ActiveProcessorCount=8`, `LP_NUM_THREADS=8` |
| Shader pack | **none** |
| Resource pack | **none** |
| World | `UI Void` — a byte-copy of the BrainageHUD capture world (flat generator, `layers=[]`, biome `minecraft:the_void`, seed `20260910`), prepared by `prepare.py` |
| Options | GUI scale 4, `renderClouds=false`, `maxFps=20`, `renderDistance=2` (client enforced 12), `soundCategory_master=0.01` |
| Scene | `/gamemode creative` → `/gamemode spectator`, `/time set noon`, `/weather clear`, `/tick freeze`, `/tp @s 0.5 -60 0.5 0 -90` — camera looking straight up into the void sky |
| Screenshot | chat history cleared with F3+D, then `t` and the literal text `/spawn` typed at 80 ms/key, then the game's own native F2 PNG (`capture_scene.py capture spawncommands-spawn '/spawn'`) |

The client completes `/spawn` to `/spawnof` and draws the completions `spawnof` (selected,
yellow), `spawnpoint` and `spawnshare` above the input line.

The delivered image is the exact integer crop `(0, 1820, 340, 2160)` of
`frames/spawncommands-spawn-wide-full.png`. Verified while creating this provenance: cropping
the shipped frame to that box reproduces `icon.png` byte for byte, and `verify.py` had already
asserted the same property against the session's own copy in `verification.json`
(`exactSourceCrop: true`, file sha256 `8feea9f4…`).

Layout inside the delivered image (all measured, native pixels): the completion panel occupies
`x 36..279, y 136..279` (244x144 px, three 48 px rows, its own dark background included), so
the sky margins are 36 px left and 60 px right of the panel. The native chat input line below
the panel is included; its full-width background bar runs to the right edge of the crop,
exactly as in the source frame.

## Provenance files

| Path | What it is |
|---|---|
| `manifest.json` | Round-3 delivery record for the three crops2 icons, including this one's measured panel box and crop box |
| `frames/spawncommands-spawn-wide-full.png` | **The native 5120x2160 F2 screenshot this icon is cropped from** |
| `crop.py` | **The script that writes the three 340x340 crops** — `make_crop("spawncommands", …)` is the one that produced this file, and it also enumerates every feasible 340-wide crop to prove the minimum asymmetry |
| `capture_scene.py` | The capture driver: scene verification, chat clearing, typing, F2, screenshot copy |
| `prepare.py` | Builds this session's runtime from the BrainageHUD capture world (world, configs, options, argfile, launcher) |
| `measure.py` | Ink/panel measurement of the native autocomplete band, with the per-entry glyph-line boxes |
| `verify.py` | Re-opens the written PNGs and asserts they are exact source crops with the measured margins |
| `verification.json`, `crop-report.json` | Measured results for all three icons (crop box, sky extrema, ink boxes, output hashes) |
| `evidence/scene-commands.json` | The scene commands as verified through the client's own chat feedback |
| `evidence/audio-before-interaction.json` | Proof that the client's audio stream was on this session's own sink |
| `evidence/geometry-probe.json` | Measured panel/glyph geometry of the source frames |
| `evidence/wider-capture.json` | The capture record of this frame: tag, typed text, frame path, 5120x2160, GUI scale 4, sky conditions |
| `launch-crops2.sh`, `client-crops2.args` | The exact launcher and Java argfile of the session |
| `cleanup.json`, `blockers.json` | Resource teardown record, the 340-px-vs-symmetry blocker and the session's other limitations |

Excluded on purpose: the game directory `runtime/` (21 MB — its F2 screenshots are duplicates
of the two frames kept here), session logs, `__pycache__`, and the unchosen exactly-symmetric
316x316 alternative `evidence/closest-equal-sky/spawncommands-autocomplete-316.png`.

## How to regenerate

The session ran with working directory `<round3>/captures-crops2`:

```sh
cd captures-crops2
python3 prepare.py                                     # builds runtime/ from the captures-ui world
Xvfb :197 -screen 0 5120x2160x24 -nolisten tcp &
pactl load-module module-null-sink sink_name=round3_crops2 \
      sink_properties=device.description=Round3Crops2
sh launch-crops2.sh                                    # Minecraft 26.2 client, GUI scale 4
python3 capture_scene.py setup                         # creative -> spectator, noon, clear, freeze, camera up
python3 capture_scene.py capture spawncommands-spawn '/spawn'
python3 crop.py && python3 verify.py                   # 340x340 crops + verification
```

`crop.py` writes all three crops and the two report files together; the delivered file is the
one it names `spawncommands-autocomplete-square.png`, i.e. exactly:

```sh
python3 - <<'PY'
from PIL import Image
Image.open('frames/spawncommands-spawn-wide-full.png').convert('RGB') \
     .crop((0, 1820, 340, 2160)).save('spawncommands-autocomplete-square.png')
PY
```

Prerequisites not shipped: the Minecraft 26.2 client, Fabric Loader and the mod jars named in
`client-crops2.args`; the `UI Void` world is rebuilt by `prepare.py` from the BrainageHUD
session's world, which is not part of this provenance.

## Notes

* **The sky margins are 36/60 px, not equal.** The requirement was a 340x340 canvas; with the
  native GUI scale 4 and the left-anchored command panel, 340x340 and perfectly equal sky
  cannot both hold. `crop.py` proves by enumeration that 24 px is the smallest achievable
  margin difference, and `blockers.json` records the arithmetic. The closest perfectly
  symmetric integer crop would be 316x316 (36/36 px) — it was produced too, but it is *not*
  the chosen icon and is not copied into this provenance.
* As with the other chat icons of this session, the frame's full-width chat input bar is cut by
  the right edge of the crop; this was already true of the approved earlier reference images,
  and no completion entry or input text is affected.
* The panel's dark translucent background is treated as part of the autocomplete block, which
  is why the measured ink box is 244 px wide while the glyphs alone span less. Centring on the
  glyphs instead would give a 4 px larger square (344 px); `crop-report.json` keeps that
  alternative measurement.
* Minecraft 26.2 renders the chat autocomplete as an inline completion plus this suggestion
  panel; it does not draw a dropdown list. `blockers.json` documents that and the client's
  refusal of `simulationDistance=2`.

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
