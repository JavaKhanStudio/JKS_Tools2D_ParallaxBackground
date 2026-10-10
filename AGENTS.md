# Agent guide

What README.md does not say: the rules that break silently here. Concepts, settings and file shapes are in
README.md; branches and releases in RELEASING.md. `git show 2a7e3f6:<path>` shows the code as it was before the 2026
repair.

## Modules

| Module    | What it is | Java | Run / check |
|-----------|------------|------|-------------|
| `core/`   | The runtime games depend on (`parallax-background` on Maven Central). Sources `src/` (GWT) and `src-jvm/`, tests `test/`. | `options.release = 11` for main, 17 for tests | `./gradlew :core:test`, `./gradlew :core:gwtCheck` |
| `engines/godot/` | A Godot 4 project: the reader for other engines, first port (r87). `addons/jks_parallax` reads a `.jplax`/`.plaxpj` and its libGDX `.atlas`, and draws it as `core` does. See `docs/other-engines.md`. | - | `tools/godot-parallax-shots.sh` (Godot 4, cage, Xwayland): libGDX and Godot frames of the same pages, compared. The board's Actions tab opens its demo through `tools/start-demo.sh godot` |
| `shots/` | `:shots`, the reference renderer (r130): `ParallaxShots` draws a round's scenes with `core` and saves the stills the Godot and jME frames are compared with. Not published. Split out of the grading lab's `--shots` mode, which the lab no longer has (r157). | 17 | `tools/parallax-lab-shots.sh ROUND OUT` |
| `engines/jme/` | `:jme`, the jMonkeyEngine reader (r112): `core` runs as is, `JmeBatch` implements libGDX's `Batch` with jME meshes. `src/` is published as `parallax-background-jme` (public API, released with core), `tests/` the frame runner and demo. | 17 | `tools/jme-parallax-shots.sh` (cage, Xwayland): the Godot check's rounds, libGDX against jME. `./gradlew :jme:run`: the demo |

- Never open a window on Simon's screen. With `ATELIER_AGENT` set or `CLAUDECODE=1`, `:jme:run` and `:shots:run`
  render in a headless cage (`gradle/offscreen.gradle`, off with
  `ATELIER_NO_OFFSCREEN=1`, only when Simon asked to watch), and fail when there is no cage. A new JavaExec task calls
  `rootProject.offscreen(it)` or `rootProject.headless(it)`, or the build stops. Anything else that opens one goes
  through cage or `tools/offscreen.sh`: `tools/offscreen-lint.sh` (CI) fails a `tools/*.sh` that does neither, unless
  it carries `# on-screen: <why>` (a launch that opens no window: `# headless: <why>` above it). Cage renders on the
  NVIDIA GPU, a desktop window on the Intel one: compare frame times of the same.
  Under all of that, `.claude/settings.json` gives every Claude session here an empty `DISPLAY` and a
  `WAYLAND_DISPLAY` naming no socket (r118): a window outside cage fails to open instead of showing. When Simon asks to
  watch, put his back on the command: `DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 ATELIER_NO_OFFSCREEN=1 ...`.
  `tools/agent-screen-check.sh` proves both halves.
- A Java 12+ API in `core/src` fails the build with an error about that API, not about the release level.
- Every dependency version, and the published `version`, is in `gradle.properties`.
- Repositories go in `settings.gradle` only: `FAIL_ON_PROJECT_REPOS` fails the build on a module-level one.
- The editor and the demo are
  [JKS_Tools2D_ParallaxEditor](https://github.com/JavaKhanStudio/JKS_Tools2D_ParallaxEditor), a repository and an
  Atelier board (`parallax-editor`) of their own since the split (r130-r133, docs/repo-split.md). Beside this
  checkout, its build compiles this `core` instead of Maven's: run a `core` change in `:editor:run` or `:demo:stress`
  there. Nothing here may point into it.
- CI (`.github/workflows/ci.yml`, JDK 17, 21 and 25) runs `./gradlew build`, `tools/browser-test.sh`,
  `tools/offscreen-lint.sh`.
  Its `godot-frames` job runs `tools/godot-parallax-shots.sh` under `xvfb-run` on Mesa llvmpipe
  (`ATELIER_NO_OFFSCREEN=1`, Godot from its GitHub release, `GODOT_VERSION` in the job), then
  `tools/jme-parallax-shots.sh` the same way, and fails on a FAIL. A manual run's `break` input sets `BREAK` for the
  jME step: `gh workflow run ci.yml --ref <branch> -f break=scroll` must fail it.

## `.plax` is a file format

- `core/src-jvm/jks/tools2d/parallax/heart/GVars_Serialization.prepareKryo`: registration order is the class id written
  into every exported `.plax`. Append only, never reorder.
- `kryo.setReferences(true)` stays: the 2019 files were written with references on, and with it off they decode as
  garbage without throwing.
- A new stored field needs a format bump in `pages/WholePage_Model_Serializer` (`CURRENT_VERSION`), written and read
  behind `currentVersion(kryo) >= N` in its serializer. Format 1 files (no version marker) must keep loading.
- A new stored field is also read in `core/src/.../pages/Utils_Page_Json`, the browser build's JSON loader: it maps
  fields by hand, and one it misses loads as its default without an error. Add it to `PlaxFormatTest.assertPageEquals`,
  which `JsonPageTest` uses.
- A new stored field is read a third time in `engines/godot/addons/jks_parallax/plax_page.gd` (`LAYER_DEFAULTS` for a
  layer's): one it misses loads as its default in a Godot game.
- A new layer field is also written back from the `ParallaxLayer` by `pages/Utils_Page.buildFromPage`, the editor's
  save path: one it misses is dropped when the editor saves. `BuildFromPageTest` round-trips a page through it.
- `core/test-data/**` holds the test fixtures for `core/test/.../PlaxFormatTest` and the reader rounds' atlases:
  `samples/` is a byte copy of the 2019 editor's six `.plax` and the demo's pages (r130, `samples/README.md`). Never
  re-export or overwrite them, nor the originals in the editor repository.
- Change what a file holds and update README.md's "File formats" section in the same commit.

## Runtime (`core`)

- Each `Parallax_Heart` keeps its world size in its `ParallaxPageReader`, which hands it to the layers it takes; a layer
  takes its decal percentages of that size. `heart/Gvars_Parallax` only holds the last heart's size, the default for
  layers and pages built without a heart: nothing a heart runs may read it.
- `ParallaxPageReader.act()`/`draw()` and `heart/Parallax_Heart.act()`/`render()` run every frame: no allocation, no
  streams. `FrameAllocationTest` counts the reader's bytes over 2000 frames and fails on any. The editor repository's
  `./gradlew :demo:stress` measures a change on the GPU: the runtime is fill-rate bound, so time pixels, not code.
- Tiling reads the camera view, position and zoom: `tilesJustEnoughToCoverTheView` holds it.
- `engines/godot/addons/jks_parallax/plax_background.gd` ports `ParallaxLayer.act`, `ParallaxPageReader.tile`/`drawRegion`,
  the cross-fade, the tint and the gradients line for line: change how a page scrolls, fades or draws, change it there,
  and run `tools/godot-parallax-shots.sh` (`engines/godot/tests/transfer` is its cross-fade round).
- `engines/jme` runs `ParallaxPageReader` itself, through `JmeBatch`, which implements only
  `draw(region, x, y, width, height)`, and ports `SquareBackground` in `JmeGradient`. Draw through another `Batch`
  method, change the gradients, or make `ParallaxPageReader` or `ParallaxLayer` reach a libGDX native (`OrthographicCamera.update` does), and
  run `tools/jme-parallax-shots.sh`.
- An `EMPTY` layer (`Enum_LayerKind`, format 5) has no image: the reader calls the game's `LayerHook` per tile in its
  place, and so do `plax_background.gd` (`set_layer_hook`) and jME. A new layer kind goes in `Enum_LayerKind` (append
  only), in `plax_page.gd`'s `KINDS`, and in a round under `engines/godot/tests` (`effects` is EMPTY's).
- A `SHADER` layer's effects (`Enum_ShaderEffect`, format 7) are written three times, line for line:
  `core/src/.../GdxLayerEffects.java` (GLSL ES 1.0, WebGL too), `engines/godot/addons/jks_parallax/plax_effects.gd`
  and `engines/jme/resources/.../ParallaxEffect.frag`, all fed `GdxLayerEffects.uniforms`. Change one, change all
  three, and run both frame checks on `engines/godot/tests/shaders`; a new effect goes in `Enum_ShaderEffect` (append
  only), `plax_effects.gd`'s `EFFECTS` and that round, strong enough that leaving it out fails
  (`tools/r180-shader-round/strength.sh`). The reader draws them through the engine's `LayerEffects`, never
  `Batch.setShader`, which `JmeBatch` throws on. FOG's x is `along()`, the view's x minus
  `ParallaxLayer.getEffectStartX` (what `act()` wrapped kept in `wrappedX`, Godot's `wrapped_x`), not the tile's own:
  the noise runs on across the tiles (r229, round `shaders` s06, `tools/r229-fog-seam/mutate.sh`).
- A page's depth fog (`fogStrength`, `fogColor`, format 10; format 9's `shaderHaze` is read as its strength) ends
  every one of those shaders in `hazed()`, toward the fog's colour (`u_fog`), and draws an IMAGE or SEQUENCE layer
  through a `PLAIN` one (jME's `Plain` define, Godot's `"PLAIN"` material); libGDX instead draws them all through one
  `PAGE_FOG` shader, each layer's fog in the batch colour (`LayerEffects.beginPageFog`), so a fogged page costs no
  flush per layer. The mix is written three times, `ParallaxPageReader.fogOf`/`frontSpeedOf` twice (Godot's
  `PlaxEffects.fog_of`/`front_speed_of`). Run both frame checks on `engines/godot/tests/fog`;
  `tools/r217-fog/strength.sh` proves leaving it out fails there, `tools/r217-fog/mutate.sh` that a wrong copy does.
- A transfert's dissolve (`TransfertStyle.dissolve`, r250, no format change) is in those shaders' `hazed()` too: its
  alpha times `dissolved()`, FOG's noise over the camera view (libGDX's and jME's vertex `v_view`/`viewPos`, Godot's
  `SCREEN_UV` with y turned up), set per layer through `LayerEffects.setDissolve`; the reader skips the page fog's
  shared shader during one. Run both frame checks on `engines/godot/tests/transfer` (t09, t10; t15 a fogged page with WAVE and FOG layers);
  `tools/r250-dissolve/strength.sh` and `mutate.sh` prove the round sees it and a wrong copy.
- A transfert through a colour (`TransfertStyle.throughColor`, r251, no format change) is in `hazed()` too: after the
  fog, mixed toward `u_grade`'s rgb by its a (jME `m_Grade`, Godot `grade`), set per layer through
  `LayerEffects.setGrade`; the gradients take `TransfertStyle.gradient` (SquareBackground, JmeGradient, Godot's
  `_act_gradients`). Run both frame checks on `engines/godot/tests/transfer` (t11, t12, t16);
  `tools/r251-through-color/strength.sh` and `mutate.sh` prove the round sees it and a wrong copy.
- A fog creep (`TransfertStyle.fogCreep`, r252, no format change) is that same grade toward a mist, with its own
  amounts: `TransfertStyle.gradeAt`/`showsIncoming` (eased, far slots first, every slot swapped at the middle), copied
  in `plax_transfert_style.gd`'s `grade_at`/`shows_incoming`. Run both frame checks on `engines/godot/tests/transfer`
  (t13, t14, t17); `tools/r252-fog-creep/strength.sh` and `mutate.sh` prove the round sees it and a wrong copy.
- A `SEQUENCE` layer's cycle (format 8) is drawn by `SequenceCycle` from integers only: GWT and GDScript round a float
  differently, so a float anywhere in the pick draws another ground in the browser or Godot. Its picks are pinned in
  `ReaderCases.sequenceCycleOfAKnownSeedIsPinned`: a change there is a change of every saved page's ground. Godot's
  copy is `plax_page.gd`'s `draw_cycle` (32-bit masked) and `plax_background.gd`'s `_draw_cycle`:
  `engines/godot/tests/sequence_cycle.gd` holds them to the JVM's picks (`tests/sequence/picks.json`, written by
  `tools/r183-sequence/PickTable.java`) and to a walk over every slot; `tools/godot-parallax-shots.sh` runs it with the
  `sequence` round.
- The browser suite's `ReaderCases` checks tiling and cross-fades without a window, by recording draw calls on a
  `RecordingBatch`; `BrowserSuiteTest` runs it in `:core:test`. Cover all four repeat modes (X, Y, XY, none).

## Browser (GWT)

- `core/src` is compiled to JavaScript by games. `./gradlew :core:gwtCheck` (part of `build`) proves it: no Kryo, no
  Jackson, no reflection, no `Cloneable`/`Object.clone()`, and only the JDK GWT emulates.
- Anything that reads or writes a file format goes in `core/src-jvm`, in the same package, and its file name in
  `Parallax.gwt.xml`'s excludes. Jackson annotations go in `pages/Json_MixIns`, Kryo serializers in `prepareKryo`: a
  mapper built without `Json_MixIns.MIX_INS` writes a different `.jplax`.
- A `core/src` member that needs `src-jvm` carries `@GwtIncompatible` and names the class fully qualified: an import of
  it fails the browser build.
- `ParallaxLayer.clone()` copies field by field: a new field goes there too, `ParallaxLayerTest` fails otherwise.
- `tools/browser-test.sh` runs `core/browser-test/cases` in headless Chrome (`--open`: in a window, the
  `browser-tests` lab). Those cases are GWT-safe code, no JUnit, no reflection; `BrowserSuiteTest` runs the same ones on
  the JVM. A test of what a browser game calls goes there; GWT floats are JavaScript doubles, so compare floats by
  `Float.floatToIntBits`, never by their printed text.
- The HTML labs (`core/browser-test/webapp/*-lab.html`, r232) share one kit: `lab.css`, `lab.js` (Copy settings) and
  `LabControls.slider`, whose name shows up to its first " (", the rest a tooltip; a pick among names is a
  `LabControls.choice` (a select, its option's index as the value: r248), never a slider. A new lab uses them, and
  `tools/r232-lab-kit/shots.sh OUT` (add the lab to its `shots.py`) fails a lab whose sliders need a scroll in the
  1580x800 window, a text under 14px, a value or a choice's name out of its box, a slider over names, or a page wider
  than a 390px phone.

## Words

French names are kept: `transfert` = the cross-fade between two pages, `decal` = a
layer's starting offset in percent of the world.
