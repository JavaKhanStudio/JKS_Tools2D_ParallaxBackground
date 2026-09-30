# Effect layers: particles, shaders, and the empty layer

Design for atelier r146 (2026-09-30), written before any code: a page should hold layers that are not an image, the
first being particle effects (snow, rain, dust, fireflies) and shaders (a heat haze, a water ripple), and it must say
plainly, in the editor, what a given engine cannot draw. r147 (a layer made of a sequence of images) adds a layer kind
too: the `kind` field below is shared with it.

## What has to hold

- **One page, three engines.** A page draws the same in libGDX (and the browser), Godot and jME: that is what
  `tools/godot-parallax-shots.sh` and `tools/jme-parallax-shots.sh` prove, frame by frame, at 2/255. An effect either
  draws the same there too, or the editor says, on the effect's own controls, which engine will not draw it.
- **No allocation per frame** (`FrameAllocationTest`): particles live in a pool sized when the page is built.
- **Fill rate is the cost** (`:demo:stress`): a particle is a quad, and hundreds of overlapping ones cost more than
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
- **`PARTICLES`** is the library's own emitter, described in the page (below).
- **`SHADER`** is one of a closed list of effects the library ships (below).

## Particles: options

| | A. Our own emitter, in the page | B. Each engine's native system | C. Hook only (`EMPTY`) |
|---|---|---|---|
| What the page stores | rate, lifetime, speed and angle ranges, gravity, wind, size and alpha over life, an atlas region, a cap, a seed | a file per engine (libGDX `.p`, a Godot `.tscn`) | a name |
| Same picture everywhere | yes, ported line by line like `act`/`tile`, and checked by the pixel rounds with a fixed seed | no: three systems, three looks | whatever the game does |
| jME | draws through `draw(region, x, y, w, h)`: works as is | no 2D particle system; would need `ParticleEffect` → throws | the game's |
| Editor | edits and previews it | can only preview libGDX's | shows an outline of the slot |
| Cost to build | one emitter in Java, one in GDScript, a pixel round | three tools, three formats, no check | small |

**Recommended: A, with C underneath.** A gives snow, rain, dust and leaves on every engine with one set of numbers and a
proof; C covers what A cannot express. The emitter keeps its particles in the layer's space: they scroll with the
layer, and they wrap around the view on the axes the page repeats on, so a snow band never ends and never repeats a
visible pattern (tiling an emitter's picture would show the same flakes side by side).

## Shaders: options

There is no shader language the three engines share: libGDX takes GLSL ES (and WebGL in a browser), jME GLSL in a
material definition, Godot its own shading language.

| | A. A closed list the library ships | B. Raw shader source in the page | C. Hook only |
|---|---|---|---|
| What the page stores | an effect name and its few numbers (`WAVE`: amplitude, wavelength, speed) | GLSL for libGDX, optionally a Godot and a jME version | a name |
| Same picture everywhere | yes, each effect written three times and checked by pixels | only where someone wrote each version; otherwise the editor flags the missing engine | the game's |
| Editor | a list and sliders | a text box, compiled for libGDX only | an outline |

**Recommended: A.** Start with two: `WAVE` (a horizontal ripple, for water and heat) applied to the layer below it,
or to its own image, and `FOG` (a scrolling noise that fades the layers behind it). Each one is a batch shader in
libGDX (a flush per shaded layer, which the stress run must measure), a `CanvasItem` shader in Godot, and a material
on `JmeBatch`'s meshes in jME, which means `JmeBatch` learns per-run materials.

## Saying what an engine cannot draw

`core/src` gets a table, `EffectSupport`: for each kind and each effect, whether libGDX, the browser, Godot and jME
draw it, and a line saying why not. The editor reads it: every control of an effect layer shows the engines it works
on (a row of three marks next to the kind selector), and a control whose value an engine ignores is marked on that
control. Each reader's pixel round writes the result the table claims, so the table cannot drift from the truth: a
`yes` without a passing round fails CI.

## Phases

0. `kind` and a layer `name` (the hook's key) in the format (Kryo, `Utils_Page_Json`, `plax_page.gd`, `PlaxFormatTest`, README), `EMPTY` drawn as nothing
   and its hook, in all three readers. A pixel round with an empty layer and a hook drawing a region between two
   layers.
1. `PARTICLES` in `core`: the emitter, its seeded generator, the pool, `ReaderCases` counting its draws in every repeat
   mode, `FrameAllocationTest`, a stress run.
2. The emitter in Godot, a pixel round (`engines/godot/tests/effects`), jME run through the same round.
3. The editor: kind selector, particle controls, support marks, live preview.
4. `SHADER`: `WAVE` and `FOG` per engine, `JmeBatch` materials, the round, the editor.
5. r147's `SEQUENCE`, on the same `kind` field.
