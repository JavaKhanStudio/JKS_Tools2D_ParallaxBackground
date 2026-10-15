"""Clips side by side, one row each, a crop of each frame, as a film (r375): to judge motions against each other.

    python3 tools/r375-animated-layer/compare.py OUT.mp4 --box X0,Y0,X1,Y1 [--scale 2] [--frames 48] [--fps 16] \
        [--bg 5a8cc8] "LABEL=FRAMES_DIR" ...

Each FRAMES_DIR holds f000.png ... (loopcheck.py's clips); a shorter clip loops within the film. Transparent pixels
show over --bg. The film runs the frames three times; OUT-f0.png is its first frame.
"""
import argparse
import glob
import os
import subprocess
import tempfile

from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("clips", nargs="+", help="LABEL=FRAMES_DIR")
    ap.add_argument("--box", required=True)
    ap.add_argument("--scale", type=int, default=1)
    ap.add_argument("--frames", type=int, default=48)
    ap.add_argument("--fps", type=int, default=16)
    ap.add_argument("--bg", default="5a8cc8")
    a = ap.parse_args()
    box = [int(v) for v in a.box.split(",")]
    bg = tuple(int(a.bg[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    w, h = (box[2] - box[0]) * a.scale // 2 * 2, (box[3] - box[1]) * a.scale
    rows = [(c.split("=", 1)[0], sorted(glob.glob(os.path.join(c.split("=", 1)[1], "f*.png")))) for c in a.clips]
    for c, (_, files) in zip(a.clips, rows):
        if not files:
            ap.error(f"no f*.png in {c.split('=', 1)[1]}")
    with tempfile.TemporaryDirectory() as tmp:
        for i in range(a.frames):
            sheet = Image.new("RGB", (w, (h + 18) * len(rows)), (20, 20, 20))
            draw = ImageDraw.Draw(sheet)
            for j, (label, files) in enumerate(rows):
                f = Image.open(files[i % len(files)]).convert("RGBA").crop(box).resize((w, h), Image.LANCZOS)
                under = Image.new("RGBA", f.size, bg)
                under.alpha_composite(f)
                sheet.paste(under.convert("RGB"), (0, j * (h + 18) + 18))
                draw.text((4, j * (h + 18) + 3), label, fill=(255, 255, 255))
            if i == 0:
                sheet.save(os.path.splitext(a.out)[0] + "-f0.png")
            for k in range(3):
                sheet.save(os.path.join(tmp, f"{k * a.frames + i:05d}.png"))
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-framerate", str(a.fps), "-i",
                        os.path.join(tmp, "%05d.png"), "-pix_fmt", "yuv420p", "-crf", "16", a.out], check=True)


if __name__ == "__main__":
    main()
