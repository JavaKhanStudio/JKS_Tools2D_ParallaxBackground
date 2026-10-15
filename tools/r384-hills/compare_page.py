#!/usr/bin/env python3
"""r384: one small HTML page to compare the forest's hills before and after hills.patch, for Simon.

    python3 tools/r384-hills/compare_page.py BEFORE AFTER OUT [--films FILM_BEFORE FILM_AFTER]

BEFORE and AFTER are ai-page.sh page folders painted on the same --seed (recipe.json, strips/, shots/). Writes
OUT/r384-compare.html and its media beside it, every file named r384-cmp-*, so they can be attached to the card
together and the page's relative links still work. Three views, each one box showing BEFORE or AFTER:
the page scrolling (FILM_* are tools/ai-film.sh OUTs: the real reader's frames), the page at 6 s (a film's frame 180 when FILM_* are given, else shots/a01-t6.png), and the hills layer
alone over the layers behind it, tiled twice. Each box has a wipe (drag: before left, after right), Before/After
buttons, and a flicker that swaps them every second.
"""
import argparse
import os
import shutil

from PIL import Image

HTML = """<!doctype html>
<html><head><meta charset="utf-8"><title>r384 hills: before / after</title>
<style>
body {{ background: #1b1d21; color: #e8e8e8; font: 17px/1.45 sans-serif; margin: 24px auto; max-width: 1300px; }}
h1 {{ font-size: 24px; }} h2 {{ font-size: 20px; margin-top: 36px; }}
.box {{ position: relative; width: 100%; user-select: none; cursor: ew-resize; background: #000; }}
.box > img, .box > video {{ display: block; width: 100%; }}
.box .after {{ position: absolute; left: 0; top: 0; height: 100%; clip-path: inset(0 0 0 var(--cut)); }}
.line {{ position: absolute; top: 0; bottom: 0; left: var(--cut); width: 2px; background: #ff0; pointer-events: none; }}
.tag {{ position: absolute; top: 8px; padding: 4px 10px; background: #000a; font-weight: bold; pointer-events: none; }}
.tag.b {{ left: 8px; }} .tag.a {{ right: 8px; }}
.bar {{ margin: 8px 0; }} .bar button {{ font-size: 16px; padding: 6px 16px; margin-right: 6px; }}
.bar button.on {{ background: #ff0; }}
.note {{ color: #b8b8b8; }}
</style></head><body>
<h1>Forest page, hills layer (l2): BEFORE and AFTER hills.patch</h1>
<p><b>BEFORE</b> = today's ai-page.sh (the hills come out one flat green).
<b>AFTER</b> = hills.patch: the hills prompted as rolling wooded hills with folds and groves, conifers in the
negative, painted at denoise 0.70. Same seed (7), same model (dreamshaper_8). The AFTER page is the BEFORE page
with only its hills strip replaced: the far mountains and the pines are the same pixels on both sides.</p>
<p class="note">In each box: drag across it (BEFORE on the left of the yellow line, AFTER on the right), or press
Before / After to see one whole, or Flicker to swap them every second.
What to look for: folds and small round trees on the hills, and the pale misty ranges SD paints behind them
(kept by the key, with clipped tops: the cost).</p>
{sections}
<script>
document.querySelectorAll('.view').forEach(v => {{
  const box = v.querySelector('.box'), btn = v.querySelectorAll('button');
  let timer = null;
  const set = p => box.style.setProperty('--cut', p + '%');
  const mark = i => btn.forEach((b, k) => b.classList.toggle('on', k === i));
  const stop = () => {{ clearInterval(timer); timer = null; }};
  set(50);
  box.addEventListener('pointermove', e => {{
    if (e.buttons !== 1 && e.pointerType === 'mouse') return;
    stop(); mark(-1);
    const r = box.getBoundingClientRect();
    set(Math.max(0, Math.min(100, 100 * (e.clientX - r.left) / r.width)));
  }});
  box.addEventListener('pointerdown', e => box.dispatchEvent(new PointerEvent('pointermove', e)));
  btn[0].onclick = () => {{ stop(); set(100); mark(0); }};
  btn[1].onclick = () => {{ stop(); set(0); mark(1); }};
  btn[2].onclick = () => {{
    if (timer) {{ stop(); mark(-1); return; }}
    let a = false; mark(2);
    timer = setInterval(() => {{ a = !a; set(a ? 0 : 100); }}, 1000); set(100);
  }};
  const vids = v.querySelectorAll('video');
  if (vids.length === 2) setInterval(() => {{
    if (Math.abs(vids[1].currentTime - vids[0].currentTime) > 0.05) vids[1].currentTime = vids[0].currentTime;
  }}, 500);
}});
</script></body></html>
"""

VIEW = """<div class="view"><h2>{title}</h2><p class="note">{about}</p>
<div class="bar"><button>Before</button><button>After</button><button>Flicker</button></div>
<div class="box">{before}{after}<div class="line"></div>
<span class="tag b">BEFORE</span><span class="tag a">AFTER</span></div></div>
"""


def behind_tiled(page, strip="l2"):
    """The strip over its page's earlier strips and its sky's top colour, tiled twice across."""
    depth = int(strip[1])
    sky = Image.open(os.path.join(page, "strips", strip + "_ai.png")).convert("RGB").getpixel((0, 0))
    im = Image.open(os.path.join(page, "strips", strip + "_cut.png")).convert("RGBA")
    bg = Image.new("RGBA", im.size, sky + (255,))
    for k in range(depth):
        bg.alpha_composite(Image.open(os.path.join(page, "strips", f"l{k}_cut.png")).convert("RGBA"))
    bg.alpha_composite(im)
    out = Image.new("RGB", (im.width * 2, im.height))
    out.paste(bg.convert("RGB"), (0, 0))
    out.paste(bg.convert("RGB"), (im.width, 0))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    ap.add_argument("--films", nargs=2, metavar=("FILM_BEFORE", "FILM_AFTER"))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    sections = []

    def pair(kind, make):
        names = []
        for side, page in (("before", a.before), ("after", a.after)):
            name = f"r384-cmp-{kind}-{side}.png"
            make(page).save(os.path.join(a.out, name))
            names.append(name)
        return [f'<img class="{s}" src="{n}">' for s, n in zip(("before", "after"), names)]

    if a.films:
        tags = []
        for side, film in zip(("before", "after"), a.films):
            name = f"r384-cmp-film-{side}.mp4"
            shutil.copy(os.path.join(film, "a01.mp4"), os.path.join(a.out, name))
            tags.append(f'<video class="{side}" src="{name}" autoplay loop muted playsinline></video>')
        sections.append(VIEW.format(title="1. The page scrolling (10 s, loops)", before=tags[0], after=tags[1],
                                    about="The real reader's frames, the two films kept in step."))
    if a.films:  # frame 180 of a 30 fps film: the same 6 s on both sides, drawn from the pages filmed
        stills = dict(zip((a.before, a.after), (os.path.join(f, "frames", "a01-f0180.png") for f in a.films)))
    else:
        stills = {p: os.path.join(p, "shots", "a01-t6.png") for p in (a.before, a.after)}
    b, f = pair("still", lambda p: Image.open(stills[p]).convert("RGB"))
    sections.append(VIEW.format(title="2. The page at 6 s", before=b, after=f, about="One still of each page."))
    b, f = pair("hills", behind_tiled)
    sections.append(VIEW.format(title="3. The hills layer alone, over the two mountain layers behind it, tiled twice",
                                before=b, after=f,
                                about="No pines in front: the hills strip as the key cut it. The join in the middle "
                                      "is the strip's loop."))
    path = os.path.join(a.out, "r384-compare.html")
    with open(path, "w") as fh:
        fh.write(HTML.format(sections="".join(sections)))
    print(path)


if __name__ == "__main__":
    main()
