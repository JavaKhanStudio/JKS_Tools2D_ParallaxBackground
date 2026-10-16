"""r419: does the animation lab's SWELL jump? Two clips of the lab's single sea view, the same scroll, one with SWELL
and one with its amplitude 0: their difference is what SWELL alone moves, and a swell that runs on changes that about
as much every frame. A phase that jumps (along() wrapping where a wave is not a whole number of turns) stands far above
its neighbours. Prints the median frame change and the frames past RATIO times it; exits 1 on any.
    tools/anim-lab.sh --slider view=3 --clip SWELL.mp4
    tools/anim-lab.sh --slider view=3 --slider swellAmplitude=0 --clip FLAT.mp4
    python3 tools/r419-swell-loop/jumps.py SWELL.mp4 FLAT.mp4 --top 292 --bottom 402
"""
import argparse
import subprocess
import sys

import numpy as np


def frames(path):
    probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                            "-of", "csv=p=0", path], capture_output=True, text=True, check=True).stdout.split(",")
    w, h = int(probe[0]), int(probe[1])
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "rawvideo", "-pix_fmt", "gray", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(-1, h, w).astype(np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("swell")
    ap.add_argument("flat")
    ap.add_argument("--top", type=int, default=292, help="the sea band's rows in the clip")
    ap.add_argument("--bottom", type=int, default=402)
    ap.add_argument("--left", type=int, default=12)
    ap.add_argument("--right", type=int, default=1528)
    ap.add_argument("--ratio", type=float, default=2.0)
    a = ap.parse_args()
    band = np.s_[:, a.top:a.bottom, a.left:a.right]
    moved = frames(a.swell)[band] - frames(a.flat)[band]
    change = np.abs(np.diff(moved, axis=0)).mean(axis=(1, 2))
    med = float(np.median(change))
    spikes = [(i + 1, round(float(c), 3)) for i, c in enumerate(change) if c > a.ratio * med]
    print(f"frames {len(moved)}, SWELL's own change a frame: median {med:.3f}/255, max {change.max():.3f}/255")
    print(f"jumps (frame, change) past {a.ratio:.1f}x the median: {spikes or 'none'}")
    return 1 if spikes else 0


if __name__ == "__main__":
    sys.exit(main())
