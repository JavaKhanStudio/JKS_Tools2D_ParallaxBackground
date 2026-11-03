#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# recut.sh ROOT OUT [JOBS] — r501: cut r496's ruins strips again with this checkout's layer.py into OUT (ROOT untouched,
# no GPU), then agree.py on them: with the creature.py that wrote ROOT's logs, reading a cut as read_alpha does (its
# blobs against those logs), and with this checkout's (its answers against its own gate). KEYED: OUT/keyed, alphas.py's.
set -u
cd "$(dirname "$0")/../.." || exit 1
ROOT=$1 OUT=$2 JOBS=${3:-8}
OLD="$OUT/old/tools/r239-ai-strips"
rm -rf "$OUT/old" "$OUT/recut"
mkdir -p "$OUT/old"
git archive 83e3f33^ tools/r239-ai-strips tools/parallax_rules.py | tar -x -C "$OUT/old" || exit 1
sed -i 's|alpha = np.asarray(cut.getchannel("A").resize(src.size, Image.NEAREST)).astype(np.float64) / 255|alpha = np.asarray(cut.getchannel("A").resize(src.size, Image.NEAREST)).astype(np.float64); alpha = np.where((alpha == 0) \| (alpha == 255), alpha / 255, (alpha + 0.5) / 255)|' "$OLD/creature.py"
grep -q "alpha + 0.5" "$OLD/creature.py" || { echo "the old creature.py's alpha line moved"; exit 2; }
one() { # ROOT OUT PREFIX
	rel=${3#"$1"/}
	mkdir -p "$2/recut/$(dirname "$rel")"
	for x in ai mask; do ln -sf "$(realpath "$3_$x.png")" "$2/recut/${rel}_$x.png"; done
	cp "$3_cut.log" "$2/recut/${rel}_cut.log"
	OMP_NUM_THREADS=1 python3 tools/r239-ai-strips/layer.py "$3_ai.png" "$2/recut/${rel}_cut.png" --mask "$3_mask.png" \
		--grow 16 --kind ruins --ckpt DreamShaperXL_Turbo_v2_1.safetensors --no-creatures 2>/dev/null
}
export -f one
# shellcheck disable=SC2016 # $0, $1, $2 are bash -c's
find "$ROOT" -name 'l*_ai.png' | sed 's/_ai.png$//' | sort | xargs -P "$JOBS" -I{} bash -c 'one "$0" "$1" "$2"' "$ROOT" "$OUT" {}
echo "== the rules that wrote the logs, on the cuts as they are"
OMP_NUM_THREADS=1 python3 tools/r501-creature-alpha/agree.py "$ROOT" "$OLD" "$OUT/keyed" "$JOBS"
echo "== the rules that wrote the logs, cut again"
OMP_NUM_THREADS=1 python3 tools/r501-creature-alpha/agree.py "$OUT/recut" "$OLD" "$OUT/keyed" "$JOBS"; a=$?
echo "== this checkout's rules, cut again"
OMP_NUM_THREADS=1 python3 tools/r501-creature-alpha/agree.py "$OUT/recut" tools/r239-ai-strips "$OUT/keyed" "$JOBS"; b=$?
[ $a = 0 ] && [ $b = 0 ]
