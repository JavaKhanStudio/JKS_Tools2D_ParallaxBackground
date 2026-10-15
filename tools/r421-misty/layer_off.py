#!/usr/bin/env python3
"""r421: layer.py with misty_pines forced off, for recut.sh (which gives it "--kind" and "--ckpt" as it gives layer.py).

    tools/r364-ghosts/recut.sh tools/r421-misty/layer_off.py OUT PAGE...
"""
import os
import sys

LAYER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips", "layer.py")
os.execv(sys.executable, [sys.executable, LAYER, *sys.argv[1:], "--rule", "misty_pines=off"])
