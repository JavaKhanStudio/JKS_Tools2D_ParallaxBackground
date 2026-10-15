#!/usr/bin/env python3
"""One small HTML page comparing versions of the same picture or film, for Simon to judge an answer by (r384, r401).

    python3 tools/compare_page.py SPEC.json OUT_DIR

SPEC: {"name": "r401", "title": ..., "intro": ... (HTML), "sections": [{"title": ..., "about": ...,
"items": [{"label": "today", "about": ..., "src": PATH}, ...], "left": 0, "right": 1}]}. A section with one item is
shown as it is (a legend, a map). Each other section shows two of its items in one box: Left and Right buttons pick
them, a yellow line splits them (drag it), Flicker shows the two whole in turn every second, the shown one's name alone
in the corner (r421: with both names up and the line jumping edge to edge it read as broken). Films (.mp4) play looped,
muted, kept in step with the section's first by their speed, and get Pause, to flicker a still frame. Every file is
copied beside OUT_DIR/<name>-compare.html as <name>-cmp-*, so they can be attached to a card together and the page's relative links still work.
"""
import html
import json
import os
import shutil
import sys

STYLE = """
body { background: #1b1d21; color: #e8e8e8; font: 17px/1.45 sans-serif; margin: 24px auto; max-width: 1300px; }
h1 { font-size: 24px; } h2 { font-size: 20px; margin-top: 40px; }
.box { position: relative; width: 100%; user-select: none; cursor: ew-resize; background: #000; }
.box > img, .box > video { display: block; width: 100%; }
.box > .item { position: absolute; left: 0; top: 0; height: 100%; visibility: hidden; }
.box > .item.left { position: relative; height: auto; visibility: visible; }
.box > .item.right { visibility: visible; clip-path: inset(0 0 0 var(--cut)); }
.line { position: absolute; top: 0; bottom: 0; left: var(--cut); width: 2px; background: #ff0; pointer-events: none; }
.tag { position: absolute; top: 8px; padding: 4px 10px; background: #000b; font-weight: bold; pointer-events: none; }
.tag.l { left: 8px; } .tag.r { right: 8px; }
.bar { margin: 6px 0; } .bar b { display: inline-block; width: 60px; }
.bar button { font-size: 16px; padding: 5px 14px; margin-right: 6px; }
.bar button.on { background: #ff0; font-weight: bold; }
.note { color: #b8b8b8; } .legend { color: #b8b8b8; margin: 8px 0 0 0; } .legend b { color: #e8e8e8; }
"""

SCRIPT = """
document.querySelectorAll('.view').forEach(v => {
  const box = v.querySelector('.box'), items = [...box.querySelectorAll('.item')];
  const names = JSON.parse(v.dataset.names), flick = v.querySelector('.flick'), pause = v.querySelector('.pause');
  const tagL = v.querySelector('.tag.l'), tagR = v.querySelector('.tag.r'), line = v.querySelector('.line');
  let left = +v.dataset.left, right = +v.dataset.right, timer = null, cut = 50, shown = -1;
  const draw = () => {
    // Flickering: one version whole (the right one clipped to nothing or to all), its name alone, no line
    items.forEach((it, k) => { it.classList.toggle('left', k === left); it.classList.toggle('right', k === right); });
    box.style.setProperty('--cut', (shown < 0 ? cut : shown === right ? 0 : 100) + '%');
    line.style.display = shown < 0 ? '' : 'none';
    tagL.textContent = shown < 0 ? names[left] : names[shown];
    tagR.style.display = shown < 0 ? '' : 'none';
    tagR.textContent = names[right];
    v.querySelectorAll('.pick-l .pick').forEach((b, k) => b.classList.toggle('on', k === left));
    v.querySelectorAll('.pick-r .pick').forEach((b, k) => b.classList.toggle('on', k === right));
    flick.classList.toggle('on', timer !== null);
  };
  const stop = () => { clearInterval(timer); timer = null; shown = -1; };
  v.querySelectorAll('.pick-l .pick').forEach((b, k) => b.onclick = () => { stop(); left = k; draw(); });
  v.querySelectorAll('.pick-r .pick').forEach((b, k) => b.onclick = () => { stop(); right = k; draw(); });
  flick.onclick = () => {
    if (timer) { stop(); draw(); return; }
    shown = left;
    timer = setInterval(() => { shown = shown === left ? right : left; draw(); }, 1000);
    draw();
  };
  const move = e => {
    const r = box.getBoundingClientRect();
    stop(); cut = Math.max(0, Math.min(100, 100 * (e.clientX - r.left) / r.width)); draw();
  };
  box.addEventListener('pointerdown', move);
  box.addEventListener('pointermove', e => { if (e.buttons === 1 || e.pointerType !== 'mouse') move(e); });
  const vids = items.filter(it => it.tagName === 'VIDEO');
  if (pause) pause.onclick = () => {
    const run = vids[0].paused;
    vids.forEach(w => { if (run) w.play(); else { w.pause(); w.currentTime = vids[0].currentTime; } });
    pause.textContent = run ? 'Pause' : 'Play'; pause.classList.toggle('on', !run);
  };
  // The others follow the first by their speed, sought only when far off or across the loop: a seek is a stutter
  if (vids.length > 1) setInterval(() => vids.slice(1).forEach(w => {
    const d = vids[0].currentTime - w.currentTime;
    if (vids[0].paused) return;
    if (Math.abs(d) > 0.25) { w.currentTime = vids[0].currentTime; w.playbackRate = 1; }
    else w.playbackRate = Math.abs(d) > 0.02 ? 1 + Math.max(-0.1, Math.min(0.1, d)) : 1;
  }), 250);
  draw();
});
"""


def media(src, cls=""):
    if src.endswith(".mp4"):
        return f'<video class="{cls}" src="{src}" autoplay loop muted playsinline></video>'
    return f'<img class="{cls}" src="{src}">'


def main():
    spec_path, out = sys.argv[1:3]
    with open(spec_path) as f:
        spec = json.load(f)
    name = spec["name"]
    os.makedirs(out, exist_ok=True)
    body = [f"<h1>{html.escape(spec['title'])}</h1>", f"<p>{spec.get('intro', '')}</p>"]
    for i, sec in enumerate(spec["sections"], 1):
        srcs = []
        for j, it in enumerate(sec["items"]):
            ext = os.path.splitext(it["src"])[1]
            dst = f"{name}-cmp-s{i}-{j}-{''.join(c if c.isalnum() else '-' for c in it['label'])}{ext}"
            shutil.copy(it["src"], os.path.join(out, dst))
            srcs.append(dst)
        body.append(f"<h2>{i}. {html.escape(sec['title'])}</h2><p class='note'>{sec.get('about', '')}</p>")
        if len(srcs) == 1:
            body.append(f'<div class="box" style="cursor: default">{media(srcs[0])}</div>')
            continue
        labels = [it["label"] for it in sec["items"]]
        buttons = "".join(f"<button class='pick'>{html.escape(lb)}</button>" for lb in labels)
        pause = "<button class='pause'>Pause</button>" if any(s.endswith(".mp4") for s in srcs) else ""
        legend = "".join(f"<li><b>{html.escape(it['label'])}</b>: {it.get('about', '')}</li>" for it in sec["items"])
        body.append(
            f"<div class='view' data-names='{html.escape(json.dumps(labels))}' data-left='{sec.get('left', 0)}' "
            f"data-right='{sec.get('right', 1)}'>"
            f"<div class='bar pick-l'><b>Left</b>{buttons}</div>"
            f"<div class='bar pick-r'><b>Right</b>{buttons}<button class='flick'>Flicker</button>{pause}</div>"
            f"<div class='box'>{''.join(media(s, 'item') for s in srcs)}<div class='line'></div>"
            f"<span class='tag l'></span><span class='tag r'></span></div><ul class='legend'>{legend}</ul></div>")
    page = os.path.join(out, f"{name}-compare.html")
    with open(page, "w") as f:
        f.write(f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(spec['title'])}</title>"
                f"<style>{STYLE}</style></head><body>{''.join(body)}<script>{SCRIPT}</script></body></html>")
    print(page)


if __name__ == "__main__":
    main()
