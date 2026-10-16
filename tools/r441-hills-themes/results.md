# r441: the inpainted hills and their canopy gate, five themes x 10 seeds

`python3 tools/r441-hills-themes/themes.py build/r441` (seed 7: hills seeds 7020-7029, ai_page's default colours,
the hills at 3f654c; each theme's hills commands are its own `ai_page.py --dry-run` lines). spread/sharp is
`ai_page.canopy()`; the gate passes spread >= 0.5 and sharp <= 2.0. "Kept" is what `paint_depths` keeps: the first
seed passed, or after 10 refusals the most wooded painted again. A strip takes about 18 s.

| theme | passed | kept | strips painted for the layer | the strips | what the gate passed |
|-------|--------|------|------------------------------|------------|----------------------|
| forest (control) `misty pine forest in the mountains at dawn` | 0/10 | 7021, best of none | 11 | flat hills on 4, cartoon conifers on 2, one big tree on 2, round crowns on 7021 and 7023 | nothing |
| desert `sandy desert canyon at sunset` | 1/10 | 7022 | 3 | dunes and mesas on every seed, no tree: the theme | 7022, mesas (striated rock) |
| snow `snowy mountains in winter at dusk` | 3/10 | 7022 | 3 | dark ridges, some with snowy peaks, no tree: the theme | 7022, 7023, 7026: snow-streaked peaks |
| autumn `autumn countryside at dusk` | 1/10 | 7027 | 8 | autumn fields, single orange trees on 4, blob groves on 3 | 7027, black blobs (the worst of the round ones) |
| post-apo `post-apocalyptic ruined city at dusk` | 7/10 | 7021 | 2 | buildings and ruins on 6, black blob trees on 3 | 4 of buildings, 3 of blobs |

What it says:

- The gate reads texture, not trees. Off the forest it passes rock strata (desert 7022), snowy peaks (snow), buildings
  (post-apo), and refuses the best trees of the autumn round (7021, 7023, 7026) for black blobs (7027). On those
  themes its pick is no better than the first seed's; it only costs tries.
- No other theme grew pines: the conifers stay out of the negative's way. The hills look like their theme everywhere
  (dunes, snowy ridges, autumn fields, ruins), since the inpaint paints what the theme says.
- The margin: the control's 7021 scores 0.48 here and 0.55 in r384's a-forest page (`--colours 7d8aa3,4f6f5c,1d3328`,
  the hills at 3e5b4b). The two paintings are 0.94 levels apart on average, the same strip to the eye. On the default
  colours the forest page refuses all ten and keeps 7021 by the fallback, after 11 strips (about 3.5 min).

Sheets: `build/r441/<theme>.png` (each cut over the sky, the gate's numbers on it), attached to r441.

Simon's answer (r441): a gate per theme, tested and untested, with its value and an estimate. That table is
`tools/r239-ai-strips/themes.json`: these five rows as measured, the untested themes (jungle, beach, night, any other)
as labelled estimates, each with the flags that suit it. A new theme measured here is a new entry there.
