# World size per heart, release workflow on master, .gitignore

Session fedora1-parallax-10 closed r22, r23, r24 and r27. Nothing is left half-done.

## What changed

- **r27, `68e513c` (develop, not pushed when parked):** each `Parallax_Heart` keeps its own world size.
  - `ParallaxPageReader` holds the size and hands it to every layer it takes (`addLayers`, `addLayersTransfert`,
    `setWorldSize`).
  - `ParallaxLayer` computes its decal math from its own `worldWidth`/`worldHeight`.
  - `WholePage_Model.getDrawing(relativePath, w, h)` builds layers for that world.
  - `Gvars_Parallax` is still written by heart constructors and by an owning heart's `resize`, as the default for
    code that reads it.
  - Test: `ParallaxHeartResizeTest.twoHeartsKeepTheirOwnWorldSize`, which failed on the old code. README and
    AGENTS.md were updated in the same commit.
- **r23, `8601795`:** a cherry-pick of `5f6d12e` (release.yml fails without the Central secrets and uses
  `publishAndReleaseToMavenCentral`) onto master. It has since been pushed and is inside `v2.1.0`.
- **r24, `1fd6148`:** `.gitignore` ignores `HQ/` (local credentials) and the five `.claude/skills` symlinks.
- **r22:** no change. `AGENTS.md` plus a one-line `CLAUDE.md` importing it was already in place.

## Learned, not written elsewhere

- The heart and the reader now call the three-argument `getDrawing`. A `WholePage_Model` subclass that overrides
  only `getDrawing()` is no longer consulted by a heart. Test stubs must override the three-argument one.
- A test can build pages through the real AssetManager path without files. `ParallaxHeartResizeTest.atlasPage`
  hands the page an in-memory `TextureAtlas`, whose `Texture` comes from a `Pixmap` under the proxied GL, through
  `Gvars_Parallax.setManager(...)` with `load`, `finishLoadingAsset` and `get` overridden.
- `ParallaxPageReader.addLayersTransfert` builds the incoming page with `getDrawing(null, ...)`, which ignores
  `heart.relativePath`. A cross-fade into a page that isn't loaded yet looks for its atlas in the internal assets.
  This was true before r27 and is notice n6.
- One `WholePage_Model` shown by two hearts at once shares its layer objects (positions included), whatever the
  sizes.

## Next

- Push `68e513c` (and this file) to develop.
- n6: bring the #runtime pack up to date (world size no longer static), and decide whether the `relativePath`
  cross-fade issue gets a ticket.
