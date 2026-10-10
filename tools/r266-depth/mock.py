#!/usr/bin/env python3
"""r266: a MOCK of five ways to give a parallax page a 2.5D or "round" feel, drawn in numpy from Hiver's four regions
(core/test-data/samples/Hiver.png), so they can be judged moving before any is built in core. Not the reader: every
layer here is a surface in a small 3D scene, each screen pixel cast back onto it (perspective, no tiling code).

  flat    today's parallax: four billboards at depths 1.5, 3, 6 and 24, the camera pans. The control.
  floor   the same, over a ground plane drawn row by row ("mode 7" / line scroll): each row at its own depth.
  planet  the same billboards bent down by the camera's distance along a planet of radius 9: far ones bend more.
  dolly   the camera moves in and out instead of panning: near layers grow faster than far ones (the reader zooms all
          layers alike today).
  tower   a Shantae / Nebulus tower: the camera orbits a brick cylinder, and Hiver's layers are panoramas on bigger
          cylinders around it, so the near ones turn faster and every edge curves.

  python3 tools/r266-depth/mock.py OUT [SECONDS]   (needs numpy, Pillow, ffmpeg; writes OUT/<mode>.mp4 and
                                                    OUT/<mode>.png, its middle frame, and OUT/contact.png)
"""
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw

W, H, FPS = 640, 360, 30
F = 300.0  # focal length in pixels
CX, CY = W / 2, H * 0.52  # the horizon sits a little under the middle
EYE = 1.0  # the camera's height above the ground
SKY_TOP, SKY_BOTTOM = np.array([150, 170, 200.0]), np.array([225, 230, 238.0])

# Hiver's regions, as the atlas packs them (index 1 trees, 2 mountain, 3 far peaks, 4 sky band), halved.
REGIONS = {'trees': (1, 365, 3403, 580), 'mountain': (1, 947, 3645, 580), 'peaks': (1, 1, 3645, 362),
           'band': (1, 1529, 3645, 580)}


def load(root):
    atlas = Image.open(os.path.join(root, 'core/test-data/samples/Hiver.png')).convert('RGBA')
    out = {}
    for name, (x, y, w, h) in REGIONS.items():
        out[name] = np.asarray(atlas.crop((x, y, x + w, y + h)).resize((w // 2, h // 2), Image.LANCZOS),
                               dtype=np.float64) / 255
    return out


def bricks():
    """The tower's wall: staggered bricks with a window every few rows."""
    w, h = 512, 256
    y, x = np.mgrid[0:h, 0:w]
    row = y // 16
    xs = (x + (row % 2) * 16) % 32
    mortar = (y % 16 < 2) | (xs < 2)
    tone = 0.85 + 0.15 * np.sin(row * 12.9898 + (x + (row % 2) * 16) // 32 * 78.233) ** 2
    rgb = np.stack([0.62 * tone, 0.45 * tone, 0.36 * tone], axis=-1)
    rgb[mortar] = (0.35, 0.3, 0.28)
    window = ((x % 128) > 52) & ((x % 128) < 76) & ((y % 128) > 40) & ((y % 128) < 88)
    rgb[window] = (0.12, 0.12, 0.2)
    return np.concatenate([rgb, np.ones((h, w, 1))], axis=-1)


def ground():
    """Snow with blue-grey stones in a loose grid, so the floor's rows show their depth."""
    n = 256
    z, x = np.mgrid[0:n, 0:n]
    rgb = np.ones((n, n, 3)) * (0.86, 0.88, 0.92)
    rgb *= (0.96 + 0.04 * np.sin(x * 0.31) * np.sin(z * 0.23))[..., None]
    cell = 64
    jx = (np.sin((x // cell) * 3.1 + (z // cell) * 7.7) * 12)
    jz = (np.cos((x // cell) * 5.3 + (z // cell) * 2.9) * 12)
    stone = ((x % cell - cell / 2 - jx) ** 2 + 2.2 * (z % cell - cell / 2 - jz) ** 2) < 90
    rgb[stone] = (0.5, 0.56, 0.64)
    return np.concatenate([rgb, np.ones((n, n, 1))], axis=-1)


def sample(tex, u, v, repeat_v=False):
    """Nearest sample of tex at (u, v) in 0..1 (u repeats); alpha 0 outside v's 0..1 unless repeat_v."""
    th, tw = tex.shape[:2]
    inside = np.ones(u.shape, bool) if repeat_v else (v >= 0) & (v < 1)
    i = np.clip(((v % 1 if repeat_v else v) * th).astype(int), 0, th - 1)
    j = ((u % 1) * tw).astype(int) % tw
    out = tex[i, j]
    out[..., 3] *= inside
    return out


# The billboards: name, depth, bottom (Y), height (world units). The trees stand on the ground; the rest sink under the
# horizon like Hiver's page does.
BOARDS = [('band', 24.0, -9.0, 14.0), ('peaks', 6.0, -0.4, 1.1), ('mountain', 3.0, -0.5, 1.9),
          ('trees', 1.5, -0.45, 1.6)]

SX, SY = np.meshgrid(np.arange(W) + 0.5, np.arange(H) + 0.5)


def sky():
    t = np.clip(SY / H, 0, 1)[..., None]
    rgb = SKY_TOP * (1 - t) + SKY_BOTTOM * t
    return np.concatenate([rgb / 255, np.ones((H, W, 1))], axis=-1)


def board(tex, z, bottom, height, cam_x, cam_z=0.0, planet=None):
    """A billboard at depth z, as seen from (cam_x, EYE, cam_z): each pixel's world X and Y, then its texel."""
    d = z - cam_z
    x = cam_x + (SX - CX) * d / F
    y = EYE - (SY - CY) * d / F
    if planet:
        # Along a planet of radius R the surface drops by s^2 / 2R at a distance s from the camera.
        y = y + (x - cam_x) ** 2 / (2 * planet)
    width = height * tex.shape[1] / tex.shape[0]
    rgba = sample(tex, x / width, (bottom + height - y) / height)
    return rgba, np.full((H, W), d)


def floor(tex, cam_x, cam_z=0.0):
    """The ground plane, row by row: below the horizon a row at sy sees the ground at depth EYE * F / (sy - CY)."""
    below = SY > CY + 0.5
    d = np.where(below, EYE * F / np.maximum(SY - CY, 1e-3), 1e9)
    x = cam_x + (SX - CX) * d / F
    rgba = sample(tex, x / 2.0, (d + cam_z) / 2.0, repeat_v=True)
    rgba[..., 3] *= below
    # Mist toward the horizon: the far rows blend into the sky's bottom colour.
    haze = np.clip(1 - np.exp(-d / 14), 0, 1)[..., None]
    rgba[..., :3] = rgba[..., :3] * (1 - haze) + SKY_BOTTOM / 255 * haze
    return rgba, d


def cylinder(tex, r, phi, around, bottom, height, dist, shade=False, repeat_v=False):
    """A cylinder of radius r round the axis, the camera at dist from it turned by phi (radians): the near side when
    r < dist (the tower), the far side when r > dist (a panorama round everything). The texture repeats `around`
    times round it."""
    dx = (SX - CX) / F
    dy = -(SY - CY) / F
    a = dx * dx + 1
    disc = dist * dist - a * (dist * dist - r * r)
    ok = disc >= 0
    root = np.sqrt(np.maximum(disc, 0))
    t = (dist - root) / a if r < dist else (dist + root) / a
    px, pz = t * dx, -dist + t
    theta = np.arctan2(px, -pz) + phi
    y = EYE + t * dy
    rgba = sample(tex, theta * around / (2 * math.pi), (bottom + height - y) / height, repeat_v)
    rgba[..., 3] *= ok
    if shade:
        # The wall darkens as it turns away: its normal against the ray.
        facing = np.clip((-px * dx - (pz) * 1) / (r * np.sqrt(a)), 0, 1)
        rgba[..., :3] *= (0.35 + 0.65 * facing)[..., None]
    return rgba, np.where(ok, t, 1e9)


def composite(layers):
    """Back to front by each pixel's own depth: a layer's depth may vary across the screen (the floor, the cylinders)."""
    depth = np.stack([d for _, d in layers])
    order = np.argsort(-depth, axis=0)
    rgbas = np.stack([c for c, _ in layers])
    out = sky()[..., :3]
    for k in range(len(layers)):
        idx = order[k]
        c = np.take_along_axis(rgbas, idx[None, ..., None], axis=0)[0]
        a = c[..., 3:4]
        out = out * (1 - a) + c[..., :3] * a
    return out


def frame(mode, s, tex):
    """s: 0..1 through the clip."""
    pan = 6 * (0.5 - 0.5 * math.cos(math.pi * s))  # eased, 6 units
    if mode in ('flat', 'floor', 'planet'):
        # The sky band is the sky, not on the ground: it does not bend round the planet.
        layers = [board(tex[n], z, b, h, pan, planet=12.0 if mode == 'planet' and n != 'band' else None)
                  for n, z, b, h in BOARDS]
        if mode == 'floor':
            layers.append(floor(tex['ground'], pan))
        return composite(layers)
    if mode == 'dolly':
        cam_z = 1.0 * math.sin(math.pi * s)  # in by one unit, then back
        return composite([board(tex[n], z, b, h, 0.0, cam_z) for n, z, b, h in BOARDS])
    if mode == 'tower':
        phi = s * math.pi * 0.75
        dist = 2.2
        layers = [cylinder(tex['band'], 40, phi * 0.98, 6, -60, 110, dist),
                  cylinder(tex['peaks'], 14, phi, 6, -1.3, 4.0, dist),
                  cylinder(tex['mountain'], 7, phi, 5, -1.3, 4.4, dist),
                  cylinder(tex['trees'], 4, phi, 6, -0.4, 2.0, dist),
                  cylinder(tex['bricks'], 0.9, phi, 5, 0, 1.6, dist, shade=True, repeat_v=True)]
        return composite(layers)
    raise ValueError(mode)


def main():
    out = sys.argv[1]
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 4
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
    os.makedirs(out, exist_ok=True)
    tex = load(root)
    tex['bricks'] = bricks()
    tex['ground'] = ground()
    modes = ['flat', 'floor', 'planet', 'dolly', 'tower']
    stills = []
    for mode in modes:
        n = int(seconds * FPS)
        video = subprocess.Popen(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                                  '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-', '-pix_fmt', 'yuv420p',
                                  os.path.join(out, f'{mode}.mp4')], stdin=subprocess.PIPE)
        for k in range(n):
            img = (np.clip(frame(mode, k / (n - 1), tex), 0, 1) * 255).astype(np.uint8)
            video.stdin.write(img.tobytes())
            if k == n // 2:
                Image.fromarray(img).save(os.path.join(out, f'{mode}.png'))
                stills.append(Image.fromarray(img))
        video.stdin.close()
        video.wait()
    label = 20
    sheet = Image.new('RGB', (W * 2, (H + label) * 3), 'black')
    draw = ImageDraw.Draw(sheet)
    for i, (mode, im) in enumerate(zip(modes, stills)):
        x, y = (i % 2) * W, (i // 2) * (H + label)
        draw.text((x + 6, y + 4), f'{mode} (mock, r266)', fill='white')
        sheet.paste(im, (x, y + label))
    sheet.save(os.path.join(out, 'contact.png'))


if __name__ == '__main__':
    main()
