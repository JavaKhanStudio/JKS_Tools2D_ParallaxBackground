"""r514: what r513's disk.sh moved, looked at. Each strip it lists goes through creature.py at REV and at the tree; each
blob either names is cropped from the painting, so a refusal reads as a picture and not as a number.

    python3 tools/r514-read-jobs/moved.py R513_LOG OUT [REV]

Writes OUT/moved.txt (every strip, what each side named), OUT/refused.png (the 0 -> 4 strips, whole, the blobs boxed) and
OUT/cleared-N.png (each 4 -> 0 strip's blobs at 2x: painting, then the alpha REV read in magenta over it).
"""
import os
import re
import subprocess
import sys
import tarfile
import tempfile
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "../.."))
BLOB = re.compile(r"(\d+) px blob at x=(\d+) y=(\d+)")
ROW, PER_SHEET = 200, 16


def named(tools, cut):
    """The blobs creature.py in tools names on this cut, (size, x, y)."""
    env = dict(os.environ, OMP_NUM_THREADS="1")
    run = subprocess.run([sys.executable, f"{tools}/creature.py", cut, "--src", cut[:-len("_cut.png")] + "_ai.png"],
                         check=False, capture_output=True, text=True, env=env)
    return [tuple(map(int, m)) for m in BLOB.findall(run.stderr)]


def crop(im, x, y, half=70, scale=2):
    """A square round x, y, wrapping along X as the strip does, its y clipped, the centre ticked."""
    w, h = im.size
    top = min(max(0, y - half), h - 2 * half)
    box = Image.new("RGB", (2 * half, 2 * half))
    for i in range(2 * half):
        box.paste(im.crop(((x - half + i) % w, top, (x - half + i) % w + 1, top + 2 * half)), (i, 0))
    box = box.resize((2 * half * scale, 2 * half * scale), Image.NEAREST)
    d = ImageDraw.Draw(box)
    cx, cy = half * scale, (y - top) * scale
    d.line([cx - 10, cy, cx - 4, cy], fill=(255, 0, 0), width=2)
    d.line([cx + 4, cy, cx + 10, cy], fill=(255, 0, 0), width=2)
    return box


def alpha_view(rgb, cut):
    """The painting dimmed where the side's alpha reads it out of the mass, as creature's --show does."""
    a = np.asarray(Image.open(cut).convert("RGBA").getchannel("A").resize(rgb.size, Image.NEAREST))
    return Image.fromarray(np.where(a[..., None] >= 128, np.asarray(rgb), np.asarray(rgb) // 3))


def main():
    log, out = sys.argv[1:3]
    rev = sys.argv[3] if len(sys.argv) > 3 else "431a6df^"
    os.makedirs(out, exist_ok=True)
    with open(log) as f:
        moved = [m.groups() for m in re.finditer(r"^(\d) -> (\d)\s+(\S+)$", f.read(), re.MULTILINE)]
    with tempfile.TemporaryDirectory() as t:
        tar = subprocess.run(["git", "archive", rev, "tools/r239-ai-strips", "tools/parallax_rules.py"], cwd=ROOT,
                             capture_output=True, check=True).stdout
        with tarfile.open(fileobj=BytesIO(tar)) as a:
            a.extractall(t, filter="data")

        def both(item):
            _, _, cut = item
            return named(f"{t}/tools/r239-ai-strips", cut), named(f"{ROOT}/tools/r239-ai-strips", cut)

        with ThreadPoolExecutor(int(os.environ.get("JOBS", "4"))) as ex:
            answers = list(ex.map(both, moved))
    rows = []
    with open(f"{out}/moved.txt", "w") as f:
        for (a, b, cut), (before, after) in zip(moved, answers, strict=True):
            short = cut.split("/build/", 1)[1]
            f.write(f"{a} -> {b}  {short}\n  before {before}\n  after  {after}\n")
            rows.append((a, b, cut, short, before, after))
    refused = [r for r in rows if r[1] != "0"]
    cleared = [r for r in rows if r[1] == "0"]
    strips = []
    for _, _, cut, short, _, after in refused:
        im = Image.open(cut[:-len("_cut.png")] + "_ai.png").convert("RGB")
        im = alpha_view(im, cut)
        d = ImageDraw.Draw(im)
        for size, x, y in after:
            e = max(10, int(size ** 0.5))
            d.rectangle([x - e, y - e, x + e, y + e], outline=(255, 0, 0), width=2)
        d.text((6, 6), short, fill=(255, 255, 0))
        strips.append(im)
        for size, x, y in after:
            print(short, size, x, y)
    if strips:
        sheet = Image.new("RGB", (max(s.width for s in strips), sum(s.height for s in strips)))
        y = 0
        for s in strips:
            sheet.paste(s, (0, y))
            y += s.height
        sheet.save(f"{out}/refused.png")
    for n in range(0, len(cleared), PER_SHEET):
        part = cleared[n:n + PER_SHEET]
        wide = max(len(r[4]) for r in part)
        sheet = Image.new("RGB", (wide * 580, len(part) * (280 + 18)), (30, 30, 30))
        d = ImageDraw.Draw(sheet)
        for i, (_, _, cut, short, before, _) in enumerate(part):
            y0 = i * (280 + 18)
            d.text((4, y0 + 2), short + "   " + " ".join(f"{s}px@{x},{y}" for s, x, y in before), fill=(255, 255, 0))
            rgb = Image.open(cut[:-len("_cut.png")] + "_ai.png").convert("RGB")
            seen = alpha_view(rgb, cut)
            for j, (_, x, y) in enumerate(before):
                sheet.paste(crop(rgb, x, y), (j * 580, y0 + 18))
                sheet.paste(crop(seen, x, y), (j * 580 + 290, y0 + 18))
        sheet.save(f"{out}/cleared-{n // PER_SHEET}.png")


if __name__ == "__main__":
    main()
