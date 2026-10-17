# Daylight and moonlight: what the shadow lab's light is made of

Simon on r425 (17 Oct 2026): "darkness [...] needs to be higher when there is no sun, way lower when the sun is at the
zenith, can climb back up while it's going down. It should not do so in a linear fashion: I want you to do some
research about it and how it behaves in real life. [...] Color of the light, color when the sun is going down, going
up, and how it affects the sky and the world. Also do the same for the moon."

This page is that research, and what of it the shadow lab (`core/browser-test/launcher/.../ShadowLab.java`, its `Light`)
uses. The numbers are the published clear-sky ones, from the sources named at the end; this session had no web access,
so they are as those sources give them, not re-measured. `tools/r425-daylight/daylight.py` computes the lab's tables
from the models below, and checks itself against the measured values (its lux at 30 degrees is 50,000, the published
figure is about 50,000-60,000).

## How much light: lux by the sun's height

The light on level ground under a clear sky, sun and sky together:

| sun height | lux | what it is |
|-----------:|----:|------------|
| 90 | 115,000 | the sun overhead (tropics only) |
| 60 | 97,000 | a summer noon at mid latitudes |
| 30 | 50,000 | mid-morning |
| 20 | 31,000 | |
| 10 | 13,000 | |
| 5 | 5,200 | the golden hour |
| 0 | 400-700 | sunrise, sunset (the disc on the horizon) |
| -6 | 3.4 | end of civil twilight: you can still read outside |
| -12 | 0.008 | end of nautical twilight: the horizon is gone |
| -18 | 0.0006 | end of astronomical twilight: the night |
| full moon, high | 0.1-0.3 (0.27 at best) | |
| quarter moon | 0.01-0.03 | |
| moonless night | 0.001-0.002 | stars and airglow |

What this means for "darkness":

- **The light spans eight orders of magnitude**: 100,000 lux at noon, 0.001 on a moonless night. Nothing linear in the
  sun's height can follow that.
- **Eyes see it on a log scale.** Brightness feels roughly like the logarithm of the light (Weber-Fechner), and the eye
  adapts over the whole range. So at noon and at 10 degrees up the world looks about as bright, though one is eight
  times the other. Then from sunrise to the end of dusk (700 lux to 3) it darkens fast, and the night is a floor.
- **The fastest change is round the horizon.** From +5 to -6 degrees the light falls by 3 orders of magnitude, about
  as much as from noon to sunrise. At mid latitudes that takes about 45 minutes.
- **Night is not black.** A full moon gives about a quarter of what civil dusk gives. A moonless night is a tenth of that
  again: dark, but landscape silhouettes still read against the sky.

The lab takes a log of the lux: `s = (log10(lux) + 3) / (log10(lux at 'full light from') + 3)`, raised to the 'dusk
curve', from `1 - darkest night` up to 1. Defaults: darkest night 0.9, curve 1.3, full light from 20 degrees. That gives
these brightnesses: 100% from 20 degrees up, 94% at 10, 87% at 5, 74% at sunrise, 41% at -6, a full moon's night 29%,
and 10% with no moon. They are judged as a game look, not measured.

## How dark a shadow is: the sun's direct share

A cast shadow lacks the sun's beam and keeps the sky's light. How dark it is depends on how much of the light comes
straight from the sun:

| sun height | direct share, level ground | direct share, an upright face turned to the sun |
|-----------:|---------------------------:|------------------------------------------------:|
| 60 | 90% | 78% |
| 30 | 84% | 89% |
| 20 | 79% | 91% |
| 10 | 64% | 93% |
| 5 | 45% | 93% |
| 2 | 22% | 93% |
| 0 | 0 | 0 |

Real shadows are **darkest when the sun is high**. At noon, a shadow on level ground gets a tenth of the light around
it. In the last few degrees before sunset, the beam on level ground is nothing beside the sky, and shadows on the
ground fade out. A wall facing a low sun still takes 93% of its light from the beam. That is the golden hour: long
shadows, warm faces.

So "darkness way lower when the sun is at the zenith" is true of the **world**: its brightness. It is not true of its
**shadows**, which are then at their strongest. The lab keeps them apart. The world's brightness follows the lux (above).
A shadow keeps what is left without its body's direct light, times 'darkness' (1: as measured). This replaces the
'shade lifts from' and 'high sun' sliders that Simon asked for on 16 Oct at 19:52. With the sun in front of you, a high
sun's shadows land under the view anyway (the depth times tan(height)), so at noon A still matches B.

## The colour of the light

- **The sun's beam reddens as it sinks.** The air scatters blue out of it (Rayleigh, as wavelength^-4), and the air mass
  grows from 1 overhead to 38 at the horizon. Its colour temperature is about 5,500-5,800 K over 40 degrees, 4,500-5,000
  K at 20, about 3,500 K at 5, and 2,000-2,500 K at the horizon: white, then cream, gold, orange, red.

  | sun height | the beam (sRGB, white-balanced to 60 up) |
  |-----------:|----------------------|
  | 60 | 1.00 1.00 1.00 |
  | 20 | 1.00 0.95 0.83 |
  | 10 | 1.00 0.87 0.61 |
  | 5 | 1.00 0.75 0.32 |
  | 2 | 1.00 0.56 0.00 |
  | 0 | 1.00 0.25 0.00 |

- **The sky's light is blue: 10,000-20,000 K.** It is what fills a shadow, so shadows in the sun are blue, more so the
  higher the sun.
- **The light on the world is the two mixed**, each by its share. Ahead of you (the lab's sky sun), the faces you see
  are turned away from it and take the sun as level ground does: at sunrise they are mostly in the sky's light, cool and
  dim, the layers dark against a glowing sky. Behind you, they face it, and a low sun lights them gold.
- **Sunrise and sunset in the sky.** Round the sun, near the horizon, the sky glows yellow to orange to red: dust and
  haze scatter forward. The glow spreads along the horizon, strongest while the sun is between about +5 and -4 degrees.
  The zenith stays blue. Opposite the sun, a grey-blue band (the Earth's shadow) rises over the horizon after sunset,
  under a pink one (the Belt of Venus).
- **The blue hour (sun -4 to -8 degrees).** With the sun gone, the sky's light comes from sunlight that grazed the upper
  air. Ozone absorbs its orange and red (Chappuis band; Hulburt 1953), so the whole sky, and everything it lights, goes a
  deep blue. Grey would be expected; the blue comes from the ozone.
- **Sunrise is sunset in reverse**, with the same physics. Mornings are often clearer and paler: less haze and dust.
  The lab does not distinguish them.

## The moon

- **Its light is sunlight**, reflected by grey soil that reflects red a little better than blue. Its colour temperature
  is about 4,100 K, a little warmer than the sun's. Near the horizon it reddens as the sun does: an orange moonrise.
- **But moonlit nights look blue.** Under about 1 lux, the eye's rods take over from the cones (Purkinje shift). They
  see no colour and are most sensitive to blue-green, so moonlight looks silver-blue and colourless. Painters and
  films show night as blue and desaturated ("day for night") for this reason.
- **The phase does not light in proportion.** The light falls much faster than the lit fraction. A half moon gives
  about a tenth of a full moon's light, a crescent 1-2% (Krisciunas and Schaefer 1991's phase law: the opposition
  surge, the shadows on its rough surface). Full: 0.27 lux high up.
- **Its cycle.** It rises about 50 minutes later each day, 29.53 days from new to new. Full, it rises as the sun sets
  and is up all night. At first quarter it is high at sunset; at last quarter it rises at midnight. A waxing moon is
  lit on its sunward side, to the right (seen from the north) in the evening sky.
- **A full moon high up casts real shadows**: on a clear night most of its light is direct, as with the sun. They are
  faint only because everything is faint.

## What the lab does with it

- **A clock.** 'hour' moves the sun on its path: it rises at 6 at the view's left, is 'noon height' up at 12 and sets at
  18 at its right. Its height is a sine over 24 hours, so it is as low at midnight as high at noon. The moon takes the
  same path, behind the sun by its age over the month, with its phase. 'Play 24 hours' runs a day and a night; the moon
  ages a day.
- **The world**, in A: every layer tinted by the hour's light. Brightness from the lux as above; colour from the sun's,
  the sky's and the moon's light by their shares, white-balanced to a high sun ('warmth' 0 is white light at every
  hour); at low light, toward a grey blue ('night blue').
- **The sky**: the painted sky times the sky's colour at that hour (deep blue in the blue hour) and its brightness. A
  glow along the horizon and round the sun while it is low. With the sun behind you, a pink band instead. The page fog
  takes the sky's light.
- **Shadows**: drawn as a product, each one what is left without that body's direct light: blue-tinted (the sky), dark
  at noon, gone at sunset. The moon casts its own.
- **Not done**: clouds, overcast (it flattens everything: the direct share drops to nothing, and shadows go), the
  differences between seasons and latitudes, stars, the moon's own glare. The sky's colour by height is a rough model
  (daylight.py's `sky_colour`), not a radiative transfer.

## Sources

- Kasten, F. and Young, A. T. (1989). Revised optical air mass tables and approximation formula. Applied Optics 28.
- Meinel, A. B. and Meinel, M. P. (1976). Applied Solar Energy (the beam's 0.7^(AM^0.678)).
- Liu, B. Y. H. and Jordan, R. C. (1960). The interrelationship and characteristic distribution of direct, diffuse and
  total solar radiation. Solar Energy 4.
- Bond, D. S. and Henderson, F. P. (1963). The Conquest of Darkness (the twilight and moonlight illuminance tables;
  also US Naval Observatory, "Rise, set and twilight definitions").
- Schlyter, P. "Radiometry and photometry in astronomy" (full moon 0.27 lux, quarter 0.01, starlight).
- Krisciunas, K. and Schaefer, B. E. (1991). A model of the brightness of moonlight. PASP 103.
- Hulburt, E. O. (1953). Explanation of the brightness and color of the sky, particularly the twilight sky. JOSA 43.
- Wyman, C., Sloan, P.-P. and Shirley, P. (2013). Simple analytic approximations to the CIE XYZ color matching
  functions. JCGT 2(2).
- Purkinje shift: any physiology of vision text (rods peak at 507 nm, cones at 555 nm; mesopic between about 0.001
  and 3 cd/m2).
