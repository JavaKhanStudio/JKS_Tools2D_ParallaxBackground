# What makes a parallax page good

Research notes for the grading lab (r73) and the `parallax-pages` skill (`.claude/skills/parallax-pages/SKILL.md`).
Each rule is marked with where it comes from, and the lab's rounds turn claims into measured ones: a rule stays a
**hypothesis** until Simon's grades back it.

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
  `lint` passes it: the numbers are right and the picture is wrong. Numbers catch motion faults; only a render
  catches layout, so the skill renders every page it writes.

## 3. Rules, and what backs them

| # | Rule | Backed by | Round 1 test |
|---|------|-----------|--------------|
| R1 | Speeds strictly increase from back to front | physics, occlusion | PurpleFairy-inverted, OneNight-shuffled |
| R2 | A constant ratio between neighbours (x1.25-x1.4) | physics; every designed sample | Hiver-linear |
| R3 | Front / back speed span between ~3x and ~15x | samples (2-9x); hypothesis for the limits | Calm-compressed (1.8x), PurpleFairy-exaggerated (45x) |
| R4 | Some parallax at all: layers not at one speed | physics | Hiver-flat, Calm-flat |
| R5 | Layer order by haze and contrast, not by region name | aerial perspective | Calm-agent (fixed after the first render) |
| R6 | Gradients match the art's light: sky colour behind the farthest haze | aerial perspective | Calm-whitesky, OneNight-wrongsky |
| R7 | A layer at least ~0.8 worlds wide when tiled: repeats hide | hypothesis | OneNight-small |
| R8 | Stagger starting offsets | samples | PurpleFairy-aligned |
| R9 | Each layer's bottom edge hidden by the layer in front or below the screen | layout | (every render) |
| R10 | Speed Y ~0.6 x speed X | samples | not tested: the lab scrolls X only |

What the lab cannot show: vertical scroll, cross-fades between pages (the demo does), and a game drawn over the page.

## 4. The lab

- `python3 tools/parallax_lab.py round1` writes `demo/lab/round1`: 18 scenes, shuffled, named `s01`… so neither the
  order nor the name says which is the control.
- `./gradlew :demo:lab` shows the latest round, scrolling. **1-5** grades and moves on, **ENTER / BACKSPACE** next /
  previous, **SPACE** pause, **LEFT / RIGHT** scroll by hand, **UP / DOWN** speed, **R** restart, **H** shows what the
  scene tests. Grades are saved in the round's `grades.json` at every key press; the lab reopens on the first
  ungraded scene.
- `tools/parallax-lab-shots.sh demo/lab/round1 demo/build/lab/round1` renders stills (0, 6 and 12 s into the same
  scroll) and a contact sheet, off screen. `tools/parallax-lab-keys-probe.sh` drives the lab with real key presses
  and checks what it saved.
