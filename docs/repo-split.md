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
