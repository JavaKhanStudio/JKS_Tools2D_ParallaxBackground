#!/usr/bin/env bash
# mutate-effects.sh [MUTANT...] — do the shaders, fog and transfer rounds see each piece of the three.js reader's
# effects (r39) and transfer styles (r40)?
#
# Each mutant breaks one piece of engines/threejs/src/plax.js (a basic sed, so ( ? + are literal; the file restored
# after each run and on exit), runs frame-check.sh on its round and prints the lines of the scenes it should fail: a
# mutant whose round still PASSES is a piece no still sees. It refuses to start when plax.js differs from the last
# commit, so it never stomps an edit in progress. r39's run: every mutant failed, its worst still 7.9 (wrapx, s06 at
# 12 s) to 68.6 / 255 (swell, s11).
#   wave    WAVE's ripple flattened (shift 0)                 shaders s01-s05, s08
#   fog     FOG's patches gone (opacity kept)                 shaders s01-s07
#   swell   SWELL's waves flattened                           shaders s09-s11
#   wrapx   wrappedX forgotten: FOG's noise jumps at a tile   shaders s06
#   wrapy   wrappedY forgotten                                shaders s07
#   haze    the page fog off (haze 0)                         fog f01-f05
#   drift   the dissolve's noise never drifts                 transfer t09, t10, t15
#   grade   the gradients fade straight, not through a colour transfer t11-t14, t16, t17
#   stagger every slot at the same point of the fade          transfer t07-t09, t12-t15
# r40's run: each failed, its worst still haze 46.2 (f02), drift 42.9 (t09), grade 8.6 (t14), stagger 78.9 (t12).
set -u
cd "$(dirname "$0")/../../.." || exit 1
JS=engines/threejs/src/plax.js
git diff --quiet HEAD -- "$JS" || { echo "mutate-effects: $JS has uncommitted edits: commit or stash them first" >&2; exit 2; }
KEEP=$(mktemp)
cp "$JS" "$KEEP"
trap 'cp "$KEEP" "$JS"; rm -f "$KEEP"' EXIT
declare -A SED=(
	[wave]='s/float shift = effect.x \* sin/float shift = 0.0 * sin/'
	[fog]='s/color.a \*= 1.0 - effect.x \* n;/color.a *= 1.0;/'
	[swell]='s/vec2 l = local(vUv) + effect.x \* vec2/vec2 l = local(vUv) + 0.0 * vec2/'
	[wrapx]='s/l.wrappedX = period > 0 ? (l.wrappedX + before - l.distanceX) % period : 0;/l.wrappedX = 0;/'
	[wrapy]='s/l.wrappedY = periodY > 0 ? (l.wrappedY + below - l.distanceY) % periodY : 0;/l.wrappedY = 0;/'
	[haze]='s/u.haze.value = pageFog ? packed(l.haze) : l.haze;/u.haze.value = 0;/'
	[drift]='s/PlaxTransfertStyle.drift(this._effectTime));/0);/'
	[grade]='s/return lerp(this.showsIncoming(progress, 0, 1) ? to : from, this.color, this.gradeAt(progress, 0, 1));/return lerp(from, to, progress);/'
	[stagger]='s/return clamp(progress \* (1 + this.stagger) - this.stagger \* k, 0, 1);/return clamp(progress, 0, 1);/'
)
declare -A ROUND=([wave]=shaders [fog]=shaders [swell]=shaders [wrapx]=shaders [wrapy]=shaders [haze]=fog [drift]=transfer
	[grade]=transfer [stagger]=transfer)
declare -A SCENES=([wave]='s0[1-58]' [fog]='s0[1-7]' [swell]='s(09|1[01])' [wrapx]='s06' [wrapy]='s07' [haze]='f0[1-5]'
	[drift]='t(09|10|15)' [grade]='t(1[1-467])' [stagger]='t(0[789]|1[2-5])')
MUTANTS=("$@")
[ ${#MUTANTS[@]} -eq 0 ] && MUTANTS=(wave fog swell wrapx wrapy haze drift grade stagger)
status=0
for m in "${MUTANTS[@]}"; do
	cp "$KEEP" "$JS"
	sed -i "${SED[$m]}" "$JS"
	cmp -s "$KEEP" "$JS" && { echo "$m: the sed matched nothing: fix this script"; status=1; continue; }
	log=$(engines/threejs/tools/frame-check.sh "engines/rounds/${ROUND[$m]}" 2>&1)
	echo "== $m (${ROUND[$m]}):"
	echo "$log" | grep -E "^(${SCENES[$m]})-t" | sed 's/^/   /'
	if echo "$log" | grep -q '^PASS'; then echo "   $m: the round PASSES: no still sees it"; status=1; fi
done
exit $status
