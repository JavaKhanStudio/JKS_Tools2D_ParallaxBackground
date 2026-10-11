#!/usr/bin/env bash
# r311: every still of the labs r311 touched, into OUT, to compare two commits with compare.py:
#   git worktree add --detach ../before <parent>; (cd ../before && tools/r311-lab-kit/stills.sh $PWD/build/before)
#   tools/r311-lab-kit/stills.sh build/after; python3 tools/r311-lab-kit/compare.py build/before build/after
# The snow is random: effects snow/all and haze move run to run (17k, 15k, 70k pixels). nosnow-*.png are those labs
# with snowDensity=0, which must stay byte-equal but for the readout's timing text (rows 389-436); haze-same.png is
# haze-lab.sh --same's still, without the snow.
# headless: every run below is a --shot, --sets or --same, headless Chrome.
set -u
OUT="$(realpath -m "${1:?usage: stills.sh OUT}")"
cd "$(dirname "$0")/../.." || exit 2
mkdir -p "$OUT"
tools/fog-lab.sh --sets "$OUT/sets" || exit 1
tools/fog-lab.sh --no-build --shot "$OUT/fog.png"
tools/haze-lab.sh --no-build --shot "$OUT/haze.png"
tools/haze-lab.sh --no-build --same "$OUT/haze-same.png"
for lab in fog snow all floor; do
	tools/effects-lab.sh --no-build --lab "$lab" --shot "$OUT/effects-$lab.png"
done
for lab in snow all; do
	tools/effects-lab.sh --no-build --lab "$lab" --slider snowDensity=0 --shot "$OUT/nosnow-$lab.png"
done
