# What makes a parallax page good

Research notes for the grading lab (r73) and the `parallax-pages` skill (`.claude/skills/parallax-pages/SKILL.md`).
Each rule is marked with where it comes from, and the lab's rounds turn claims into measured ones: a rule stays a
**hypothesis** until Simon's grades back it.

The grading lab (`ParallaxLab`, `./gradlew :demo:lab`) and its rounds (`demo/lab/...`) are in the editor repository,
[JKS_Tools2D_ParallaxEditor](https://github.com/JavaKhanStudio/JKS_Tools2D_ParallaxEditor), since r133; the paths
below are there. The stills come from this repository's `tools/parallax-lab-shots.sh`.

## 1. Where depth comes from

A 2D background gets its depth from three cues, strongest first:

1. **Motion parallax.** A point at distance *d* crosses the view at a speed proportional to *1/d*. The eye reads the
   *order* of speeds as depth order, and the *ratio* between neighbouring layers as the distance between them. This
   is the only cue this library computes: everything else is in the art or the layout.
2. **Occlusion.** A layer drawn over another is in front of it. Layers are stored back to front, so draw order and
   speed order must agree: a front layer slower than the one behind it contradicts its own occlusion, and the eye sees
   it "slide behind" something it covers.
3. **Aerial perspective.** Far things are paler, lower in contrast and shifted toward the sky colour; near things are
   darker and more saturated. The art carries this (there is no per-layer tint), so **order layers by how hazy the
   image is, not by its name**: in `calm.atlas` the palest tree strip is named `Trees_close` and the darkest
   `Trees_fartest`, and Simon's page puts them the other way round from their names.

## 2. What Simon's pages do (measured)

`tools/parallax_lab.py lint <page>` prints these numbers for any page.

| Page | Layers | Speed step (layer to layer) | Back / front speed | Span | Speed Y / X |
|------|--------|-----------------------------|--------------------|------|-------------|
| Calm, PurpleFairy | 7-9 | x1.33 (4/3), constant | 0.0110-0.0178 / 0.1 | 5.6-9x | 0.6 |
| OneNight | 8 | x1.33 after its clouds, which scroll faster than the rocks drawn over them (and drift) | 0.0237 / 0.1 | 4.2x | 0.6 |
| Hiver | 7 | x1.25, constant | 0.01 / 0.041 | 4.1x | 0.6-1.0 |
| Printemps | 5 | x1.25, two layers at one speed | 0.01 / 0.02 | 2x | 1.0-1.2 |
| CalmTree 3/4/5 | 9-11 | irregular, some steps backwards | 0.013-0.024 / 0.06-0.1 | 4-8x | mixed |
| CalmLag | 12 | x1.25 | 0.01 / 0.116 | 12x | 1.0 |

- **Speeds follow a constant ratio** (a geometric series). A constant ratio means equal steps *in depth*, which is
  what *1/d* turns even spacing into.
- **The front layer is the ground plane of the scene, not faster than the game.** No sample has a foreground layer
  running past the player.
- **At-rest speed is for things that move on their own**: clouds (40-90, far, drifting) and water (-12 to -20, near,
  against the scroll).
- **Starting offsets are staggered** (decal X 0, 10, 20, 30…): identical tiles never start aligned.
- **Sky and ground gradients** fill what the layers leave uncovered (Calm: `00a6ff` to `f5f5f5`; OneNight: night blue
  over dark red). Hiver and Printemps leave the lower ~40% white on purpose: the game's ground goes there.
- **CalmLag is the editor's default layout** (every layer 1.0 wide, decal X +10, decal Y -8, speed x1.25). It is what
  a page looks like with no art direction: mountains and clouds stacked below the trees.
  `lint` passes it: the numbers are right and the picture is wrong. Numbers catch motion faults, and since r129 the
  layout faults the atlas can tell (cut edges, empty bands, stretched regions, seams); only a render catches
  composition, so the skill renders every page it writes.

## 3. Rules, and what backs them

Status after rounds 1 (§5) and 2 (§7): **backed** = the broken variant was graded lower than its control; **not backed** = graded
the same or higher, so it is no longer a rule; **open** = no signal yet. One grader, one pass, one-point differences:
backed means "not contradicted", not proven.

| # | Rule | Backed by | Round 1 test | Status |
|---|------|-----------|--------------|--------|
| R1 | Speeds strictly increase from back to front | physics, occlusion | PurpleFairy-inverted 2, OneNight-shuffled 1 (controls 3) | **backed** |
| R2 | A constant ratio between neighbours, x1.4-x1.6 when the art gives no sizes to read | physics; every designed sample | R1: Hiver-linear 1 (control 1). R2: PurpleFairy-art x1.15: 2, x1.33: 3, x1.6: 4 | **backed, steeper**: the grade rose with the ratio; Simon's x1.25-x1.33 is the low end. Round 3 tries x2.0 |
| R3 | Front / back speed span at least ~3x; the top is the page's, not ~15x | samples (2-9x) | R1: Calm-compressed 1.8x: 2; PurpleFairy-exaggerated 45x: 2. R2: Calm-art 4.5x: 4, 12x: 4, 41x: **5**; PurpleFairy-art 2.3x: 2 | **backed** below 3x. **Not backed** above 15x: 41x on Calm-art was the only 5 of round 2, while 45x on Simon's PurpleFairy scored 2 in round 1. Round 3 brackets the top on both kinds of atlas |
| R4 | Some parallax at all: layers not at one speed | physics | Calm-flat 2 (control 3); Hiver-flat 1 (control 1) | **backed** (Calm) |
| R5 | Layer order by haze and contrast, not by region name | aerial perspective | Calm-agent 3 (= Simon's 3) | kept: it is how the art is read (§6), not a taste |
| R6 | Gradients match the art's light | aerial perspective | Calm-whitesky **4**, OneNight-wrongsky 3 (controls 3) | **not backed**: a white sky behind Calm was graded above Calm's blue. Match the art's colours to hide seams (§6), not as a rule of taste |
| R7 | Full-frame planes at their natural size; strips any width | hypothesis, then §6 | R1: OneNight-small 3 (control 3). R2: CalmTree3-art-narrow 3 (art 4); Calm-art-narrow 4 (art 4) | **backed for full-frame planes** (shrunk, their bottoms show); width does not matter for strips |
| R8 | Stagger starting offsets | samples | PurpleFairy-aligned 3 (control 3) | **not backed**: harmless, no longer a rule |
| R9 | Each layer's bottom edge hidden by the layer in front or below the screen | layout | (every render) | kept, and extended: every CUT edge (bottom, top, crop) is hidden (§6) |
| R10 | Speed Y ~0.6 x speed X | samples | not tested: the lab scrolls X only | open |
| R11 | Start from the art (§6), not from rules or the editor's layout | round 1's "random values" | R2: every *-art page graded at or above Simon's: Hiver 4 / 3, Calm 4 / 3, OneNight 4 / 3, PurpleFairy 3 / 3, CalmTree3 4 / 4 | **backed**: three wins, two ties, no loss |

What the lab cannot show: vertical scroll, cross-fades between pages (the demo does), and a game drawn over the page.

## 4. The lab

- `python3 tools/parallax_lab.py round1` (and `round2`) writes `demo/lab/round1`: 18 scenes, shuffled, named `s01`… so neither the
  order nor the name says which is the control.
- `./gradlew :demo:lab` shows the latest round, scrolling. **1-5** grades and moves on, **ENTER / BACKSPACE** next /
  previous, **SPACE** pause, **LEFT / RIGHT** scroll by hand, **UP / DOWN** speed, **R** restart, **H** shows what the
  scene tests. Grades are saved in the round's `grades.json` at every key press; the lab reopens on the first
  ungraded scene.
- `tools/parallax-lab-shots.sh demo/lab/round1 demo/build/lab/round1` renders stills (0, 6 and 12 s into the same
  scroll) and a contact sheet, off screen. `tools/parallax-lab-keys-probe.sh` drives the lab with real key presses
  and checks what it saved.

## 5. Round 1 results (r92)

Graded by Simon in the lab, 1-5 (5 best), `demo/lab/round1/grades.json` joined with `round.json`:

| Scene | Grade | | Scene | Grade |
|-------|-------|-|-------|-------|
| Calm (control) | 3 | | OneNight (control) | 3 |
| Calm-flat | 2 | | OneNight-shuffled | 1 |
| Calm-compressed | 2 | | OneNight-small | 3 |
| Calm-whitesky | 4 | | OneNight-wrongsky | 3 |
| Calm-agent (from the rules) | 3 | | OneNight-agent (from the rules) | 2 |
| CalmLag (editor default) | 3 | | PurpleFairy (control) | 3 |
| Hiver (control) | 1 | | PurpleFairy-inverted | 2 |
| Hiver-flat | 1 | | PurpleFairy-exaggerated | 2 |
| Hiver-linear | 1 | | PurpleFairy-aligned | 3 |

- **Nothing scored above 4**, controls included, and Simon: "a lot of the examples were terrible, to a point where
  it felt like you were using random values everywhere. You should first try to see what layers are, how the speed
  between the layers should work, and then try some things."
- **Hiver's control scored 1 because it does not fill the screen**: every Simon sample places its layers at decal Y
  56-70%, so on a 16:9 screen the art is a band in the top half over a white bottom gradient, with the firs cut flat
  (the demo shows it the same way). The Hiver rows measured nothing about R2 or R4.
- **Motion rules held** (R1, R3, R4): break the speeds and the grade drops. **Taste rules did not** (R6, R7, R8).
- **The agent's pages** matched Calm (3) and lost to OneNight (2 vs 3): written from rules, not from the art, they
  inherited the samples' layout faults.

## 6. The layers, looked at (r92)

`tools/parallax_regions.py ATLAS out.png` draws every region of an atlas on a checkerboard at its original size and
measures it: aspect, the rows holding art, the rows solid across the width, how much of the bottom row is opaque,
whether the left and right edges join (`seam`), and the colours at the top and bottom of the art.

**Two kinds of atlas**, and they are placed differently:

- **Full-frame planes** (PurpleFairy, calmTree3; 1920x1080 or 1080-tall panoramas): the painter drew one screen and
  cut it into planes; each plane's art is already at its height. At their **natural size** (region height = screen
  height: `sizeRatio = aspect x 9/16`) and decal Y 0 they compose as painted. Simon's PurpleFairy scatters them
  (size 1.00-1.34, decal Y -9.5 to 21).
- **Pieces on a full-frame canvas** (OneNight's grounds): 1920x1080 too, but every hill starts at the bottom of its
  canvas, so at decal Y 0 they pile up and every tree is a screen tall. They are stacked like strips.
- **Strips** (Hiver, Printemps, calm; 5:1 to 12:1): one band of the scene each, stacked: farther higher, each bottom
  edge under the solid band of the layer in front.

**Every cut edge must be off screen or covered.** A region's art can touch its edge because the painter cropped it:
Hiver's mountain peaks and tallest fir touch the top of their strips, so those strips' top edges go above the screen
(the firs become a big foreground, size 4.6); otherwise a peak shows cut flat. `art` and `solid` in
`parallax_regions.py` say where the edges are.

**Atlases packed with whitespace stripped need `useOriginalSize`** (calmTree3's `offset:` lines): without it a
region's art is stretched over the layer and its transparent part disappears; calmTree3's pale trunks then end in
flat cuts mid-screen.

**Depth order by the art.** calm: Clouds, Mountains_big, Mountains_small, Trees_close (palest), Trees_far,
Trees_fartest (darkest). Hiver: p3 sky, p1 mountains, p2 snow hills, p0 firs. Printemps: p4 sky, p3 hills, p2 field,
p1 poles, p0 flowers. PurpleFairy: 7, 6, 5, 4, 3, 2, 1. OneNight: clouds, rocks (the mountain), ground 0, 1, 2,
water. calmTree3: 9em, 7em, 6em, Parallax 7, 5em V2 (mist), Work2, 4em M1.

**Speed from the art.** A layer's speed is proportional to 1/distance, and so is the size at which the painter drew
the same kind of thing: a tree drawn half as big is twice as far and moves half as fast. So the ratio between two
layers' speeds is the ratio between the sizes of what they show (tree rows in calm grow ~x1.3 row to row; calmTree3's
trunks x1.6 then x2.5). Things with nothing to compare (sky, far mountains) are far: 1/10 to 1/20 of the front.

**Gradients hide what the layers leave**, in the colours of the art beside them: Hiver's snow ground is the bottom
gradient in the hills' own bottom colour (`e0e8dd`). A translucent layer (OneNight's mountain glow) shows the
gradients through it, so one gradient covering the whole screen (`topHalfSize` 0, `bottomHalfSize` 1): two would draw
their seam across it.

**Seams**: Printemps p2 (60), p3 (100) and PurpleFairy 3 (117) do not tile: their right edge does not meet their left.

The pages written this way are `tools/parallax_lab.py` `*_from_art()`; `tools/parallax_lab.py study` writes them in
order to `demo/lab/study`, and round 2 grades them next to Simon's.

## 7. Round 2 results (r128)

Graded by Simon on 2026-10-06, 1-5, `demo/lab/round2/grades.json` joined with `round.json`:

| Atlas | Simon's page | From the art | Brackets |
|-------|--------------|--------------|----------|
| Hiver | 3 | **4** | |
| calm | 3 | **4** | span 4.5x: 4, 12x (the art page): 4, 41x: **5**; 0.7 as wide: 4 |
| OneNight | 3 | **4** | |
| PurpleFairy | 3 | 3 (x1.33) | x1.15 a layer (span 2.3x): 2, x1.6 (span 17x): 4 |
| calmTree3 | 4 | 4 | 0.75 as wide: 3 |

- **Starting from the art beats or ties every one of Simon's pages** (R11): +1 on Hiver, Calm and OneNight, equal on
  PurpleFairy and CalmTree3. No page from the art lost.
- **The grader's scale held where it can be checked**: Calm and OneNight's controls were graded 3 in both rounds.
  Hiver's control rose from 1 to 3 on the same page: round 1's verdict was about it leaving the lower half white
  (§5), so that row says less than the other two.
- **Deeper is better, further than the rules said** (R2, R3): on PurpleFairy the grade climbed with the ratio
  (2, 3, 4), and on Calm the 41x span was round 2's only 5. Round 1's 45x scored 2 on Simon's PurpleFairy, which
  scatters its planes (§6): the limit looks like the page's, not a number. Lint now warns only past 45x.
- **Shallow is what hurts**: 2.3x was the round's only 2.
- **Width matters only for full-frame planes** (R7): shrinking calmTree3's showed their bottoms (3 against 4);
  shrinking calm's strips changed nothing.
- **No page reached 5 without a deep span.** Round 3 (`demo/lab/round3`) writes pages from the skill (version 2) on
  three atlases no page has been written for, at the new ratios, and brackets the top of the span: x2.0 a layer on
  PurpleFairy, 100x on Calm.
