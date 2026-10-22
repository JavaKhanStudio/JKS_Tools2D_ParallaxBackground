#!/usr/bin/env bash
# r488: the wall time of the browser gate and three heavy headless runs (the lab kit's fit shots, the panel zoom, an
# 8 s anim-lab clip), one after another, in the checkout TREE (a worktree of the commit before r488 for the "before").
# Run it for both trees serially, alternating, on the same machine: a parallel pair measures the load, not the GL.
#   tools/r488-gpu/time.sh TREE OUT     prints "<run> <seconds> exit <code>" per run, the load before each
# The war must be built in TREE (./gradlew :core:browserTestWar). PARALLAX_GL=soft times SwiftShader in an r488 tree.
# headless: every run below is headless Chrome through tools/browser.py, no window.
set -u
TREE="$(realpath "${1:?usage: time.sh TREE OUT}")"
OUT="$(realpath -m "${2:?usage: time.sh TREE OUT}")"
mkdir -p "$OUT"
cd "$TREE" || exit 1
run() {
	local name=$1; shift
	echo "load $(cut -d' ' -f1 /proc/loadavg)"
	local start=$SECONDS
	"$@" >"$OUT/$name.log" 2>&1
	local code=$?
	echo "$name $((SECONDS - start)) exit $code"
}
run gate tools/browser-test.sh --no-build
run shots tools/r232-lab-kit/shots.sh "$OUT/shots" --no-build
run zoom tools/r452-lab-zoom/zoom.sh "$OUT/zoom" --no-build
run clip tools/anim-lab.sh --no-build --clip "$OUT/anim.mp4"
