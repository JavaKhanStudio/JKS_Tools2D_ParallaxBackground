"""Daylight and moonlight, as the shadow lab uses them (r425, Simon on 17 Oct 11:08: "do some research about it and how it
behaves in real life [...] colour of the light [...] the moon"). docs/daylight.md says where each number comes from.

    python3 tools/r425-daylight/daylight.py           the tables, as text
    python3 tools/r425-daylight/daylight.py --java    the same, as ShadowLab's arrays

A clear sky, the sun h degrees over the horizon:
- the air mass (Kasten and Young 1989), the sun's beam through it (Meinel and Meinel 1976: 1361 W/m2 times
  0.7^(AM^0.678)), the sky's light on the ground (Liu and Jordan 1960: 0.271 - 0.294 of the beam's transmittance),
  in lux at 110 lm/W; under the horizon, the measured twilight (3.4 lux at -6, 0.008 at -12, 0.0006 at -18), down to
  the night's starlight and airglow, 0.001 lux;
- the share of that light that comes straight from the sun, on level ground and on an upright face turned to it: a
  cast shadow keeps the rest, the sky's alone;
- the sun's colour: a 5778 K black body through the air mass, Rayleigh scattering (0.0088 l^-4.05, l in um), haze
  (Angstrom, beta 0.08, alpha 1.3) and ozone's Chappuis band, white-balanced to the sun 60 degrees up;
- the sky's colour: the sun's light scattered by Rayleigh (l^-4), high up, after grazing the ozone layer (Hulburt
  1953: the ozone keeps the twilight zenith blue);
- the moon: Krisciunas and Schaefer 1991's phase law, 0.27 lux full and high.
Colours: Wyman, Sloan and Shirley 2013's fit of the CIE 1931 observer, to linear sRGB, then encoded (gamma 2.2), the
largest channel 1.
"""
import itertools
import math
import sys

import numpy as np

LAMBDA = np.arange(380.0, 781.0, 5.0)


def _g(lam, mu, s1, s2):
    s = np.where(lam < mu, s1, s2)
    return np.exp(-0.5 * ((lam - mu) / s) ** 2)


def cmf(lam):
    x = 1.056 * _g(lam, 599.8, 37.9, 31.0) + 0.362 * _g(lam, 442.0, 16.0, 26.7) - 0.065 * _g(lam, 501.1, 20.4, 26.2)
    y = 0.821 * _g(lam, 568.8, 46.9, 40.5) + 0.286 * _g(lam, 530.9, 16.3, 31.1)
    z = 1.217 * _g(lam, 437.0, 11.8, 36.0) + 0.681 * _g(lam, 459.0, 26.0, 13.8)
    return x, y, z


XYZ_TO_RGB = np.array([[3.2406, -1.5372, -0.4986], [-0.9689, 1.8758, 0.0415], [0.0557, -0.2040, 1.0570]])


def planck(lam_nm, t):
    lam = lam_nm * 1e-9
    return 1 / (lam ** 5 * (np.exp(1.4388e-2 / (lam * t)) - 1))


SUN = planck(LAMBDA, 5778)
UM = LAMBDA / 1000
RAYLEIGH = 0.0088 * UM ** -4.05
HAZE = 0.08 * UM ** -1.3
# Chappuis: about 0.035 in a vertical column at its 600 nm peak, a broad band.
OZONE = 0.035 * np.exp(-0.5 * ((LAMBDA - 600) / 45) ** 2)


def air_mass(h):
    return 1 / (math.sin(math.radians(h)) + 0.50572 * (h + 6.07995) ** -1.6364)


def linear_rgb(spectrum):
    x, y, z = cmf(LAMBDA)
    xyz = np.array([(spectrum * x).sum(), (spectrum * y).sum(), (spectrum * z).sum()])
    return XYZ_TO_RGB @ xyz


def balanced(rgb, white):
    c = np.clip(rgb / white, 0, None)
    return c / c.max()


def encode(c):
    return np.clip(c, 0, None) ** (1 / 2.2)


WHITE = linear_rgb(SUN * np.exp(-air_mass(60) * (RAYLEIGH + HAZE + OZONE)))
def sun_colour(h):
    """The sun's direct light h degrees up (0 to 90), encoded, the largest channel 1."""
    m = air_mass(max(h, -0.5) if h > -0.5 else -0.5)
    return encode(balanced(linear_rgb(SUN * np.exp(-m * (RAYLEIGH + HAZE + OZONE))), WHITE))


def sky_colour(h):
    """The light of the sky overhead with the sun h degrees up: the sun's beam scattered by the air (Rayleigh, l^-4).
    What reaches overhead is scattered high up, so the beam has crossed only a part of the air the sun's own light
    has, 0.15 of its air mass (no more than at 3 degrees: the sky does not whiten in the last ones) and less as the
    sun sinks; but it has crossed the ozone layer edge-on, 12 air
    masses at the horizon, 40 by -6 (the blue hour's blue)."""
    if h >= 0:
        rayleigh, ozone = 0.15 * min(air_mass(h), air_mass(3)), min(air_mass(h), 12.0)
    else:
        rayleigh, ozone = 0.15 * air_mass(3) * math.exp(h / 6), 12 + 28 * min(1.0, -h / 6)
    beam = SUN * np.exp(-rayleigh * (RAYLEIGH + HAZE) - ozone * OZONE)
    return encode(balanced(linear_rgb(beam * (RAYLEIGH + 0.3 * HAZE)), WHITE))


def lux(h):
    """A clear sky's light on the ground, sun and sky, the sun h degrees up; the night's 0.001 lux under it all."""
    if h > 0.5:
        r = math.radians(h)
        dni = 1361 * 0.7 ** (air_mass(h) ** 0.678)
        dhi = 1361 * math.sin(r) * (0.271 - 0.294 * dni / 1361)
        return 110 * (dni * math.sin(r) + dhi) + 0.001
    # Twilight, log-linear between the measured points.
    points = [(0.5, math.log10(lux(0.51))), (0, 2.75), (-6, math.log10(3.4)), (-12, math.log10(0.008)), (-18, math.log10(0.0006))]
    for (h0, l0), (h1, l1) in itertools.pairwise(points):
        if h >= h1:
            return 10 ** (l1 + (l0 - l1) * (h - h1) / (h0 - h1)) + 0.001
    return 10 ** (points[-1][1] + 0.4 * (h + 18)) + 0.001 if h > -24 else 0.001


def direct_share(h):
    """The share of the light on level ground that comes straight from the sun: a cast shadow lacks that share."""
    if h <= 0:
        return 0.0
    r = math.radians(h)
    dni = 1361 * 0.7 ** (air_mass(h) ** 0.678)
    dhi = 1361 * math.sin(r) * (0.271 - 0.294 * dni / 1361)
    return dni * math.sin(r) / (dni * math.sin(r) + dhi)


def direct_share_facing(h):
    """The share of the light on an upright face turned to the sun that comes straight from it: the sky lights half
    of such a face, the ground (albedo 0.2) the other half."""
    if h <= 0:
        return 0.0
    r = math.radians(h)
    dni = 1361 * 0.7 ** (air_mass(h) ** 0.678)
    dhi = 1361 * math.sin(r) * (0.271 - 0.294 * dni / 1361)
    ghi = dni * math.sin(r) + dhi
    face = dni * math.cos(r)
    return face / (face + dhi / 2 + 0.2 * ghi / 2)


def moon_phase_light(age):
    """The full moon's light that a moon {age} days old gives (29.53 a month; 14.77 full): Krisciunas and Schaefer."""
    a = abs(180 - 360 * (age % 29.53) / 29.53)
    return 10 ** (-0.4 * (0.026 * a + 4e-9 * a ** 4))


HEIGHTS = [-18, -15, -12, -9, -6, -4, -2, -1, 0, 1, 2, 3, 5, 7.5, 10, 15, 20, 30, 45, 60, 90]


def main():
    java = "--java" in sys.argv
    rows = []
    for h in HEIGHTS:
        rows.append((h, math.log10(lux(h)), direct_share(h), sun_colour(max(h, 0)), sky_colour(h), direct_share_facing(h)))
    if java:
        def floats(values, form):
            return ", ".join(format(v, form) + "f" for v in values)
        print(f"static final float[] LIGHT_HEIGHTS = {{ {floats([r[0] for r in rows], 'g')} }};")
        print(f"static final float[] LIGHT_LOG_LUX = {{ {floats([r[1] for r in rows], '.2f')} }};")
        print(f"static final float[] LIGHT_DIRECT = {{ {floats([r[2] for r in rows], '.2f')} }};")
        print(f"static final float[] LIGHT_FACING = {{ {floats([r[5] for r in rows], '.2f')} }};")
        print(f"static final float[] LIGHT_SUN = {{ {floats([c for r in rows for c in r[3]], '.3f')} }};")
        print(f"static final float[] LIGHT_SKY = {{ {floats([c for r in rows for c in r[4]], '.3f')} }};")
        return
    print("height  log10 lux       lux  direct  facing  sun rgb           sky rgb")
    for h, ll, d, s, k, f in rows:
        print(f"{h:6g}  {ll:9.2f}  {10 ** ll:8.4g}  {d:6.2f}  {f:6.2f}  {s[0]:.2f} {s[1]:.2f} {s[2]:.2f}    {k[0]:.2f} {k[1]:.2f} {k[2]:.2f}")
    ages = (0, 3.7, 7.4, 11, 14.77, 18.5, 22.1, 25.8)
    print("\nmoon, share of a full moon's light by age (days): " + ", ".join(f"{a:g}: {moon_phase_light(a):.3f}" for a in ages))

if __name__ == "__main__":
    main()
