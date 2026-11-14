"""5. the page (r533: split out of depth_page.py): the speeds, the atlas, the .jplax and its round.json, and the
sheet."""
import json
import os

import dp_common  # noqa: F401  (sys.path: make_page, parallax_lab)
import make_page
import numpy as np
import parallax_lab as lab
from PIL import Image

ROOT = lab.ROOT


def speeds_of(layers, front_speed):
    """Each layer's speed: proportional to its disparity, the front at front_speed, none under a twentieth of it,
    each at least 1.25 times the one behind."""
    front = max(layers[-1]["disparity"], 1e-3)
    s, prev = [], 0.0
    for ly in layers:
        v = max(front_speed * ly["disparity"] / front, front_speed / 20, prev * 1.25 if prev else 0)
        s.append(round(min(v, front_speed), 5))
        prev = v
    s[-1] = front_speed
    return s


def write_page(name, layers, s, img, page_dir, a):
    """5. The page: the atlas, the .jplax and its round.json; (scene, its folder from ROOT, the sky's colours)."""
    n = len(layers)
    h, w = img.shape[:2]
    make_page.pack(name, [(ly["name"], ly["path"]) for ly in layers], "Linear,Linear", out=page_dir)
    # Every layer's bottom on the screen's bottom, but the clouds' (r409): cut at their last row, raised by the rows
    # under it, and drifting at their rest speed.
    page_layers = [lab.layer(ly["name"], speed=s[k], rest=ly.get("rest", 0.0),
                             dy=round(100 * (h - ly.get("bottom", h)) * lab.SCREEN_W / w / lab.SCREEN_H, 3))
                   for k, ly in enumerate(layers)]
    sky = (seam_colour(img[:4]), seam_colour(img[-4:]))
    page = lab.page_of(f"{name}.atlas", page_layers, sky=(sky[0], sky[0]), ground=(sky[1], sky[1]), top_size=0.5,
                       bottom_size=0.5)
    with open(os.path.join(page_dir, f"{name}.jplax"), "w") as f:
        json.dump(page, f, indent=1)
    rel = os.path.relpath(page_dir, ROOT)
    about = (f"r279 depth-page: {os.path.basename(a.painting)} cut into {a.layers} depth bands"
             + (", the sky and its clouds apart (r409)" if n > a.layers else "") + ", speeds "
             + ", ".join(f"{v:g}" for v in s))
    scene = {"id": "d01", "page": f"{rel}/{name}.jplax", "atlasDir": rel, "name": name, "about": about}
    with open(os.path.join(page_dir, "round.json"), "w") as f:
        json.dump({"round": name, "about": about, "scenes": [scene]}, f, indent=1)
    return scene, rel, sky


def seam_colour(rows):
    return "".join(f"{int(v):02x}" for v in np.median(rows.reshape(-1, 3), axis=0))


def sheet(cut, layers):
    """The painting, its depth, its bands, then each layer raw (the hole see-through) and repainted, on a checker."""
    paint = Image.open(os.path.join(cut, "painting.png"))
    w, h = paint.size
    tw, th = w // 2, h // 2
    check = np.indices((th, tw)).sum(axis=0) // 8 % 2
    bg = Image.fromarray(np.where(check[..., None], 200, 150).repeat(3, axis=2).astype(np.uint8))
    tiles = [paint, Image.open(os.path.join(cut, "depth.png")).convert("RGB"),
             Image.open(os.path.join(cut, "bands.png"))]
    for ly in layers:
        for f in (ly["raw"], ly["path"]):
            im = Image.open(f).convert("RGBA")
            full = Image.new("RGBA", (w, h))
            full.paste(im, (0, ly.get("bottom", h) - im.height if f == ly["path"] else h - im.height))
            t = bg.copy()
            t.paste(full.resize((tw, th)), (0, 0), full.resize((tw, th)))
            tiles.append(t)
    cols = 3
    out = Image.new("RGB", (cols * tw, -(-len(tiles) // cols) * th), "black")
    for i, t in enumerate(tiles):
        out.paste(t.convert("RGB").resize((tw, th)), ((i % cols) * tw, (i // cols) * th))
    out.save(os.path.join(cut, "sheet.png"))
