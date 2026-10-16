#!/usr/bin/env bash
# headless: unit tests, no window
# mutate.sh — r384's proof: each part of the hills' inpaint recipe in ai_page.py broken in turn, hills_paint_test.py
# must fail on an assertion (a FAIL, not an import error: a mutant that breaks the import passes nothing).
# Copies tools/*.py and tools/r239-ai-strips into a scratch folder; the clean copy must pass first. Exits 1 when one
# survives.
set -u
cd "$(dirname "$0")/../.." || exit 1
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp tools/*.py "$WORK/"
cp -r tools/r239-ai-strips "$WORK/"
T="$WORK/r239-ai-strips/hills_paint_test.py"
python3 "$T" >"$WORK/clean.log" 2>&1 || { echo "the clean copy fails: $WORK/clean.log"; cat "$WORK/clean.log"; exit 2; }
survived=0
mutate() {  # name, the line as it is, the line broken
	cp tools/r239-ai-strips/ai_page.py "$WORK/r239-ai-strips/ai_page.py"
	python3 - "$WORK/r239-ai-strips/ai_page.py" "$2" "$3" <<'PY' || { echo "$1: the line is gone, update mutate.sh"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
if s.count(old) != 1:
    sys.exit(1)
open(p, "w").write(s.replace(old, new))
PY
	python3 "$T" >"$WORK/$1.log" 2>&1
	if ! grep -q '^FAIL:' "$WORK/$1.log"; then
		echo "SURVIVED  $1"; tail -5 "$WORK/$1.log"
		survived=1
	else
		echo "killed    $1 ($(grep -c '^FAIL:' "$WORK/$1.log") failing)"
	fi
}
mutate no-inpaint 'return kind == "hills" and not self.pixel' 'return False and not self.pixel'
mutate any-denoise ' and self.a.denoise is None' ''
mutate any-ckpt 'self.model["ckpt"] == GEN_CKPT and ' ''
mutate init-not-src 'if gen[2] == "inpaint":
            return ["--src"' 'if False:
            return ["--src"'
mutate old-words '"words": "rolling hills with small round trees, folds and shadows"' '"words": "rolling hills with small trees"'
mutate no-gate 'if self.inpaints(kind) and gate:' 'if False:'
mutate no-spread 'if spread < CANOPY_SPREAD or sharp' 'if sharp'
mutate no-sharp 'spread < CANOPY_SPREAD or sharp > CANOPY_SHARP:' 'spread < CANOPY_SPREAD:'
mutate few-tries 'tries = max(a.tries, HILLS_TRIES) if' 'tries = a.tries if'
mutate no-fallback '                if best:  #' '                if False:  #'
mutate fallback-gated 'got = self.paint(k, kind, seed, prefix, gate=False)' 'got = self.paint(k, kind, seed, prefix)'
mutate pines-struck '[x for x in self.ks if x != "pines"] if self.inpaints(kind) else self.ks' 'self.ks'
mutate conifer-best 'spread if sharp <= CANOPY_SHARP else -sharp' 'spread'
exit $survived
