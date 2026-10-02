# SpawnCommands icon

## What this is

The mod's icon: `icon.png` — 512x512 RGBA PNG, 3 807 bytes,
sha256 `808b6a3ca5f9fea93dbb4910321324edaf8077af2b88944b622e9f8bfb6e0297`.

It is a real in-game screenshot of the mod's `/spawn` tab-completion: a 340x340 square crop
captured at GUI scale 4, reduced to its native 85x85 GUI pixels, enlarged 6x with NEAREST to
510x510 and centred on a transparent 512x512 canvas (1 px margin). No interpolation, no
compositing. The mod ships 128x128 LANCZOS reductions of `icon.png` at
`common/src/main/resources/assets/spawncommands/icon.png` and
`fabric/src/gametest/resources/assets/spawncommands/icon.png`.

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

The capture crop is the exact integer crop `(0, 1820, 340, 2160)` of
`frames/spawncommands-spawn-wide-full.png` (340x340 RGB, sha256 `8feea9f4…`; until 2026-10-02
it was shipped as `icon.png` itself). Verified while creating this provenance: cropping the
shipped frame to that box reproduces that crop byte for byte, and `verify.py` had already
asserted the same property against the session's own copy in `verification.json`
(`exactSourceCrop: true`, file sha256 `8feea9f4…`).

**Native-scale enlargement (2026-10-02).** The icon rule is square, a power of two, 512 or
1024 px. The crop is pixel art: 11 colours, and every 4x4 block (GUI scale 4, aligned with the
crop) is a single colour. `native_scale.py` takes each block's colour to recover the native
85x85 image, enlarges it by the largest integer factor that fits 512 (6x, 510x510) with
NEAREST, and centres it on a transparent 512x512 canvas. A LANCZOS 340→512 resize was
rejected because it blurs every GUI pixel edge.

Layout inside the capture crop (all measured, crop pixels): the completion panel occupies
`x 36..279, y 136..279` (244x144 px, three 48 px rows, its own dark background included), so
the sky margins are 36 px left and 60 px right of the panel. The native chat input line below
the panel is included; its full-width background bar runs to the right edge of the crop,
exactly as in the source frame.

## Provenance files

| Path | What it is |
|---|---|
| `manifest.json` | Round-3 delivery record for the three crops2 icons, including this one's measured panel box and crop box |
| `frames/spawncommands-spawn-wide-full.png` | **The native 5120x2160 F2 screenshot this icon is cropped from** |
| `native_scale.py` | **The script that writes `icon.png`** from the frame and crop box (native 85x85 recovery, 6x NEAREST, centred on 512x512) and the two shipped 128x128 copies |
| `crop.py` | **The script that writes the three 340x340 crops** — `make_crop("spawncommands", …)` is the one that produced the capture crop, and it also enumerates every feasible 340-wide crop to prove the minimum asymmetry |
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

`crop.py` writes all three crops and the two report files together; the capture crop is the
one it names `spawncommands-autocomplete-square.png`, i.e. exactly:

```sh
python3 - <<'PY'
from PIL import Image
Image.open('frames/spawncommands-spawn-wide-full.png').convert('RGB') \
     .crop((0, 1820, 340, 2160)).save('spawncommands-autocomplete-square.png')
PY
```

Then write `icon.png` and the shipped copies (Pillow 12.3.0, from `docs/icon/provenance`):

```sh
python3 native_scale.py    # prints: native (85, 85), 0 tied blocks, x6 -> (510, 510) centred on 512x512
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
