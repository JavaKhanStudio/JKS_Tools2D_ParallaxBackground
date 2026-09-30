# One repository or two? (atelier r113, 2026-09-30)

Should the library and the editor live in two repositories, so that each is versioned on its own now that pages run in
more than one engine? This is the analysis. Nothing has been moved yet.

## What one version number covers today

`version` in `gradle.properties` is stamped on four things, and a release of any one of them releases all four
(RELEASING.md):

| What | Where it goes | Who needs a new one |
|------|---------------|---------------------|
| `core`, the libGDX runtime | Maven Central, `io.github.javakhanstudio:parallax-background` | a libGDX game |
| `editor` | `ParallaxEditor-X.Y.Z.zip` on the GitHub release | a page author |
| `engines/godot/addons/jks_parallax` | `jks-parallax-godot-X.Y.Z.zip`, and the Godot Asset Library listing (d11) | a Godot game |
| `demo` | nowhere, it is a sample | nobody |

So an editor-only fix makes a Maven release whose jar has not changed, and a Godot fix does too. That is the
versioning mess, and it is real.

## What is actually coupled

- **The editor writes the file format with `core`'s code.** It imports 30 `core` classes (`ParallaxLayer` 8 times,
  `WholePage_Model` 6, `GVars_Serialization` for the Kryo registration). An editor build writes the `.plax` format of
  the `core` it was built with: format 4 today (`WholePage_Model_Serializer.CURRENT_VERSION`).
- **Few changes cross the editor/`core` line.** Of the 40 commits since 2026-01-01 that touched a module, 18 were
  editor only, 8 `core` only, 5 `core` and editor together, 3 the demo and Godot. The five crossing ones are the
  format changes: a stored field added in `core`, and the editor control that sets it.
- **The Godot reader is tied to `core`, not to the editor.** `plax_background.gd` ports `ParallaxLayer.act` and
  `ParallaxPageReader` line for line, and `tools/godot-parallax-shots.sh` compares its frames with frames the libGDX
  runtime draws of the demo's pages (`demo/lab`). A Godot change is proved against `core` and `demo` in the same
  checkout.
- **`core`'s format tests read the editor's files.** `PlaxFormatTest` and `JvmFixtures` load every `.plax` under
  `editor/Files` (6 files, 24 KB: the 2019 samples, the proof that format 1 still loads) and `demo/assets`.

So the seam is not "libGDX / editor". It is **writer / readers**: the editor produces pages; `core`, the Godot reader
and the next one (Unity, d11) consume them, must agree with each other frame for frame, and move together. What they
share with the editor is the file format and its version number, nothing else.

## Three ways to do it

### A. One repository, two version lines

Keep the checkout. Give the editor its own version and its own tag (`editor-v1.0.0`), and keep `vX.Y.Z` for the
readers: the Maven jar and the Godot zip, which draw the same pages the same way and so share a version.
`release.yml` builds what the tag names. The editor's About shows the format version it writes.

- Costs: `release.yml` split in two jobs by tag prefix, an `editorVersion` in `gradle.properties`, RELEASING.md's two
  procedures. About a day.
- Keeps: a format change is one commit, `core` and editor together; the Godot comparison and the format fixtures as
  they are; the board as it is.
- Does not fix: a newcomer to the library still clones 33 MB of editor samples; the GitHub release page lists both.

### B. Two repositories: the readers, and the editor

- **Readers** (this repository, keeps its history, its name, its Central coordinates and its badges): `core`,
  `engines/`, `demo`, the format docs, `tools/godot-*`, `tools/browser-test.sh`. Versioned by `vX.Y.Z` as now.
- **Editor** (new repository, history kept with `git filter-repo --path editor/`): depends on
  `io.github.javakhanstudio:parallax-background` like any game. Versioned on its own.
- **Quick iteration across the two:** the editor's `settings.gradle` includes the readers' checkout when it sits next
  to it (`includeBuild('../JKS_Tools2D_ParallaxBackground')`), and Gradle then builds `core` from source instead of
  downloading it: a `core` change shows in `./gradlew run` of the editor with no publish. The project is named `core`,
  not `parallax-background`, so the include needs an explicit `dependencySubstitution` from the Maven coordinates to
  `project(':core')`. Without the sibling checkout (CI), the editor takes the snapshot or the release.
- Costs: the 6 editor `.plax` fixtures copied into `core/test-data` and the two tests re-pointed; two CIs, two
  RELEASING.md, two AGENTS.md; a second board checkout for the editor's tasks; every format change becomes two steps
  (the field in the readers, a snapshot, then the control in the editor). About three days, most of it on the board
  and the docs.
- Gains: each repository's releases mean one thing; the library's page is only the library; the editor can go to a
  store on its own schedule.

### C. Three repositories: `core`, editor, Godot addon

The Godot Asset Library would take the addon repository as is, instead of the release zip RELEASING.md works around
it with. But the frame comparison would cross repositories (it needs `core` and `demo` to draw the reference frames),
and Unity would make a fourth. This splits what has to move together. Not recommended.

## Recommendation

**A now, B when the editor has a release schedule of its own.** The versioning mess is one version for two
audiences, and A separates the audiences in a day without splitting the five commits in forty that change both. B is
the right shape when the editor ships on a store or gets its own contributors: the layout above is how to do it then,
and nothing in A stands in its way.

## Decision (Simon, 2026-09-30): B, with the demo in the editor's repository

"B but demo should be kept in the editor. Core should be as light as possible."

**What that runs into.** The demo is not only a game: its files are how the readers are proved.

- `demo/assets` (`Hiver`, `Printemps`: 1.2 MB of atlases and pages) is read by `PlaxFormatTest`, `JvmFixtures`,
  `ParallaxPageReaderTest` and the browser suite's `ReaderCases`, and is the atlas of 14 scenes of the reader rounds.
- The reader rounds (`demo/lab/round1`, `engines/godot/tests/conformance` and `/transfer`) also take their atlases from
  `editor/Files/Demos` (10 scenes) and `editor/Files/transfer` (6).
- The libGDX frames the Godot and jME frames are compared with are drawn by the demo's `ParallaxLab --shots`
  (`tools/parallax-lab-shots.sh`).

So moving `demo/` and `editor/` out as they are leaves `core`'s tests and both frame comparisons without their files.

**The layout proposed**, which keeps the published `core` jar as it is (none of this ships in it):

| Library repository (this one) | Editor repository (new) |
|---|---|
| `core`, `engines/godot`, `engines/jme` | `editor`, `editor/Files` |
| the fixtures the tests and rounds read, gathered in one test-data directory: `demo/assets`, the atlases of `editor/Files/Demos` and `/transfer` the rounds name, `editor/Files`' six `.plax` | `demo`: `ParallaxDemo`, the grading lab, `ParallaxStress`, `demo/assets` (a copy) |
| the reader rounds, and a headless reference renderer: `ParallaxLab --shots` split out as a small unpublished module | `tools/driver-probe.sh`, `stress-project.py`, the lab probes, `start-demo.sh libgdx` |
| `tools/godot-*`, `tools/jme-*`, `tools/browser-test.sh` | its own CI, `RELEASING.md` (editor zip), `AGENTS.md` |
| Maven Central, the Godot zip, `vX.Y.Z` | depends on `io.github.javakhanstudio:parallax-background`; `includeBuild` of a sibling checkout of this repository (with a `dependencySubstitution` to `project(':core')`) for quick iteration |

**Phases**, each one leaving both builds green:

1. Here, before any split: gather the fixtures, point the tests and the rounds' `atlasDir` at them, split the
   reference renderer out of `ParallaxLab`. Done when a scratch clone with `editor/` and `demo/` deleted passes
   `./gradlew build`, `tools/browser-test.sh` and both frame comparisons.
   **Done (r130, 6804c5c):** `core/test-data/samples` holds the fixtures, `shots/` (`:shots`, `ParallaxShots`) the
   reference renderer, `engines/godot/tests/round1` a copy of the lab's round 1 (the lab keeps its own, and its
   grades), and `settings.gradle` includes `editor` and `demo` only when their folder exists.
   `tools/library-alone-check.sh` runs the four gates on a clone with both deleted: all pass, and the 202 lines of
   scores (every still of the Godot and jME rounds) are the same as the checkout's before the change, whose libGDX
   stills are byte for byte those `ParallaxLab --shots` drew.
2. The editor repository: `git filter-repo` keeps the history of `editor/`, `demo/` and their tools; its build takes
   `core` from Central or the sibling checkout; its CI builds and zips the editor.
   **Done (r132):** [JavaKhanStudio/JKS_Tools2D_ParallaxEditor](https://github.com/JavaKhanStudio/JKS_Tools2D_ParallaxEditor),
   default branch `develop`: 134 commits of history, made by `tools/r132-editor-split.sh`, then its own build, CI,
   README, RELEASING.md and AGENTS.md on top. It takes `parallax-background:$parallaxVersion` from Central (the snapshot
   repository on `develop`), or builds `core` from `../JKS_Tools2D_ParallaxBackground` when that checkout is there;
   its `tools/sibling-core-check.sh` proves a change to that `core` shows in `:editor:run`, and `-PparallaxFromCentral`
   takes the artifact instead. Its version went on from 2.5.0 on a line of its own. Until phase 3, `editor/` and
   `demo/` are in both repositories: a change to one of them goes to the editor repository.
3. Here: delete `editor/` and `demo/`; `release.yml` stops attaching the editor zip; README (module map, code map,
   downloads), AGENTS.md, RELEASING.md and the board's context packs follow.
