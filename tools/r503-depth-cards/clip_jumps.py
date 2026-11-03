#!/usr/bin/env python3
"""Does a clip flicker? (r503) Counts the frames that differ from the one before by more than JUMP/255 on average, each a cut
or a frame with a layer or the sky missing. A clip of the depth lab has at most its slide's few; ANGLE on the GPU gave 24-70.
  python3 tools/r503-depth-cards/clip_jumps.py F.mp4 [MOST]     exit 1 over MOST (default 5) jumps
"""
import subprocess
import sys

import numpy as np

W, H = 160, 90
JUMP = 15


def main():
    most = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', sys.argv[1], '-vf', f'scale={W}:{H},format=gray', '-f', 'rawvideo', '-'],
                         check=True, stdout=subprocess.PIPE).stdout
    frames = np.frombuffer(raw, dtype=np.uint8).reshape(-1, H, W).astype(int)
    steps = np.abs(np.diff(frames, axis=0)).mean(axis=(1, 2))
    jumps = np.nonzero(steps > JUMP)[0]
    print(f'{sys.argv[1]}: {len(frames)} frames, {len(jumps)} jumps over {JUMP}/255: {[int(j) for j in jumps[:24]]}')
    sys.exit(1 if len(jumps) > most else 0)


if __name__ == '__main__':
    main()
