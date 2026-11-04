# sweep.sh's fixtures (r505)

Whole painted strips that `sweep.sh` plants on and checks, committed because `build/` and parallax-ai's `build/` are
scratch: atelier r2599 thinned `build/rN` to 6 pictures each on 2026-11-03 and the sweep skipped what it lost, in silence,
until `mutate.sh` could prove almost nothing. A fixture that goes missing now fails the sweep.

- `NAME_ai.png`: the painting, whole, losslessly recompressed. Never a crop: `creature.py`'s sizes follow the strip's
  width and its box wraps along X.
- `NAME_cut.png`: the cut's alpha, RGB black: `creature.py --src` looks at the painting through it and never reads the
  cut's colours.
- `NAME_mask.png`: sil.py's mask, for the cases `layer.py` cuts again.

Where each came from: `tools/r505-sweep-fixtures/make.py`. Never re-export them: a new rule that needs another strip adds
one, chosen with `tools/r505-sweep-fixtures/matrix.py`, `search.py` and `corpus.py`.
