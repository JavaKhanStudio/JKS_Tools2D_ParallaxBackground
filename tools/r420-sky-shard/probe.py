#!/usr/bin/env python3
"""r420's probe: what sky_apart is handed and gives back over a box of a cached painting, no GPU.

    ~/ComfyUI/venv/bin/python tools/r420-sky-shard/probe.py CACHE.npz OUT X0 X1 Y0 Y1 [--depth-page PATH]

Runs tools/r409-clouds/replay.py's main, catching sky_apart's arguments and result, and prints per pixel of the box
the band before (L), the label after (S: -1 sky), the depth (dn x100) and the colour, and saves OUT/sky_apart.npz.
"""
import argparse
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "r409-clouds"))
sys.path.insert(0, os.path.join(HERE, "..", "r380-twig-gaps"))
import bands  # noqa: E402
import replay  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cache")
    ap.add_argument("out")
    ap.add_argument("box", nargs=4, type=int)
    ap.add_argument("--depth-page", default=bands.DEFAULT)
    a = ap.parse_args()
    got = {}
    load = bands.load

    def caught(path):
        dp = load(path)
        sky_apart = dp.sky_apart

        def spy(L, dn, clouds, area, img=None, **kw):
            S = sky_apart(L, dn, clouds, area, img, **kw)
            got.update(L=L, dn=dn, clouds=clouds, img=img, S=S, sky=dp.SKY)
            return S
        dp.sky_apart = spy
        return dp
    bands.load = caught
    sys.argv = ["replay.py", a.cache, a.out, "--theme", "probe", "--depth-page", a.depth_page]
    replay.main()
    np.savez_compressed(os.path.join(a.out, "sky_apart.npz"), **{k: v for k, v in got.items() if k != "sky"})
    x0, x1, y0, y1 = a.box
    print(f"SKY {got['sky']}; per row: L (band before), S (after, s = sky), dn x100, colour")
    for y in range(y0, y1):
        print(y, " ".join(("s" if got["S"][y, x] < 0 else str(got["S"][y, x])) + str(got["L"][y, x])
                          + f"{100 * got['dn'][y, x]:02.0f}" for x in range(x0, x1)))
    for y in range(y0, y1):
        print(y, " ".join("{:02x}{:02x}{:02x}".format(*got["img"][y, x]) for x in range(x0, x1)))


if __name__ == "__main__":
    sys.exit(main())
