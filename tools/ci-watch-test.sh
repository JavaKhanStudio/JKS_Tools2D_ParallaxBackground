#!/usr/bin/env bash
# ci-watch-test.sh — proves tools/ci-watch.sh (r339) with gh and atelier stubbed on PATH: a failed run with no open
# "CI red" task adds one #build task naming the failed job; a failed run with one open notes it instead; a success, a
# cancelled run and no run at all add nothing; a red run of the same SHA dispatched on a ci/* branch is not the push's
# (r342). Exit 0 when all hold.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd) || exit 1
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
# gh: the push run of $RUN ("status conclusion", or empty for none), a failed job, a log line. With $DISPATCHED, a
# newer run of the same SHA dispatched on a ci/* branch, listed unless the call asks for develop's pushes.
cat > "$tmp/bin/gh" <<'GH'
#!/usr/bin/env bash
case "$*" in
	"run list"*"--branch develop --event push"*) [ -z "$RUN" ] || echo "42 $RUN" ;;
	"run list"*) if [ -n "${DISPATCHED:-}" ]; then echo "43 $DISPATCHED"; elif [ -n "$RUN" ]; then echo "42 $RUN"; fi ;;
	*"--json url"*) echo "https://ci.example/42" ;;
	*"--json jobs"*) echo "  build (17): ./gradlew build" ;;
	*"--log-failed"*) printf 'build\tstep\tFAILED SomeTest\n' ;;
esac
GH
# atelier: records each call; `list` prints $OPEN as the board's open tasks.
cat > "$tmp/bin/atelier" <<'AT'
#!/usr/bin/env bash
echo "$*" >> "$CALLS"
[ "$1" = list ] && echo "${OPEN:-[]}"
exit 0
AT
chmod +x "$tmp/bin/gh" "$tmp/bin/atelier"
sha=$(git -C "$HERE/.." rev-parse HEAD)
fails=0
expect() { # NAME RUN OPEN WANT-REGEX (of the non-list calls; empty: none) [DISPATCHED]
	: > "$tmp/calls"
	DISPATCHED=${5:-} RUN=$2 OPEN=$3 CALLS="$tmp/calls" PATH="$tmp/bin:$PATH" CI_WATCH_POLL=0 CI_WATCH_WAIT_RUN=0 \
		"$HERE/ci-watch.sh" "$sha" develop >/dev/null 2>&1
	local got
	got=$(grep -v '^list' "$tmp/calls" | tr '\n' ' ')
	if { [ -z "$4" ] && [ -z "$got" ]; } || { [ -n "$4" ] && [[ "$got" =~ $4 ]]; }; then
		echo "ok: $1"
	else
		echo "FAIL: $1 (calls: ${got:-none})"; fails=1
	fi
}
expect "a red run adds a #build task naming the failed job" "completed failure" "[]" \
	"^add .*--tags build .*build \(17\): \./gradlew build.*FAILED SomeTest.*CI red on develop $"
expect "a red run with one open notes it" "completed failure" '[{"ref": "r9", "title": "CI red on develop"}]' \
	"^note r9 .*Still red"
expect "a green run adds nothing" "completed success" "[]" ""
expect "a cancelled run adds nothing" "completed cancelled" "[]" ""
expect "no run adds nothing" "" "[]" ""
expect "a red ci/* dispatch of the same SHA adds nothing while the push is green" "completed success" "[]" "" \
	"completed failure"
expect "a green ci/* dispatch hides no red push" "completed failure" "[]" "^add .*CI red on develop $" \
	"completed success"
exit $fails
