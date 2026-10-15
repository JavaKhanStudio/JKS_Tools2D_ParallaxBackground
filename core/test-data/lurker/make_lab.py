#!/usr/bin/env python3
"""Writes the lurker lab's pages (r369, Simon: "do we have a lab associated with this? I want to test it in action";
core/browser-test/.../LurkerLab, tools/effects-lab.sh --lab lurker): r363's demo (tools/r363-reveal/make.py) on an
atlas the lab can ship without a copy of Hiver.png: figure.png, the hooded figure alone; HiverLurker.atlas, Hiver's
regions then the figure on a page of its own; lab.jplax, v01 (the figure at its depth behind Hiver's front pines), its
lurkerCover baked by tools/lurker-cover.sh in the lab's 40 x 22.5 world; empty.jplax, v00, the same page without it.
Run from the repository root: python3 core/test-data/lurker/make_lab.py (needs Pillow, Java and the gradle build)."""
import importlib.util
import json
import subprocess

HERE = 'core/test-data/lurker'
SAMPLES = 'core/test-data/samples'

spec = importlib.util.spec_from_file_location('reveal', 'tools/r363-reveal/make.py')
reveal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reveal)


def main():
    fig = reveal.figure()
    fig.save(f'{HERE}/figure.png')
    with open(f'{SAMPLES}/Hiver.atlas') as f:
        hiver = f.read().replace('Hiver.png', '../samples/Hiver.png', 1).rstrip('\n')
    w, h = fig.size
    with open(f'{HERE}/HiverLurker.atlas', 'w') as f:
        f.write(f'{hiver}\n\nfigure.png\nsize: {w}, {h}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n'
                f'figure\n  rotate: false\n  xy: 0, 0\n  size: {w}, {h}\n  orig: {w}, {h}\n  offset: 0, 0\n'
                '  index: -1\n')
    for name, units in (('empty', 0), ('lab', 1.8)):
        page = reveal.page_with_figure(fig.size, units)
        page['pageModel']['atlasName'] = 'HiverLurker.atlas'
        with open(f'{HERE}/{name}.jplax', 'w') as f:
            json.dump(page, f, indent=1)
    subprocess.run(['tools/lurker-cover.sh', f'{HERE}/lab.jplax', HERE, '40', '22.5'], check=True)


if __name__ == '__main__':
    main()
