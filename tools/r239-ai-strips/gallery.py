#!/usr/bin/env python3
"""The AI pages gallery (r239): one HTML page showing every page tools/ai-page.sh made, how each was made, and a form
that writes the command for another.

    python3 tools/r239-ai-strips/gallery.py OUT ROOT...

Finds every ROOT/**/recipe.json (ai_page.py writes it; a page made before it existed: run ai-page.sh again with
--keep --no-shots and the same arguments), and writes OUT/index.html with its pictures and films copied into OUT/media,
so OUT can be served or zipped alone. A page shows its film (tools/ai-film.sh: OUT/film/a01.mp4, core's frames) when
it has one, else its 12 s still. tools/ai-gallery.sh is the way in: it builds, serves and opens it.
"""
import glob
import html
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ai_page import WORDS  # noqa: E402

PASSES = [
    ("shape", "init", "1. The shape",
     "sil.py draws the layer's outline in flat colours: a sum of sine waves that each fit a whole number of times "
     "across the strip, and trees placed on a circle. Its right edge runs on into its left edge: it loops by "
     "construction."),
    ("painted", "ai", "2. The painting",
     "Stable Diffusion repaints the shape from the prompt (img2img: it keeps the outline and the colours, and adds "
     "the detail). Every convolution of the model is padded with the strip's other end (circular padding), so the "
     "painting loops too: the step where it joins itself is no bigger than the steps inside it."),
    ("cut", "cut", "3. The cut",
     "The flat sky is keyed out to transparency, so the layer can stand in front of the ones behind it. In pixel "
     "style the strip is also reduced to one image pixel per art pixel and 12 colours, drawn sharp (Nearest)."),
]

CSS = """
:root { color-scheme: dark; --bg: #1b1d21; --panel: #25282e; --line: #3a3f48; --text: #e8e9ec; --muted: #a9afba;
  --accent: #7cb3ff; --fail: #ff8a80; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 system-ui, -apple-system, sans-serif; }
main { max-width: 1280px; margin: 0 auto; padding: 16px; }
h1 { font-size: 24px; margin: 4px 0; } h2 { font-size: 20px; margin: 28px 0 8px; } h3 { font-size: 16px; margin: 0; }
p { margin: 6px 0; max-width: 80ch; } .muted { color: var(--muted); }
a { color: var(--accent); }
nav { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
nav a { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 4px 10px;
  text-decoration: none; }
.card h2 { margin-top: 0; }
.card { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 12px; margin: 16px 0; }
.film { width: 100%; aspect-ratio: 16 / 9; background: #000; border-radius: 4px; display: block; }
.facts { display: flex; flex-wrap: wrap; gap: 4px 18px; margin: 8px 0; color: var(--muted); }
.facts b { color: var(--text); font-weight: 600; }
code, pre { font: 13px/1.45 ui-monospace, monospace; }
pre { background: #121417; border: 1px solid var(--line); border-radius: 4px; padding: 8px; margin: 6px 0;
  white-space: pre-wrap; word-break: break-word; }
.cmd { display: flex; gap: 8px; align-items: flex-start; } .cmd pre { flex: 1; }
button { font: inherit; background: #31353d; color: var(--text); border: 1px solid var(--line); border-radius: 4px;
  padding: 4px 10px; cursor: pointer; } button:hover { border-color: var(--accent); }
details { margin: 8px 0; } summary { cursor: pointer; color: var(--accent); }
.layer { border-top: 1px solid var(--line); padding: 10px 0; }
.passes { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 8px; margin: 6px 0; }
.passes figure { margin: 0; } .passes figcaption { color: var(--muted); font-size: 13px; }
.strip { width: 100%; display: block; border-radius: 3px; }
.checker { background: repeating-conic-gradient(#555 0 25%, #777 0 50%) 0 0 / 16px 16px; }
.pixel { image-rendering: pixelated; }
.twice { display: flex; overflow-x: auto; } .twice img { height: 150px; width: auto; flex: none; }
.pair { display: flex; position: relative; } .pair img { width: 50%; }
.pair::after { content: ""; position: absolute; top: 0; bottom: 0; left: 50%;
  border-left: 2px dashed #ff5252; }
.lint { color: var(--fail); }
.swatch { display: inline-block; width: 12px; height: 12px; border-radius: 2px; vertical-align: -1px;
  border: 1px solid var(--line); }
form { display: grid; grid-template-columns: max-content 1fr; gap: 8px 12px; align-items: center; max-width: 900px; }
form input[type=text], form select { font: inherit; background: #121417; color: var(--text);
  border: 1px solid var(--line); border-radius: 4px; padding: 4px 6px; width: 100%; }
form input[type=number] { width: 8em; font: inherit; }
.row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.row select { width: auto; }
@media (max-width: 720px) { .passes { grid-template-columns: 1fr; } form { grid-template-columns: 1fr; } }
"""


def esc(s):
    return html.escape(str(s), quote=True)


def find(roots):
    """Every page directory under the roots (the ones holding a recipe.json), oldest recipe first."""
    out = []
    for root in roots:
        for d, dirs, files in os.walk(root):
            dirs[:] = sorted(x for x in dirs if x not in ("strips", "page", "shots", "film", "frames"))
            if "recipe.json" in files:
                out.append(d)
    return sorted(out, key=lambda d: os.path.getmtime(os.path.join(d, "recipe.json")))  # made first, shown first


def depth_card(r, d, pid, title, view, lint, copy):
    """A page tools/depth-page.sh cut from one painting (r279): the painting, its depth and bands, then each layer
    as the bands cut it (what nearer things hid, see-through), repainted, and as the page draws it."""
    cut = lambda name: copy(os.path.join(d, "cut", name), f"{pid}-{name}")  # noqa: E731
    loop = r.get("loop") or {}
    fixed = loop.get("after") is not None
    figs = [(cut("source.png"), "The painting"), (cut("painting.png") if fixed else None, "Its join repainted"),
            (cut("depth.png"), "Its depth (light: near)"), (cut("bands.png"), f"{len(r['layers'])} depth bands")]
    top = "".join(f'<figure><img class="strip" src="{src}" alt="{esc(label)}" loading="lazy">'
                  f'<figcaption>{esc(label)}</figcaption></figure>' for src, label in figs if src)
    joint = (f"Its ends did not meet (seam ratio {loop['before']}): a 96 px band round the join was repainted "
             f"(ratio {loop['after']} after).") if fixed else (
        f"Its ends already meet (seam ratio {loop.get('before', '?')}): nothing repainted at the join.")
    rows = []
    for k, ly in enumerate(r["layers"]):
        sid = ly["strip"]
        passes = [(cut(f"{sid}_raw.png"), "The whole painting: the back layer is opaque" if k == 0 else
                   "Cut by its band: what nearer things hid is see-through"),
                  (cut(f"{sid}_mask.png") if ly["prompt"] else None, "What gets repainted (white)"),
                  (cut(f"{sid}_painted.png") if ly["prompt"] else None, "The holes repainted"),
                  (cut(f"{sid}.png"), "The layer")]
        figs = "".join(f'<figure><img class="strip checker" src="{src}" alt="{esc(label)}" loading="lazy">'
                       f'<figcaption>{esc(label)}</figcaption></figure>' for src, label in passes if src)
        final = f"media/{pid}-{sid}.png"
        prompt = (f'<details><summary>repaint prompt</summary><pre>{esc(ly["prompt"])}</pre>'
                  f'<p class="muted">negative</p><pre>{esc(r["negative"])}</pre><p class="muted">inpaint denoise '
                  f'{r["denoise"]}, seed {ly["seed"]}</p></details>') if ly["prompt"] else ""
        rows.append(
            f'<div class="layer"><h3>Layer {k}{" (back)" if k == 0 else " (front)" if k == len(r["layers"]) - 1 else ""}'
            f'</h3><div class="facts"><span>depth <b>{ly["disparity"]}</b></span><span>speed <b>{ly["speed"]}</b>'
            f'</span><span>repainted <b>{round(100 * ly["repainted"])}%</b></span></div>'
            f'<div class="passes">{figs}</div>'
            f'<p class="muted">Drawn twice side by side, the join dashed.</p>'
            f'<div class="pair"><img class="checker" src="{final}" alt=""><img class="checker" src="{final}" alt="">'
            f'</div>{prompt}</div>')
    return (f'<section class="card" id="{esc(pid)}"><h2>{esc(title)}</h2>'
            f'<p>“{esc(r["theme"])}”</p>{view}'
            f'<div class="facts"><span>model <b>{esc(r["model"])}</b></span><span>painting '
            f'<b>{r["size"][0]}x{r["size"][1]}</b></span><span>layers <b>{len(r["layers"])}</b></span></div>{lint}'
            f'<div class="cmd"><pre>{esc(r["command"])}</pre><button data-copy>Copy</button></div>'
            f'<p class="muted">Files: {esc(os.path.relpath(d))}/page (the atlas and the .jplax a game loads).</p>'
            f'<details><summary>How it was made: the painting, then layer by layer (back to front)</summary>'
            f'<div class="passes">{top}</div><p>{esc(joint)}</p>{"".join(rows)}</details></section>')


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out, roots = os.path.abspath(sys.argv[1]), sys.argv[2:]
    media = os.path.join(out, "media")
    shutil.rmtree(media, ignore_errors=True)
    os.makedirs(media)
    pages = find(roots)

    def copy(src, name):
        """src copied into media as name; its URL, or None when there is no such file."""
        if not os.path.exists(src):
            return None
        shutil.copyfile(src, os.path.join(media, name))
        return "media/" + name

    cards, nav = [], []
    for n, d in enumerate(pages):
        r = json.load(open(os.path.join(d, "recipe.json")))
        depth = r.get("tool") == "depth-page"
        style = "from a painting" if depth else r["style"]
        pid = f"p{n}-{r['name']}-{'depth' if depth else r['style']}"
        pixel = style == "pixel"
        title = f"{r['name']}, {style}"
        nav.append(f'<a href="#{esc(pid)}">{esc(title)}</a>')
        first_of = lambda pattern: (sorted(glob.glob(os.path.join(d, pattern))) or [""])[0]  # noqa: E731
        poster = copy(first_of("shots/*-t12.png"), f"{pid}-t12.png")
        film = copy(first_of("film/*.mp4"), f"{pid}.mp4")
        if film:
            view = (f'<video class="film" src="{film}" poster="{poster or ""}" controls loop muted playsinline '
                    f'preload="metadata"></video><p class="muted">core\'s own frames, off screen: 8 s of scrolling at '
                    '30 fps (tools/ai-film.sh).</p>')
        elif poster:
            view = (f'<img class="film" src="{poster}" alt="{esc(title)} at 12 s"><p class="muted">core\'s frame at '
                    '12 s; no film yet (tools/ai-film.sh DIR/page DIR/film).</p>')
        else:
            view = '<p class="muted">No render yet: run the tool again without --no-shots.</p>'
        if r["lint"]:
            lint = (f'<details><summary class="lint">lint: {len(r["lint"])} fault(s)</summary><ul>'
                    + "".join(f'<li class="lint">{esc(p)}</li>' for p in r["lint"]) + "</ul></details>")
        else:
            lint = '<p class="muted">lint (tools/parallax_lab.py): ok</p>'
        if depth:
            cards.append(depth_card(r, d, pid, title, view, lint, copy))
            continue
        rows = []
        for k, layer in enumerate(r["layers"]):
            first = layer["strips"][0]
            figs = []
            for key, suffix, label, _ in PASSES:
                src = copy(os.path.join(d, "strips", f"{first['strip']}_{suffix}.png"), f"{pid}-l{k}-{suffix}.png")
                cls = "strip" + (" checker" if key == "cut" else "") + (" pixel" if key == "cut" and pixel else "")
                figs.append(f'<figure><img class="{cls}" src="{src}" alt="{esc(label)}" loading="lazy">'
                            f'<figcaption>{esc(label[3:])}</figcaption></figure>')
            cut = f"media/{pid}-l{k}-cut.png"
            seq = ""
            if layer["sequence"]:
                vs = []
                for s in layer["strips"]:
                    vs.append(copy(os.path.join(d, "strips", f"{s['strip']}_cut.png"), f"{pid}-{s['strip']}-cut.png"))
                imgs = "".join(f'<img class="checker" src="{v}" alt="segment {i}">' for i, v in enumerate(vs))
                seq = (f'<p><b>A SEQUENCE of {len(vs)} strips.</b> The first one, then {len(vs) - 1} edge-locked '
                       'variants: new trees in the middle, the first strip\'s own columns at both ends, so any of them '
                       'can follow any other. The library picks the order from the page\'s seed: the front never '
                       'shows the same picture twice in a row. All of them, end to end:</p>'
                       f'<div class="twice">{imgs}</div>')
            seeds = ", ".join(str(s.get("seed")) for s in layer["strips"])
            rows.append(
                f'<div class="layer"><h3>Layer {k}: {esc(layer["kind"])}'
                f'{" (back)" if k == 0 else " (front)" if k == len(r["layers"]) - 1 else ""}</h3>'
                f'<div class="facts"><span>speed <b>{layer["speed"]}</b></span><span>raised <b>{layer["dy"]}%</b>'
                f'</span><span>shape colour <span class="swatch" style="background:#{esc(layer["colour"])}"></span> '
                f'<b>#{esc(layer["colour"])}</b></span><span>seed <b>{esc(seeds)}</b></span></div>'
                f'<div class="passes">{"".join(figs)}</div>'
                f'<p class="muted">Drawn twice side by side, the join dashed: no step where it repeats.</p>'
                f'<div class="pair"><img class="checker{" pixel" if pixel else ""}" src="{cut}" alt="">'
                f'<img class="checker{" pixel" if pixel else ""}" src="{cut}" alt=""></div>{seq}'
                f'<details><summary>prompt</summary><pre>{esc(first.get("prompt", "?"))}</pre>'
                f'<p class="muted">negative</p><pre>{esc(first.get("negative", "?"))}</pre>'
                f'<p class="muted">img2img denoise {first.get("denoise", "?")}: how much of the shape it may '
                'repaint (0 none, 1 all).</p></details></div>')
        cards.append(
            f'<section class="card" id="{esc(pid)}"><h2>{esc(title)}</h2>'
            f'<p>“{esc(r["theme"])}”</p>{view}'
            f'<div class="facts"><span>model <b>{esc(r["model"])}</b></span><span>strip '
            f'<b>{r["size"][0]}x{r["size"][1]}</b></span><span>layers <b>{len(r["layers"])}</b></span>'
            f'<span>page fog <b>{r["fogStrength"]}</b></span><span>sky <span class="swatch" '
            f'style="background:#{esc(r["sky"][0])}"></span> <span class="swatch" '
            f'style="background:#{esc(r["sky"][1])}"></span></span></div>{lint}'
            f'<div class="cmd"><pre>{esc(r["command"])}</pre><button data-copy>Copy</button></div>'
            f'<p class="muted">Files: {esc(os.path.relpath(d))}/page (the atlas and the .jplax a game loads).</p>'
            f'<details><summary>How it was made, layer by layer (back to front)</summary>{"".join(rows)}</details>'
            '</section>')

    kinds = sorted(WORDS)
    opts = lambda sel: "".join(f'<option{" selected" if k == sel else ""}>{k}</option>' for k in kinds)  # noqa: E731
    start = ["mountains", "mountains", "hills", "pines"]
    cols = ["7d8aa3", "647c80", "4f6f5c", "1d3328"]
    depth = "".join(f'<span class="row" data-depth="{i}"><select>{opts(k)}</select><input type="color" '
                    f'value="#{c}"></span>' for i, (k, c) in enumerate(zip(start, cols)))
    empty = ('<p class="muted">No page found. Make the examples (about 20 min of GPU): '
             '<code>tools/r239-ai-strips/examples.sh build/ai/examples</code>, then '
             '<code>tools/ai-gallery.sh</code> again.</p>') if not pages else ""
    doc = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI parallax pages</title><style>{CSS}</style></head>
<body><main>
<h1>AI parallax pages</h1>
<p>Parallax backgrounds painted by Stable Diffusion on this machine, cut into layers that loop, and drawn by the
library. Nothing is touched up by hand: every picture below is a file the tool wrote, and every film is the
library's own frames.</p>
<nav>{"".join(nav)}<a href="#make">Make another</a></nav>
{empty}
<h2>How a page is made</h2>
<p>The hard part is the loop: a layer repeats sideways forever, so its right edge must run on into its left edge,
to the pixel. Each layer goes through three passes, back to front:</p>
<ol>{"".join(f"<li><b>{esc(label[3:])}.</b> {esc(text)}</li>" for _, _, label, text in PASSES)}
<li><b>The page.</b> The cut layers are packed into one atlas and written as a page (.jplax): each layer scrolls half
as fast as the one in front of it, sits low enough that the one in front hides its bottom edge, and the page's fog
fades the far ones toward the sky.</li></ol>
<p>In <b>painted</b> style (SD 1.5, a strip of 1024x384) the front layer is a SEQUENCE: three more strips that start
and end on the first one's columns, so the front changes as it scrolls. <b>Pixel</b> style (SDXL with the
PixelArt_XL add-on, 1536x576 cut to one pixel per art pixel) has one strip a layer.</p>
<p><b>From a painting</b> (tools/depth-page.sh, r279) goes the other way: one flat painting, its depth estimated
by Depth Anything V2 Small, cut into depth bands, far to near; each layer's holes (where a nearer thing hid it) are
repainted by SD 1.5, and each layer scrolls at a speed set by its depth.</p>
<p class="muted">Open “How it was made” under a page to see each layer's three passes, its prompt, its seed and the
layer drawn twice to show the join. The method and its traps: docs/ai-parallax.md.</p>
{"".join(cards)}
<section class="card" id="make"><h2>Make another</h2>
<p>Pick a theme and a shape for each depth; this writes the command. Run it in the checkout, then
<code>tools/ai-gallery.sh</code> again to see the page here.</p>
<form id="maker">
<label for="m-theme">Theme</label><input type="text" id="m-theme" value="snowy pine valley at dawn">
<label for="m-name">Name</label><input type="text" id="m-name" value="snow">
<label for="m-style">Style</label><select id="m-style"><option value="painted">painted: SD 1.5, ~2 min a page
</option><option value="pixel">pixel: SDXL + PixelArt_XL, ~3-30 min a page</option></select>
<label>Depths, back to front<br><span class="muted">shape, colour</span></label>
<div><div class="row" id="m-depths">{depth}</div>
<div class="row"><button type="button" id="m-add">+ depth</button><button type="button" id="m-del">- depth</button>
</div></div>
<label for="m-sky">Sky</label><div class="row"><input type="checkbox" id="m-sky"> set it (else the painted
sky)<input type="color" id="m-sky-top" value="#6d8fc4"> top <input type="color" id="m-sky-low" value="#dfe6ee">
horizon</div>
<label for="m-seed">Seed</label><div class="row"><input type="number" id="m-seed" value="1" min="1">
<span class="muted">another seed, another painting of the same shapes</span></div>
</form>
<div class="cmd"><pre id="m-cmd"></pre><button data-copy>Copy</button></div>
<p class="muted">Each colour is the shape's flat colour before painting: img2img keeps much of it, so it is the
theme's say in the palette. Leave out of the theme what another layer draws (a “turquoise sea” in the theme leaves a
haze of sea in every layer's sky). Shapes: {esc(", ".join(f"{k} ({w})" for k, w in sorted(WORDS.items())))}.
Needs ComfyUI in ~/ComfyUI (COMFYUI_DIR) with its models, and the GPU free enough: an ollama model loaded beside it
makes SDXL ten times slower. tools/ai-page.sh --help says the rest. To film it:
<code>tools/ai-film.sh OUT/page OUT/film 8 30</code>.</p>
</section>
</main>
<script>
const $ = id => document.getElementById(id);
const q = s => /^[A-Za-z0-9_.,\\/-]+$/.test(s) ? s : "'" + s.replace(/'/g, "'\\\\''") + "'";
function command() {{
  const rows = [...document.querySelectorAll('#m-depths [data-depth]')];
  const kinds = rows.map(r => r.querySelector('select').value);
  const cols = rows.map(r => r.querySelector('input').value.slice(1));
  const name = $('m-name').value.trim().replace(/[^A-Za-z0-9_-]+/g, '-') || 'page';
  const style = $('m-style').value;
  let c = ['tools/ai-page.sh', '--theme', q($('m-theme').value.trim()), '--name', name, '--style', style,
    '--kinds', kinds.join(','), '--colours', cols.join(',')];
  if ($('m-sky').checked) c.push('--sky', $('m-sky-top').value.slice(1) + ',' + $('m-sky-low').value.slice(1));
  c.push('--seed', String(Math.max(1, parseInt($('m-seed').value) || 1)), 'build/ai/' + name + '-' + style);
  $('m-cmd').textContent = c.join(' ');
}}
$('m-add').onclick = () => {{
  const rows = $('m-depths'); if (rows.children.length >= 6) return;
  const n = rows.lastElementChild.cloneNode(true); rows.insertBefore(n, rows.lastElementChild); command();
}};
$('m-del').onclick = () => {{
  const rows = $('m-depths'); if (rows.children.length <= 2) return;
  rows.removeChild(rows.children[rows.children.length - 2]); command();
}};
$('maker').addEventListener('input', command); $('maker').addEventListener('change', command);
document.querySelectorAll('[data-copy]').forEach(b => b.onclick = () => {{
  const t = b.previousElementSibling.textContent;
  (navigator.clipboard ? navigator.clipboard.writeText(t) : Promise.reject()).then(
    () => {{ b.textContent = 'Copied'; }}, () => {{ b.textContent = 'Select it'; }});
  setTimeout(() => {{ b.textContent = 'Copy'; }}, 1500);
}});
command();
</script>
</body></html>
"""
    with open(os.path.join(out, "index.html"), "w") as f:
        f.write(doc)
    print(f"{out}/index.html: {len(pages)} page(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
