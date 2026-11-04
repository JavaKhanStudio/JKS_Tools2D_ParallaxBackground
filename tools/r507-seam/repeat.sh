#!/usr/bin/env bash
# r507: tools/depth-lab.sh --seam again and again under load, the count written down. A run that fails keeps its stills
# in OUT/runNN; one that passes keeps its log only. The last line of OUT/summary.txt is the count.
#   tools/r507-seam/repeat.sh OUT [RUNS, 20] [BURNERS, as many as there are cores; 0 when other work already loads the machine]
# DEPTH_AA=1 in the environment keeps the multisampling drive.py turns off, the control: 5 of 20 runs failed
# with it, each on a still with a layer gone, 0 of 25 without, side by side at load 15-115 (r507).
# headless: --seam only, and depth-lab.sh's headless Chrome goes through tools/browser.py; no window opens.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT=$(realpath -m "$1") RUNS=${2:-20} BURNERS=${3:-$(nproc)}
mkdir -p "$OUT"
BURN=()
trap 'kill "${BURN[@]}" 2>/dev/null' EXIT
for _ in $(seq "$BURNERS"); do
	python3 -c 'while True: pass' &
	BURN+=($!)
done
FAILED=0
for n in $(seq -w 1 "$RUNS"); do
	DIR="$OUT/run$n"
	START=$SECONDS
	# --no-build: the war stays the one under test, and its date in each line shows if another session rebuilt it.
	if tools/depth-lab.sh --no-build --seam "$DIR" >"$OUT/run$n.log" 2>&1; then
		VERDICT=pass
		rm -rf "$DIR"
	else
		VERDICT=FAIL
		FAILED=$((FAILED + 1))
	fi
	WAR=$(date -r core/build/browserTest/war/browsertests/browsertests.nocache.js +%H:%M:%S)
	echo "run $n $VERDICT $((SECONDS - START)) s, load $(cut -d' ' -f1 /proc/loadavg), war $WAR" | tee -a "$OUT/summary.txt"
done
echo "$((10#$RUNS - FAILED)) of $((10#$RUNS)) passed, $BURNERS burners" | tee -a "$OUT/summary.txt"
[ "$FAILED" = 0 ]
