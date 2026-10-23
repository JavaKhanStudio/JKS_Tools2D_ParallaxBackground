#!/usr/bin/env bash
# library-alone-check.sh [REF] — proves the library checks itself without editor/ and demo/ (r130, docs/repo-split.md
# phase 1): clones REF (default HEAD) of this checkout into build/library-alone/<REF>, deletes editor/ and demo/, and
# runs the gates there: ./gradlew build, tools/browser-test.sh, engines/godot/tools/frame-check.sh, engines/jme/tools/frame-check.sh,
# engines/threejs/tools/frame-check.sh.
#
#   tools/library-alone-check.sh                 HEAD, editor/ and demo/ deleted
#   KEEP_APPS=1 tools/library-alone-check.sh X   X as it is: the baseline to compare the scores with
#
# Commit first: the clone holds REF, not the working tree. Writes scores.txt in the clone: every still's mean
# difference and each round's verdict, from both frame comparisons. Diff two runs' scores.txt to see that none moved
# (a REF before r130 writes its reports under demo/build/: read there too). From r133 on, REF has no editor/ or demo/
# to delete: this runs the five gates on a clean clone of it. Exits 1 when a gate fails. Needs what the five gates need (Chrome, godot 4.x, cage, Xwayland, python3 with Pillow).
set -u
cd "$(dirname "$0")/.." || exit 1
REF="${1:-HEAD}"
SHA=$(git rev-parse --short "$REF") || exit 2
DIR="$PWD/build/library-alone/$SHA${KEEP_APPS:+-with-apps}"
rm -rf "$DIR"
git clone -q --no-hardlinks "$PWD" "$DIR" && git -C "$DIR" checkout -q --detach "$SHA" || exit 2
cd "$DIR" || exit 2
[ "${KEEP_APPS:-0}" = 1 ] || rm -rf editor demo
status=0
gate() {
	local log
	# Named by its path: the readers' frame-check.sh would share one log by their base name.
	log="$DIR/$(echo "${1#./}" | tr / -).log"
	echo "== $*"
	if "$@" >"$log" 2>&1; then
		echo "   ok"
	else
		echo "   FAILED: $log"
		tail -5 "$log"
		status=1
	fi
}
gate ./gradlew build
gate tools/browser-test.sh
gate engines/godot/tools/frame-check.sh
gate engines/jme/tools/frame-check.sh
gate engines/threejs/tools/frame-check.sh
for r in {demo/,}build/{godot,jme,threejs}/*/report.txt; do
	[ -f "$r" ] && sed "s|^|$(dirname "$r" | sed 's|.*build/||') |" "$r"
done | sort >"$DIR/scores.txt"
echo "scores: $DIR/scores.txt ($(wc -l <"$DIR/scores.txt") lines)"
exit $status
