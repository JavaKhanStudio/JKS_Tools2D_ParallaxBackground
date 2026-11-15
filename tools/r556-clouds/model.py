"""r556: the clouds lab's PUFFS and WISPS shaders modelled in numpy, to tune a formula without a GWT build per try.

    python3 tools/r556-clouds/model.py OUT.png

Draws the three settings (a Mediterranean day, a cold forest night, a medieval fantasy evening), PUFFS over WISPS, as
CloudsLab.PUFFS_FRAGMENT and WISPS_FRAGMENT do: the same hash, lattice, union, octaves, cost and light, in float32, over
the sky's gradient alone (no page). The numbers are CloudsLab's (SKY_TOP, SKY_LOW, LIT, SHADE, SUN_ANGLE, STYLES):
change the shader there, change it here. A picture to read, not a test: the shader in the lab is the one judged.
The hash is a permutation polynomial on whole numbers: the sine hash this began with drew other clouds in float64 than
in float32, as it would from one GPU to the next.
"""
import sys

import numpy as np
from PIL import Image

W, H = 760, 428
WORLD_W, WORLD_H = 40.0, 22.5
SIZE = (64.0, 64.0 * 930 / 5000)  # the clouds layer: MADE_SIZE x the world's width, calm's Clouds strip's shape
FOOT = 0.50 * WORLD_H
SKY_TOP = [(0.16, 0.45, 0.86), (0.015, 0.03, 0.085), (0.27, 0.33, 0.64)]
SKY_LOW = [(0.74, 0.88, 0.97), (0.09, 0.15, 0.27), (0.99, 0.74, 0.50)]
LIT = [(1, 1, 1), (0.60, 0.68, 0.84), (1, 0.88, 0.66)]
SHADE = [(0.70, 0.80, 0.93), (0.05, 0.08, 0.16), (0.62, 0.42, 0.56)]
SUN_ANGLE = [118, 62, 35]
# coverage, size, stretch, softness, base, flat, rim, opacity, churn
STYLES = [
    [(0.40, 3.4, 1.3, 0.035, 0.24, 5, 0.3, 1, 1), (0.52, 2.2, 6, 0.30, 0.45, 0.6, 0.2, 0.75, 0.04)],
    [(0.44, 3.6, 1.5, 0.08, 0.24, 4, 0.9, 0.9, 1), (0.60, 2, 7, 0.35, 0.45, 0.5, 0.9, 0.7, 0.03)],
    [(0.48, 4.2, 1.2, 0.03, 0.22, 6, 0.6, 1, 1), (0.56, 2.4, 6, 0.30, 0.45, 0.6, 0.6, 0.8, 0.04)],
]
f32 = np.float32


def fract(x):
    return x - np.floor(x)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


TAU = 6.2831853


def permute(x):
    return np.mod((34.0 * x + 1.0) * x, 289.0)

def hash1(x, y, k):
    """Whole numbers in, a 289th out: exact in a float's 24 bits, the same on every GPU (a sine hash is not)."""
    return permute(permute(permute(np.mod(x, 289.0)) + np.mod(y, 289.0)) + k) / 289.0


def hash2(x, y):
    return hash1(x, y, 0.0)


def noise(px, py, period):
    ix, iy = np.floor(px), np.floor(py)
    fx, fy = px - ix, py - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x0, x1 = np.mod(ix, period), np.mod(ix + 1, period)
    top = hash2(x0, iy) * (1 - fx) + hash2(x1, iy) * fx
    bottom = hash2(x0, iy + 1) * (1 - fx) + hash2(x1, iy + 1) * fx
    return top * (1 - fy) + bottom * fy


def field(px, py, t, cells):
    return 0.5 * noise(px, py, cells) + 0.25 * noise(2 * px + t, 2 * py - 0.5 * t, 2 * cells) \
        + 0.125 * noise(4 * px - 1.6 * t, 4 * py - 1.1 * t, 4 * cells)


def clouds(setting, style=1, t=3.0, scroll=0.0, steps=0):
    """WISPS."""
    coverage, size, stretch, soft, base, flat, rim, opacity, _ = STYLES[setting][style]
    ys, xs = np.mgrid[0:H, 0:W].astype(f32)
    wx, wy = (xs + 0.5) / W * WORLD_W, (H - ys - 0.5) / H * WORLD_H
    lx, ly = np.mod(wx + scroll, SIZE[0]), wy - FOOT
    inside = (ly >= 0) & (ly <= SIZE[1])
    up = ly / SIZE[1]
    cells = max(1, round(SIZE[0] / (size * stretch)))
    cw, chh = SIZE[0] / cells, size
    px, py = lx / cw, ly / chh
    n = field(px, py, t, cells)
    fine = 0.0625 * noise(8 * px + 2.3 * t, 8 * py - 2.0 * t, 8 * cells)
    cost = np.where(up < base, (base - up) * flat, (up - base) / (1 - base) * 0.16)
    cost = cost + 0.5 * (1 - smoothstep(0, 0.12, up) * smoothstep(0, 0.1, 1 - up))
    d = (n + fine) / 0.9375 - (0.72 - 0.44 * coverage) - cost
    alpha = smoothstep(0, max(soft, 0.004), d)
    angle = np.radians(SUN_ANGLE[setting])
    sun = (WORLD_W / 2 + 16 * np.cos(angle), 12 + 8.5 * np.sin(angle))
    dx, dy = sun[0] - wx, sun[1] - wy
    dist = np.maximum(np.hypot(dx, dy), 1e-4)
    sx, sy = dx / dist, dy / dist
    toward = field(px + 0.4 * sx * chh / cw, py + 0.4 * sy, t, cells)
    lit = 0.5 + 4.5 * (n - toward) + 0.45 * (up - base) - 0.6 * d
    lit = np.clip(lit, 0, 1)
    if steps:
        lit = np.floor(lit * steps + 0.5) / steps
    near = 1 - smoothstep(0, 0.55, np.hypot(dx / WORLD_H, dy / WORLD_H))
    thin = 1 - smoothstep(0, 3 * soft + 0.06, d)
    out = np.zeros((H, W, 3), f32)
    sky_t = (wy / WORLD_H)[..., None]
    sky = np.array(SKY_LOW[setting], f32) * (1 - sky_t) + np.array(SKY_TOP[setting], f32) * sky_t
    color = np.array(SHADE[setting], f32) * (1 - lit[..., None]) + np.array(LIT[setting], f32) * lit[..., None]
    silver = np.minimum(1, 1.15 * np.array(LIT[setting], f32))
    k = (rim * thin * near)[..., None]
    color = color * (1 - k) + silver * k
    a = (alpha * opacity * inside)[..., None]
    disc = np.clip((1.5 - np.hypot(wx - sun[0], wy - sun[1])) * 8, 0, 1)[..., None]
    out = sky * (1 - disc) + np.array((1, 0.97, 0.85), f32) * disc
    return out * (1 - a) + color * a


K = 9.0
def puff_field(px, py, cells, t, coverage, stretch):
    """A smooth union of round puffs (log-sum-exp of r (1 - (d/r)^2)), over the 3x3 cells round the pixel."""
    ix, iy = np.floor(px), np.floor(py)
    acc = np.zeros(px.shape, f32)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx, cy = ix + ox, iy + oy
            hx = np.mod(cx, cells)
            a, b, c2, d = hash1(hx, cy, 1), hash1(hx, cy, 2), hash1(hx, cy, 3), hash1(hx, cy, 4)
            row = cy
            # which columns hold a cloud: a slow wave of hashes along the layer, against the coverage
            g = 0.5 * hash1(np.floor(hx / 4.0), 0 * cy, 5) + 0.3 * hash1(np.floor(np.mod(hx + 2.0, cells) / 4.0), 0 * cy, 7) + 0.2 * hash1(hx, 0 * cy, 6)
            keep = smoothstep(1 - coverage - 0.1, 1 - coverage + 0.1, g)
            tall = np.where(row == 0, 1.0, np.where(row == 1, 0.8 * smoothstep(0.45, 0.8, keep * (0.5 + 0.5 * c2)), 0.0))
            r = (0.62 + 0.3 * c2) * keep * tall * (1 + 0.1 * np.sin(TAU * (0.07 * t + d)))
            ccx = cx + 0.3 + 0.4 * a + 0.08 * np.sin(TAU * (0.05 * t + b))
            ccy = cy + np.where(row == 0, 0.25 + 0.2 * b, 0.05 + 0.3 * b)
            dx, dy = (px - ccx) * stretch / 1.25, py - ccy
            rr = np.maximum(r, 1e-3)
            sdf = rr * (1 - (dx * dx + dy * dy) / (rr * rr))
            acc += np.where(r > 0.02, np.exp(np.clip(K * sdf, -40, 40)), 0)
    # small bumps: the union's surface pushed in and out by two crossing waves, moving on the clock
    bumps = 0.5 * np.sin(TAU * (2.3 * px * stretch / 1.25 + 0.9 * py + 0.11 * t)) * np.sin(TAU * (2.1 * py - 0.7 * px + 0.3 - 0.08 * t)) \
        + 0.5 * np.sin(TAU * (3.7 * px * stretch / 1.25 - 1.3 * py - 0.13 * t)) * np.sin(TAU * (3.1 * py + 1.9 * px + 0.09 * t))
    return np.log(np.maximum(acc, 1e-12)) / K, 0.035 * 2 * bumps

def puffs(setting, t=3.0, steps=0, coverage=None):
    cov, size, stretch, soft, base, _flat, rim, opacity, _ = STYLES[setting][0]
    if coverage: cov = coverage
    ys, xs = np.mgrid[0:H, 0:W].astype(f32)
    wx, wy = (xs + 0.5) / W * WORLD_W, (H - ys - 0.5) / H * WORLD_H
    lx, ly = np.mod(wx, SIZE[0]), wy - FOOT
    inside = (ly >= 0) & (ly <= SIZE[1])
    cells = max(1, round(SIZE[0] / (size * stretch)))
    cw, ch = SIZE[0] / cells, size
    st = cw / ch
    px, py = lx / cw, (ly - base * SIZE[1]) / ch
    angle = np.radians(SUN_ANGLE[setting])
    sun = (WORLD_W / 2 + 16 * np.cos(angle), 12 + 8.5 * np.sin(angle))
    dx, dy = sun[0] - wx, sun[1] - wy
    dist = np.maximum(np.hypot(dx, dy), 1e-4); sx, sy = dx / dist, dy / dist
    # small bumps on the big ones: the field's edge pushed in and out by two sines
    body, bumps = puff_field(px, py, cells, t, cov, st)
    s = body + bumps
    e = 0.12
    toward = puff_field(px + e * sx / st, py + e * sy, cells, t, cov, st)[0]
    foot = smoothstep(0.0, 0.1, py - 0.2)
    alpha = smoothstep(0, max(soft, 0.004) * 2, s) * foot
    up = (py - 0.2) / 1.6
    lit = np.clip(0.5 + 0.26 * (body - toward) / e + 0.35 * (up - 0.4) + 0.5 * bumps, 0, 1)
    if steps: lit = np.floor(lit * steps + 0.5) / steps
    sky_t = (wy / WORLD_H)[..., None]
    sky = np.array(SKY_LOW[setting], f32) * (1 - sky_t) + np.array(SKY_TOP[setting], f32) * sky_t
    color = np.array(SHADE[setting], f32) * (1 - lit[..., None]) + np.array(LIT[setting], f32) * lit[..., None]
    near = 1 - smoothstep(0, 0.55, np.hypot(dx / WORLD_H, dy / WORLD_H))
    thin = 1 - smoothstep(0, 0.12, s)
    k = (rim * thin * near)[..., None]
    color = color * (1 - k) + np.minimum(1, 1.15 * np.array(LIT[setting], f32)) * k
    a = (alpha * opacity * inside)[..., None]
    disc = np.clip((1.5 - np.hypot(wx - sun[0], wy - sun[1])) * 8, 0, 1)[..., None]
    out = sky * (1 - disc) + np.array((1, 0.97, 0.85), f32) * disc
    return out * (1 - a) + color * a


def sheet(steps=0):
    """PUFFS over WISPS, in the three settings."""
    return np.concatenate([np.concatenate([puffs(i, steps=steps) for i in range(3)], axis=1),
                           np.concatenate([clouds(i, 1) for i in range(3)], axis=1)], axis=0)


if __name__ == "__main__":
    Image.fromarray((np.clip(sheet(), 0, 1) * 255).astype(np.uint8)).save(sys.argv[1])
    print(sys.argv[1])
