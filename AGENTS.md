# Agent guide

What README.md does not say: the rules that break silently here. Concepts, settings and file shapes are in
README.md; branches and releases in RELEASING.md. `git show 2a7e3f6:<path>` shows the code as it was before the 2026
repair.

## Modules

| Module    | What it is | Java | Run / check |
|-----------|------------|------|-------------|
| `core/`   | The runtime games depend on (`parallax-background` on Maven Central). Sources `src/` (GWT) and `src-jvm/`, tests `test/`. | `options.release = 11` for main, 17 for tests | `./gradlew :core:test`, `./gradlew :core:gwtCheck` |
| `editor/` | The desktop tool that builds pages and exports `.plax`. Sources `src/` and `mains/`. | 17 | `./gradlew :editor:run` (workingDir `editor/`, finds `editor/Files`) |
| `demo/`   | A small game using `core`. | 17 | `./gradlew :demo:run` (workingDir `demo/assets`): SPACE page, N tint, LEFT/RIGHT scroll, R reset. `./gradlew :demo:lab` (workingDir the root): the grading lab, `demo/lab` |

- A Java 12+ API in `core/src` fails the build with an error about that API, not about the release level.
- Every dependency version, and the published `version`, is in `gradle.properties`.
- Repositories go in `settings.gradle` only: `FAIL_ON_PROJECT_REPOS` fails the build on a module-level one.
- CI (`.github/workflows/ci.yml`, JDK 17, 21 and 25) runs `./gradlew build`, `tools/browser-test.sh`, then
  `./gradlew :editor:distZip :demo:distZip`.

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
- `editor/Files/**/*.plax`, `demo/assets/**/*.plax` and `core/test-data/**` are the test fixtures for `core/test/.../PlaxFormatTest`.
  Never re-export or overwrite them.
- Change what a file holds and update README.md's "File formats" section in the same commit.

## Runtime (`core`)

- Each `Parallax_Heart` keeps its world size in its `ParallaxPageReader`, which hands it to the layers it takes; a layer
  takes its decal percentages of that size. `heart/Gvars_Parallax` only holds the last heart's size, the default for
  layers and pages built without a heart: nothing a heart runs may read it.
- `ParallaxPageReader.act()`/`draw()` and `heart/Parallax_Heart.act()`/`render()` run every frame: no allocation, no
  streams. `FrameAllocationTest` counts the reader's bytes over 2000 frames and fails on any. `./gradlew :demo:stress`
  measures a change on the GPU: the runtime is fill-rate bound, so time pixels, not code.
- Tiling reads the camera view, position and zoom: `tilesJustEnoughToCoverTheView` holds it.
- `ParallaxPageReaderTest` checks tiling and cross-fades without a window, by recording draw calls on a proxied
  `Batch`. Cover all four repeat modes (X, Y, XY, none).

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

## Editor

- State is static, in `editor/src/jks/tools2d/parallax/editor/gvars/GVars_*`, and there is one project at a time.
- There are no editor tests, and a compiling control is not a working one: the 2019 editor shipped buttons and
  dialogs with empty listeners. After wiring a control, run `./gradlew :editor:run` and use it.
- Panels read the window size when built; `vue/Vue_Edition` rebuilds them after a resize.
- `GVars_UI.init` sets VisUI's global `scaleFactor`; skin styles are shared, so copy a style before changing it.
- Every control has a `setName` (`driver/Names`): `--driver-port` and the presenter's scripts find controls by it. Name a
  new control, and treat a rename as breaking those scripts.
- README.md's layer-settings table documents every setting: rename or rescale one and fix the table in the same
  commit.

## Words

French names are kept: `vue` = a screen of the editor, `transfert` = the cross-fade between two pages, `decal` = a
layer's starting offset in percent of the world.
