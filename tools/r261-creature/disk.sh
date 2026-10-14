#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# disk.sh [REV] — r362: creature.py at REV (default HEAD) and in the working tree over every *_cut.png on disk with its
# painted *_ai.png beside it, here and in parallax-ai's build (../JKS_Tools2D_ParallaxAI/build): sweep.sh's strips and
# every round since. Prints each strip whose answer changed, then how many each refuses. A rule change that refuses a
# strip nobody looked at shows here first; look at it before trusting the sweep. JOBS=8 at a time.
set -u
export LC_ALL=C # sort and join agree on the order
cd "$(dirname "$0")/../.." || exit 1
REV="${1:-HEAD}"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
git archive "$REV" tools/r239-ai-strips | tar -x -C "$T" || exit 1
one() { # DIR CUT: "<exit> <cut> <what it named>"
	s="${2%_cut.png}_ai.png"
	[ -f "$s" ] || return 0
	out=$(python3 "$1"/creature.py "$2" --src "$s" 2>&1)
	echo "$? $2 $(echo "$out" | sed 's/.*: //' | paste -sd';')"
}
export -f one
run() { # DIR OUT
	# shellcheck disable=SC2016 # $0 and $1 are bash -c's, not this shell's
	find build ../JKS_Tools2D_ParallaxAI/build -name '*_cut.png' 2>/dev/null | sort |
		xargs -P "${JOBS:-8}" -I{} bash -c 'one "$0" "$1"' "$1" {} | sort -k2 >"$2"
}
run "$T/tools/r239-ai-strips" "$T/before"
run tools/r239-ai-strips "$T/after"
join -j 2 <(cut -d' ' -f1,2 "$T/before") <(cut -d' ' -f1,2 "$T/after") | awk '$2 != $3 { print $2 " -> " $3 "  " $1 }'
echo "disk: $(wc -l <"$T/after") strips; $REV refuses $(awk '$1 != 0' "$T/before" | wc -l), the working tree $(awk '$1 != 0' "$T/after" | wc -l)"
