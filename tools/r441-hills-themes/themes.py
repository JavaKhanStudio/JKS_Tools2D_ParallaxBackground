"""r441: the inpainted hills (ai_page.HILLS_INPAINT) and their canopy gate on themes other than the forest.

    python3 tools/r441-hills-themes/themes.py OUT [--seed 7] [--tries 10] [--only desert,snow]

For each theme, ai_page.py --dry-run --seed SEED gives the hills depth's three commands (sil.py, gen.py inpaint,
layer.py) exactly as a page run gives them; they are run again on each of the next TRIES seeds (SEED*1000 + k*10 + t,
as paint_depths numbers them) into OUT/<theme>/s<seed>_*. Each cut is read by ai_page.canopy(), and the row says what
paint_depths would keep: the first seed the gate passes, else the most wooded one. A strip already cut is not
painted again: delete it to repaint. OUT/<theme>.png: the cuts over the sky, two a row, the gate's numbers on each.
results.md: what it printed (seed 7, 10 tries).
"""
import argparse
import os
import re
import shlex
import subprocess
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(HERE, "..", "r239-ai-strips"))
import ai_page  # noqa: E402

THEMES = {
    "forest": "misty pine forest in the mountains at dawn",  # r384's: the control
    "desert": "sandy desert canyon at sunset",
    "snow": "snowy mountains in winter at dusk",
    "autumn": "autumn countryside at dusk",
    "postapo": "post-apocalyptic ruined city at dusk",
}


def commands(theme, seed, scratch):
    """The hills depth's sil.py, gen.py and layer.py lines of a dry run, its prefix and first seed."""
    out = subprocess.run(["python3", os.path.join(ROOT, "tools/r239-ai-strips/ai_page.py"), "--theme", theme,
                          scratch, "--dry-run", "--seed", str(seed)], capture_output=True, text=True, check=True).stdout
    lines = out.splitlines()
    sil = next(i for i, x in enumerate(lines) if "/sil.py hills " in x)
    prefix = re.search(r"/sil.py hills (\S+)", lines[sil])[1]
    first = int(re.search(r"--seed (\d+)", lines[sil])[1])
    assert "gen.py inpaint" in lines[sil + 1], lines[sil + 1]
    return lines[sil:sil + 3], prefix, first


def paint(lines, prefix, first, seed, dest):
    p = os.path.join(dest, f"s{seed}")
    if not os.path.exists(p + "_cut.png"):
        for line in lines:
            cmd, log = line.rsplit(" > ", 1)
            argv = [x.replace(prefix, p) for x in shlex.split(cmd)]
            argv = [str(seed) if x == str(first) else x for x in argv]
            with open(log.replace(prefix, p), "w") as f:
                subprocess.run(argv, stdout=f, stderr=subprocess.STDOUT, cwd=ROOT, check=False)  # layer.py refuses with 1
    return p


def sheet(rows, out):
    first = Image.open(rows[0][0] + "_ai.png")
    w, h = first.width // 2, first.height // 2
    img = Image.new("RGB", (2 * w + 10, (len(rows) + 1) // 2 * (h + 22)), "white")
    d = ImageDraw.Draw(img)
    for i, (p, text) in enumerate(rows):
        bg = Image.new("RGBA", first.size, (213, 222, 231, 255))
        if os.path.exists(p + "_cut.png"):
            bg.alpha_composite(Image.open(p + "_cut.png").convert("RGBA"))
        x, y = (i % 2) * (w + 10), (i // 2) * (h + 22)
        d.text((x + 4, y + 4), text, fill="black")
        img.paste(bg.convert("RGB").resize((w, h)), (x, y + 18))
    img.save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--tries", type=int, default=ai_page.HILLS_TRIES)
    ap.add_argument("--only", help="themes, comma separated: " + ",".join(THEMES))
    a = ap.parse_args()
    print("theme\tpassed\tkept\tper seed (spread/sharp, + passes)")
    for name in a.only.split(",") if a.only else THEMES:
        dest = os.path.join(os.path.abspath(a.out), name)
        os.makedirs(dest, exist_ok=True)
        lines, prefix, first = commands(THEMES[name], a.seed, os.path.join(dest, "dry"))
        rows, cells, passed, kept, best = [], [], 0, None, None
        for t in range(a.tries):
            seed = first + t
            p = paint(lines, prefix, first, seed, dest)
            if not os.path.exists(p + "_cut.png"):
                cells.append(f"{seed} refused by layer.py")
                rows.append((p, f"seed {seed}: layer.py refused"))
                continue
            spread, sharp = ai_page.canopy(p + "_cut.png")
            ok = spread >= ai_page.CANOPY_SPREAD and sharp <= ai_page.CANOPY_SHARP
            passed += ok
            if ok and kept is None:
                kept = seed
            score = (spread if sharp <= ai_page.CANOPY_SHARP else -sharp, seed)
            best = max(best, score) if best else score
            cells.append(f"{seed} {spread:.2f}/{sharp:.2f}{' +' if ok else ''}")
            rows.append((p, f"seed {seed}: spread {spread:.2f} sharp {sharp:.2f} {'PASS' if ok else 'refused'}"))
        keep = f"{kept}" if kept else f"{best[1]} (best of none)" if best else "none"
        print(f"{name}\t{passed}/{a.tries}\t{keep}\t" + "  ".join(cells), flush=True)
        sheet(rows, os.path.join(a.out, name + ".png"))


if __name__ == "__main__":
    main()
