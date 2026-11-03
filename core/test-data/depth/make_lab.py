#!/usr/bin/env python3
"""Writes the depth lab's Hiver (r504; DepthLab.FILES): engines/rounds/shaders/s01.jplax, the fog lab's page and a reader
round's, which stay as they are, filling the lab's 2D frame. Same layers, same speeds: the depths the lab shows do not move.
Run from the repository root: python3 core/test-data/depth/make_lab.py
"""
import json

SOURCE = 'engines/rounds/shaders/s01.jplax'
OUT = 'core/test-data/depth/hiver.jplax'
# r502: every layer hangs at 45-70% of the height over white gradients, which left the frame's bottom 45% white. As r414's
# lurker lab: every layer goes down this many percent, the front pines on the view's bottom, and the gradients take Hiver's
# sky (over the art's top) and its snow (in the pines' gaps).
DROP = 45.0
SKY = {'r': 170 / 255, 'g': 168 / 255, 'b': 190 / 255, 'a': 1.0}
SNOW = {'r': 225 / 255, 'g': 233 / 255, 'b': 222 / 255, 'a': 1.0}
# Lowered as it is, the mountains show what the view's top used to hide: the art is cut flat at its region's top, through a
# peak and its pines. Scaled by this about their foot, that edge stays over the view's top (they stand 33.3% tall at
# 1.18, their foot 25% up, so 25 + 33.3 x 2.3 = 101.6%), and the frame's upper half is a valley instead of an empty sky.
MOUNTAINS = 1
MOUNTAINS_SCALE = 2.3


def main():
    with open(SOURCE) as f:
        page = json.load(f)
    page.update({'topHalf_top': SKY, 'topHalf_bottom': SKY, 'bottomHalf_top': SNOW, 'bottomHalf_bottom': SNOW})
    layers = page['pageModel']['pageList']
    for layer in layers:
        layer['decal_Y_Ratio'] = round(layer['decal_Y_Ratio'] - DROP, 6)
    layers[MOUNTAINS]['sizeRatio'] = round(layers[MOUNTAINS]['sizeRatio'] * MOUNTAINS_SCALE, 6)
    with open(OUT, 'w') as f:
        json.dump(page, f, indent=1)
        f.write('\n')


if __name__ == '__main__':
    main()
