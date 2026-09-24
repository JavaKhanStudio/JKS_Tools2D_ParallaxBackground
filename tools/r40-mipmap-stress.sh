#!/usr/bin/env bash
# r40-mipmap-stress.sh — does mipmapping a power-of-two atlas page pay? 200 layers, Linear vs MipMapLinearLinear.
#
#   tools/r40-mipmap-stress.sh
#
# Pads copies of the demo's sample atlases (3647 px wide, not powers of two) to 4096x4096 pages, as the editor now
# exports them, writes one copy per filter under demo/build/stress/r40pot, and runs ParallaxStress on each.
# The samples themselves are never touched (AGENTS.md: they are fixtures).
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
import os, re
from PIL import Image
out = 'demo/build/stress/r40pot'
for name in ['Hiver', 'Printemps']:
    page = Image.open(f'demo/assets/{name}.png')
    padded = Image.new('RGBA', (4096, 4096), (0, 0, 0, 0))
    padded.paste(page, (0, 0))
    atlas = re.sub(r'size: *\d+, *\d+', 'size: 4096, 4096', open(f'demo/assets/{name}.atlas').read(), count=1)
    for kind, filt in [('lin', 'Linear,Linear'), ('mip', 'MipMapLinearLinear,Linear')]:
        os.makedirs(f'{out}/{kind}', exist_ok=True)
        padded.save(f'{out}/{kind}/{name}.png')
        open(f'{out}/{kind}/{name}.atlas', 'w').write(re.sub(r'filter: *[^\n]+', 'filter: ' + filt, atlas))
PY
for kind in lin mip; do
	echo "$kind:"
	./gradlew -q :demo:stress --args="--layers 200 --seconds 4 --every 0 --atlases ../build/stress/r40pot/$kind/Hiver.atlas,../build/stress/r40pot/$kind/Printemps.atlas" 2>&1 | grep '^stress'
done
