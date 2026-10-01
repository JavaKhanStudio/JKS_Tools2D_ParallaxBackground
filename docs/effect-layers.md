# Effect layers: particles, shaders, and the empty layer

Design for atelier r146 (2026-09-30), written before any code: a page should hold layers that are not an image, the
first being particle effects (snow, rain, dust, fireflies) and shaders (a heat haze, a water ripple), and it must say
plainly, in the editor, what a given engine cannot draw. r147 (a layer made of a sequence of images) adds a layer kind
too: the `kind` field below is shared with it.

**Decided** (Simon, 2026-09-30, on r146): particles use **each engine's native system** (option B below), shaders a
**closed list the library ships** (A), and the **empty layer ships first**, as phase 0. The sections below keep the
options as they were weighed; the phases at the end are the plan as decided.

## What has to hold

- **One page, three engines.** A page draws the same in libGDX (and the browser), Godot and jME: that is what
  `tools/godot-parallax-shots.sh` and `tools/jme-parallax-shots.sh` prove, frame by frame, at 2/255. An effect either
  draws the same there too, or the editor says, on the effect's own controls, which engine will not draw it.
- **No allocation per frame** (`FrameAllocationTest`): particles live in a pool sized when the page is built.
- **Fill rate is the cost** (`:demo:stress`, in the editor repository): a particle is a quad, and hundreds of overlapping ones cost more than
  the whole page. An effect carries a hard cap, and the editor shows it.
- **`core/src` is GWT**: no reflection, no `java.util.Random` seeding tricks that differ in JavaScript. A seeded
  generator written in the code, the same on every engine, makes particles deterministic, and so comparable by pixels.
- **jME draws through `JmeBatch`**, which implements only `draw(region, x, y, width, height)`. libGDX's own
  `ParticleEffect` draws sprites through `draw(texture, float[] vertices, ...)`, and throws there.

## The layer kinds

`Parallax_Model` gains `kind` (format 5): `IMAGE` (every page so far, the default when the field is missing),
`EMPTY`, `PARTICLES`, `SHADER`, and later r147's `SEQUENCE`. A layer keeps its place in the back-to-front order, its
speeds, decal and size, whatever its kind: an effect between the far hills and the trees scrolls at the speed of that
depth, and the trees are drawn over it.

- **`EMPTY`** draws nothing. It holds a slot a game can fill: `ParallaxPageReader` calls a `LayerHook` the game
  registered under the layer's `name`, with the layer's scrolled origin and the batch, in draw order. That is the
  escape hatch for anything the page cannot describe (a libGDX `ParticleEffect`, a Godot `GPUParticles2D`, a game
  sprite that should sit between two layers), and it is portable by construction: each engine calls its own hook.
- **`PARTICLES`** is an engine's own particle effect, named by file in the page, one file per engine (below).
- **`SHADER`** is one of a closed list of effects the library ships (below).

## Particles: options

| | A. Our own emitter, in the page | B. Each engine's native system | C. Hook only (`EMPTY`) |
|---|---|---|---|
| What the page stores | rate, lifetime, speed and angle ranges, gravity, wind, size and alpha over life, an atlas region, a cap, a seed | a file per engine (libGDX `.p`, a Godot `.tscn`) | a name |
| Same picture everywhere | yes, ported line by line like `act`/`tile`, and checked by the pixel rounds with a fixed seed | no: three systems, three looks | whatever the game does |
| jME | draws through `draw(region, x, y, w, h)`: works as is | no 2D particle system; would need `ParticleEffect` → throws | the game's |
| Editor | edits and previews it | can only preview libGDX's | shows an outline of the slot |
| Cost to build | one emitter in Java, one in GDScript, a pixel round | three tools, three formats, no check | small |

Recommended was A, with C underneath. **Decided: B, with C underneath.** The page stores, for a `PARTICLES` layer,
one effect file per engine, as paths relative to the page like its images: a libGDX `.p` (drawn by `core` with
libGDX's `ParticleEffect`, in a desktop game and in the browser) and a Godot scene holding a `GPUParticles2D` or
`CPUParticles2D` (instanced by `plax_background.gd` at the layer's place in the draw order). What follows from it:

- **jME draws no particles.** It has no 2D particle system, and `ParticleEffect` draws through
  `draw(texture, float[] vertices, ...)`, which `JmeBatch` throws on. The jME reader skips a `PARTICLES` layer, without
  throwing, and `EffectSupport` says so; a game that wants them there fills an `EMPTY` layer itself.
- **The same page looks different per engine.** Each file is drawn by its own system: nothing compares them by pixels.
  The rounds check what can be checked: the effect is there, at its layer's place in the draw order, where the layer
  scrolled it, and an engine with no file for it draws nothing and says so once.
- **An engine with no file is a missing engine, not an error**: the editor marks it on the layer.
- **Allocation and cost stay ours to hold.** `ParticleEffect` keeps its particles in arrays sized by its emitters'
  max count; `FrameAllocationTest` must still count zero bytes with a particle layer playing, and `:demo:stress`
  measures one.

Open, asked on phase 1: where the effect sits when the view moves (pinned to a point of the layer and scrolled with
it, or following the view so snow never runs out), and so what a repeating layer does with it.

## Shaders: options

There is no shader language the three engines share: libGDX takes GLSL ES (and WebGL in a browser), jME GLSL in a
material definition, Godot its own shading language.

| | A. A closed list the library ships | B. Raw shader source in the page | C. Hook only |
|---|---|---|---|
| What the page stores | an effect name and its few numbers (`WAVE`: amplitude, wavelength, speed) | GLSL for libGDX, optionally a Godot and a jME version | a name |
| Same picture everywhere | yes, each effect written three times and checked by pixels | only where someone wrote each version; otherwise the editor flags the missing engine | the game's |
| Editor | a list and sliders | a text box, compiled for libGDX only | an outline |

**Recommended and decided: A.** Start with two: `WAVE` (a horizontal ripple, for water and heat) applied to the layer below it,
or to its own image, and `FOG` (a scrolling noise that fades the layers behind it). Each one is a batch shader in
libGDX (a flush per shaded layer, which the stress run must measure), a `CanvasItem` shader in Godot, and a material
on `JmeBatch`'s meshes in jME, which means `JmeBatch` learns per-run materials.

## Saying what an engine cannot draw

`core/src` gets a table, `EffectSupport`: for each kind and each effect, whether libGDX, the browser, Godot and jME
draw it, and a line saying why not (jME and particles: no 2D particle system). The editor reads it: every control of an
effect layer shows the engines it works on (a row of marks next to the kind selector), and a control whose value an
engine ignores is marked on that control; a `PARTICLES` layer with no Godot file marks Godot as missing. Each reader's
round writes the result the table claims, so the table cannot drift from the truth: a `yes` without a passing round
fails CI (a pixel round for shaders, a presence-and-order round for particles).

## Phases

Each is a task under r146, run in this order (`--after`). Every one that stores a field bumps the format once:
`CURRENT_VERSION`, Kryo, `Utils_Page_Json`, `plax_page.gd`, `PlaxFormatTest`, README's "File formats".

0. `kind` and a layer `name` (the hook's key) in the format, `EMPTY` drawn as nothing and its hook, in all three
   readers. A pixel round with an empty layer and a hook drawing a region between two layers.
1. `PARTICLES` in `core` (libGDX and the browser): the effect file paths in the format, `ParticleEffect` loaded with the
   page and drawn in order, `ReaderCases` placing it in every repeat mode, `FrameAllocationTest`, a stress run; the jME
   reader skipping it; `EffectSupport`.
2. `PARTICLES` in Godot: `plax_page.gd` reads the scene path, `plax_background.gd` instances it in draw order and moves
   it as the layer scrolls; a round that checks its place.
3. `SHADER`: `WAVE` and `FOG` in all three engines, `JmeBatch` materials, a pixel round, `EffectSupport`.
4. The editor: kind selector, particle file pickers, shader controls, support marks, live preview (libGDX). After the
   repo split (r132), in JKS_Tools2D_ParallaxEditor.
5. r147's `SEQUENCE`, on the same `kind` field: r147's own phases, after phase 0.
