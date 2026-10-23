# A page as the sky of a 3D world: the rings

The question (parallax-readers r35, from Simon on deux-generaux r449, 22 Oct 2026: make the three.js work « implemented
correctly in the parallax board, both in reader and everywhere else we could use the upgrade »): a game whose engine is
3D (three.js, Godot 3D, jMonkeyEngine) wants a page as the background of its world, not as a flat picture glued to the
camera. Crypto en visuel built one way and Simon picked it (deux-generaux r339, lab
[parallax-sky/compare.html](https://javakhanstudio.github.io/Crypto_en_visuel/lab/parallax-sky/compare.html): way b,
« Anneaux », over a screen-space backdrop). Its code is deux-generaux `lib/saga/sky.js`, about 130 lines of three.js over
its own copy of the reader. This page is what the readers would take from it. **Nothing here is built**: Simon answers
the questions at the end first (r35), and the build is its own tickets.

## What sky.js does

1. **Each layer is an open cylinder round the world**, centred on it, its radius from its speed: the fastest layer at
   `R0` = 330 units, the slowest at `R1` = 760, `r = R0 (smax / speed)^p` with `p = ln(R1/R0) / ln(smax/smin)`. A
   camera that moves through the world sees the bands slide against each other by perspective alone: nothing drives
   the parallax.
2. **A band spans the elevations the page gives the layer**, seen from the world's centre at the ridge's height
   (`RIDGE` = 50): its bottom at `decal_Y` (a percent of the page's height), its top a layer's height above, the page's
   height being 45 degrees of sky; `lift` and `scale` (the lab's two sliders, -2 and 0.7) lower and squeeze the page so
   its trees stand on the 3D mountains. The texture repeats round the circumference at its own aspect
   (`round(2 pi r / (h * width / height))` times).
3. **The bands draw far first, by `renderOrder`**: they share a centre, so three.js's depth sort cannot order them. They
   write no depth; the 3D ridge, drawn before them with depth, stays in front.
4. **The page's gradients are a sphere that follows the camera**, coloured by elevation; under the horizon it is the
   fog's colour, so the ground's far edge fades into it.
5. **Each band's lowest part melts into the fog colour** (a smoothstep over its bottom 22 %), and each band gets a haze
   that grows with its depth (`0.08 + 0.5 (1 - i/(n-1))^2`, by index).
6. **The whole page is multiplied by a tint**, night (`#30386e`) to dawn (`#ffd9bc`), from the episode's clock.
7. **`speedXAtRest` drifts each band** by its texture offset, as the page drifts in libGDX.

## What of it the library already has

Most of sky.js is a second spelling of what core already defines; a reader's rings should use core's words, not new ones.

| sky.js | core |
|--------|------|
| radius from speed, a power law fitted between two radii | `ParallaxDepth.distanceOf` (r444): distance = front speed / speed, the camera's distance to the front layer the one free number (`cameraDistance`). sky.js's fit is that law **compressed**: on calm-day (speeds 0.011 to 0.1) the library puts the back layer 9.1 times as far as the front, sky.js 2.3 times (`p` = 0.38) |
| haze per depth, by index | the page fog, `ParallaxPageReader.setFog(strength, colour)` and `fogOf`: `1 - exp(-strength (1/speed - 1/front))`, by speed |
| tint, night to dawn | `tintTo` (every reader has it) |
| drift by `speedXAtRest` | the same field, the same meaning |
| gradient sphere: three of the page's four colours | the page's two gradients, four colours and `topHalfSize` / `bottomHalfSize`: sky.js drops `bottomHalf_bottom` and both sizes |
| band elevation from `decal_Y` and the layer's height | the layer's box, as `ParallaxLayer` lays it out on a 2D view |
| a cross-fade into another page | not in sky.js; the reader's `transfertInto` carries the alphas a band would read |

So the page format needs **nothing new**: every number a ring needs is a layer field already saved, or a choice of the
3D scene (where its centre is, how far the front band stands, at what height the page's foot sits, the fog's colour).
Those belong to the game's call, not the page: the same page is the sky of a valley and of an ocean.

## What a reader would add

One object beside the flat background, built from the same page and the same state (scroll, cross-fade, tint), so a
page keeps one reader and two ways to draw it:

    rings = new PlaxRings(background, {        // three.js: plax.js; Godot: a Node3D; jME: a Node
      centre, front: 330,                      // the front band's radius, in world units
      far: 760,                                // optional: compress the depths into front..far, as sky.js does
      foot: 50, lift: -2, scale: 0.7,          // the page's foot on the world, and its fit
      fog: { colour, strength },               // the scene's fog: the bands' melt, their haze and the gradient's horizon
    })
    rings.update(seconds)                      // drift, cross-fade alphas, tint, from the background's state

- **The numbers once, in core.** A `ParallaxRings` in core (no drawing: radius, elevations, repeat count, render order,
  fog per band, for a page and those options) with its unit tests; each reader ports it line for line, as they port
  `ParallaxDepth` and `fogOf`. A ring round (engines/rounds) then checks the numbers per reader as JSON against core's,
  exactly, before any pixel; the stills are judged per engine by eye in its lab, because three engines' perspective
  rasterisation will not agree at 2/255.
- **three.js** (engines/threejs/src/plax.js): sky.js moved in and spoken in core's words. Its first user is
  deux-generaux, which then drops `lib/saga/sky.js` and `lib/plax.js` (its r493).
- **Godot 4** (engines/godot/addons/jks_parallax): a `Node3D` of `MeshInstance3D` open cylinders with a `ShaderMaterial`
  each, ordered by `render_priority`, depth write off; the gradient as a `WorldEnvironment` sky shader (it follows the
  camera by itself). Not the panorama sky: a Godot sky is infinitely far, so it would lose the perspective parallax.
- **jMonkeyEngine**: a `Node` of `Cylinder` geometries in the Transparent bucket with a comparator that orders them by
  radius, and the gradient in the Sky bucket. The jME reader runs core's `ParallaxPageReader` as is, so it reads the
  scroll and fades from core's own objects.
- **libGDX** has a 3D API too (`ModelBatch`, `PerspectiveCamera`); core drawing rings would make it the reference for
  this too, but no libGDX game of Simon's asks for it.
- **Each gets a lab**: one 3D scene (a ground, a ridge, a camera flight) with the page round it, sliders for `front`,
  `far`, `foot`, `lift`, `scale` and the fog, and Copy settings.

Layer kinds: an IMAGE, SEQUENCE or SHADER layer is a band (SEQUENCE and SHADER once the reader ports them flat); EMPTY
and PARTICLES are skipped at first; FLOOR, the dolly, towers and the planet are 2D ways to fake depth that the 3D world
now gives, so a band ignores them and draws the layer's image plain.

## The questions (asked on r35)

1. Which engines get the rings, and in what order.
2. Whether the bands stand at the library's depths (front / speed, as the page fog and the dolly read them) or
   compressed between two radii, as the saga's sky Simon picked does.
3. Whether the numbers live in core (one `ParallaxRings`, ported) or only in each reader.

Simon's answers are written here when they come.
