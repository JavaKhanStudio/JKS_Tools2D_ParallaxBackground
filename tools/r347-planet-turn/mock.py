#!/usr/bin/env python3
"""r347: a MOCK of the planet as a turning world, beside the planet as built (r277, r330), drawn in numpy from Hiver's
regions (tools/r266-depth/mock.py's), so Simon can judge it moving before any reader changes. Not the reader.

  now      r277's law: each layer's columns lowered by bend * dx^2, bend = front / speed / 2R, so the far layers drop
           more; the art scrolls straight through a curve fixed in the view. The towers (a game's hook) are lowered at
           their own x (r330) and stay upright; the snow (a VIEW effect) is lowered at its box's middle: not at all.
  turning  one warp for the whole page: the flat page is wrapped round one centre under the view (x along the ground's
           arc, y along the radius). Every layer keeps its own speed, so it turns at its own angular speed; upright
           things lean out from the centre and travel along arcs; nothing pokes, since all layers bend alike.

  python3 tools/r347-planet-turn/mock.py OUT [SECONDS] [RADIUS_PX ...]
      writes OUT/now.gif, OUT/turning-<R>.gif, OUT/side.gif (now above turning at the first radius) and OUT/side.png.
"""
import importlib.util
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

# r266's mock, for its Hiver regions (loaded by path: its name, mock, is also a deprecated package's).
_spec = importlib.util.spec_from_file_location(
    'r266_mock', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'r266-depth', 'mock.py'))
r266 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(r266)

W, H, FPS = 640, 360, 20
# The flat page is drawn on a canvas W wide on each side of the view and H above it, for the warp to read from.
CW, CH, LEFT, TOP = 3 * W, 2 * H, W, H
SKY_TOP, SKY_BOTTOM, SNOW = np.array([0.66, 0.66, 0.78]), np.array([0.86, 0.84, 0.9]), np.array([0.92, 0.93, 0.96])
SCROLL = 40.0  # the front layer's pixels per second / FRONT
FRONT = 0.041
# name, speed (the lab page's ratios), height and bottom in view heights (bottom from the view's bottom)
LAYERS = [('band', 0.010, 0.80, 0.30), ('peaks', 0.0125, 0.36, 0.32), ('mountain', 0.0156, 0.55, 0.14),
          ('towers', 0.017, 0, 0.22), ('trees', 0.041, 0.62, -0.04)]
TOWERS = [(0.05, 0.08, 0.22), (0.28, 0.05, 0.3), (0.47, 0.09, 0.26), (0.72, 0.05, 0.16), (0.86, 0.07, 0.24)]
TOWER_TILE = 0.9  # view widths


def scaled(tex, height):
    th, tw = tex.shape[:2]
    h = max(1, round(height))
    w = max(1, round(tw * h / th))
    return np.asarray(Image.fromarray((tex * 255).astype(np.uint8)).resize((w, h), Image.LANCZOS), np.float64) / 255


def page(tex, t):
    """The flat page at t seconds on the canvas, back to front: the sky, a list of (rgba, speed, middles), middles the
    canvas x each column is lowered at (its tower's middle, as r330's hook does; None: its own x)."""
    out = []
    ys = np.arange(CH)[:, None]
    sky = SKY_TOP + (SKY_BOTTOM - SKY_TOP) * np.clip((ys - TOP * 0.6) / (TOP + H * 0.6 - TOP * 0.6), 0, 1)[..., None]
    sky = np.broadcast_to(sky, (CH, CW, 3))
    for name, speed, height, bottom in LAYERS:
        layer = np.zeros((CH, CW, 4))
        middles = None
        shift = -SCROLL * t * speed / FRONT * 3
        y1 = TOP + round(H * (1 - bottom))
        if name == 'towers':
            tile = TOWER_TILE * W
            middles = np.arange(CW, dtype=np.float64)
            for k in range(-4, 6):
                for left, wide, tall in TOWERS:
                    x0 = round(LEFT + shift % tile + (k + left) * tile)
                    x1 = x0 + int(wide * tile)
                    y0 = y1 - int(tall * H)
                    x0c, x1c = max(x0, 0), min(x1, CW)
                    if x1c > x0c:
                        u = (np.arange(x0c, x1c) - x0) / max(1, x1 - x0)
                        tone = 0.12 + 0.25 * np.sin(u * math.pi) ** 4
                        layer[y0:y1, x0c:x1c, :3] = np.array([0.9, 0.85, 1.0]) * tone[None, :, None]
                        layer[y0:y1, x0c:x1c, 3] = 1
                        middles[x0c:x1c] = (x0 + x1) / 2
        else:
            img = scaled(tex[name], height * H)
            ih, iw = img.shape[:2]
            cols = (np.arange(CW) - LEFT - shift).astype(int) % iw
            y0 = y1 - ih
            a, b = max(y0, 0), min(y1, CH)
            layer[a:b] = img[a - y0:b - y0][:, cols]
        out.append((layer, speed, middles))
    return sky, out


def snow(t, n=140):
    rng = np.random.default_rng(7)
    x0, y0, v = rng.uniform(0, W, n), rng.uniform(0, H, n), rng.uniform(18, 40, n)
    x = (x0 + 12 * np.sin(t * 0.8 + y0)) % W
    y = (y0 + v * t) % H
    return x, y


def dots(img, xs, ys, offset_y=None):
    for i, (x, y) in enumerate(zip(xs, ys)):
        yy = y + (0 if offset_y is None else offset_y[i])
        a, b = int(yy) - 1, int(x) - 1
        if 0 <= a < img.shape[0] - 2 and 0 <= b < img.shape[1] - 2:
            img[a:a + 3, b:b + 3] = 1.0


def over(dst, rgba):
    a = rgba[..., 3:4]
    return dst * (1 - a) + rgba[..., :3] * a


def frame_now(tex, t, radius):
    sky, layers = page(tex, t)
    out = sky[TOP:TOP + H, LEFT:LEFT + W].copy()
    dx = np.arange(W) + 0.5 - W / 2
    rows = np.arange(H)[:, None]
    for rgba, speed, middles in layers:
        at = dx if middles is None else middles[LEFT:LEFT + W] - LEFT - W / 2
        drop = (FRONT / speed / (2 * radius)) * at * at
        src = np.clip((rows - drop[None, :]).round().astype(int) + TOP, 0, CH - 1)
        cut = rgba[src, np.arange(W)[None, :] + LEFT]
        cut[..., 3] *= (rows - drop[None, :] >= -TOP)
        out = over(out, cut)
    x, y = snow(t)
    dots(out, x, y)
    return out


def frame_turning(tex, t, radius, ground=0.2):
    """The flat page warped round a centre `radius` under the ground line (`ground` view heights over the bottom)."""
    sky, layers = page(tex, t)
    flat = sky.copy()
    for rgba, _, _ in layers:
        flat = over(flat, rgba)
    x, y = snow(t)
    dots(flat, x + LEFT, y + TOP)
    gy = H * (1 - ground)  # the ground line on screen
    cy = gy + radius
    sx, sy = np.meshgrid(np.arange(W) + 0.5 - W / 2, cy - (np.arange(H) + 0.5))
    a = np.arctan2(sx, sy)
    r = np.hypot(sx, sy)
    u = W / 2 + a * radius
    v = gy - (r - radius)
    i = np.clip(np.round(v + TOP).astype(int), 0, CH - 1)
    j = np.clip(np.round(u + LEFT).astype(int), 0, CW - 1)
    return flat[i, j]


def to_image(rgb):
    return Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))


def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 8 + 7 * len(text), 16), fill=(0, 0, 0))
    d.text((4, 3), text, fill=(255, 255, 255))
    return im


def save_gif(frames, path):
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / FPS), loop=0)


def main():
    out = sys.argv[1]
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 8
    radii = [float(r) for r in sys.argv[3:]] or [900.0, 1600.0]
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
    os.makedirs(out, exist_ok=True)
    tex = r266.load(root)
    n = int(seconds * FPS)
    # r277's lab radius: the front layer drops 1.25 of 22.5 units at the side, 20 px here.
    now_r = (W / 2) ** 2 / (2 * 20.0)
    now = [label(to_image(frame_now(tex, k / FPS, now_r)), 'now (r277 + r330)') for k in range(n)]
    save_gif(now, os.path.join(out, 'now.gif'))
    for idx, radius in enumerate(radii):
        turning = [label(to_image(frame_turning(tex, k / FPS, radius)), f'turning, radius {int(radius)} px')
                   for k in range(n)]
        save_gif(turning, os.path.join(out, f'turning-{int(radius)}.gif'))
        if idx == 0:
            side = []
            for a, b in zip(now, turning):
                s = Image.new('RGB', (W, 2 * H + 4), 'black')
                s.paste(a, (0, 0))
                s.paste(b, (0, H + 4))
                side.append(s)
            save_gif(side, os.path.join(out, 'side.gif'))
            side[n // 2].save(os.path.join(out, 'side.png'))


if __name__ == '__main__':
    main()
