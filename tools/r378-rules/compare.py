#!/usr/bin/env python3
"""depth-page examples before and after r378's rules: what each layer's rules decided, how many of its pixels
moved, and the back layer side by side where they moved.

    python3 tools/r378-rules/compare.py build/r378/before build/r378/after OUT

For each example (valley, river, noloop): prints per layer the rules weighed in after/recipe.json and the share of
pixels whose colour moved by more than 24 (a repaint redraws the whole hole, so every layer moves a little where
its hole is: compare the layers the rule touched with those it did not). Writes OUT/<example>-l0.png: the back
layer before | after over magenta, cropped round what changed most, and OUT/<example>-stills.png: before over
after at 0, 6 and 12 s.
"""
import argparse
import json
import os

import numpy as np
from PIL import Image


def over_magenta(path):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, (255, 0, 255, 255))
    return Image.alpha_composite(bg, im).convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for ex in ("valley", "river", "noloop"):
        b, f = os.path.join(a.before, ex), os.path.join(a.after, ex)
        if not os.path.exists(os.path.join(f, "recipe.json")):
            continue
        with open(os.path.join(f, "recipe.json")) as fh:
            recipe = json.load(fh)
        for k, entry in enumerate(recipe["layers"]):
            p0, p1 = os.path.join(b, "cut", f"l{k}.png"), os.path.join(f, "cut", f"l{k}.png")
            x0, x1 = (np.asarray(Image.open(p).convert("RGBA")).astype(int) for p in (p0, p1))
            moved = float((np.abs(x0 - x1).max(axis=2) > 24).mean()) if x0.shape == x1.shape else float("nan")
            said = ", ".join(f"{r['rule']} {'applied, ' + str(r['moved']) + ' px' if r['applied'] else 'skipped'}"
                             + ("" if r["applied"] else f" ({list(r['when'])[-1]})" if r["when"] else "")
                             for r in entry.get("rules", []))
            print(f"{ex} l{k}: {moved:.1%} of its pixels moved; {said}")
        l0b, l0a = over_magenta(os.path.join(b, "cut", "l0.png")), over_magenta(os.path.join(f, "cut", "l0.png"))
        w, h = l0b.size
        d = np.abs(np.asarray(l0b).astype(int) - np.asarray(l0a).astype(int)).max(axis=2)
        cy, cx = np.unravel_index(np.argmax(np.convolve(d.sum(axis=1), np.ones(64), "same")[:, None]
                                            * np.convolve(d.sum(axis=0), np.ones(64), "same")[None]), d.shape)
        box = (max(0, min(w - 320, cx - 160)), max(0, min(h - 200, cy - 100)))
        box = box + (box[0] + 320, box[1] + 200)
        side = Image.new("RGB", (1290, 400), "white")
        side.paste(l0b.crop(box).resize((640, 400)), (0, 0))
        side.paste(l0a.crop(box).resize((640, 400)), (650, 0))
        side.save(os.path.join(a.out, f"{ex}-l0.png"))
        stills = [Image.open(os.path.join(r, "shots", f"d01-t{t}.png")).convert("RGB") for r in (b, f) for t in (0, 6, 12)]
        sw, sh = stills[0].width // 2, stills[0].height // 2
        sheet = Image.new("RGB", (3 * sw, 2 * sh))
        for i, s in enumerate(stills):
            sheet.paste(s.resize((sw, sh)), ((i % 3) * sw, (i // 3) * sh))
        sheet.save(os.path.join(a.out, f"{ex}-stills.png"))


if __name__ == "__main__":
    main()
