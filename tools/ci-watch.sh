#!/usr/bin/env bash
# ci-watch.sh SHA BRANCH — waits for CI (.github/workflows/ci.yml) on a pushed commit and puts a red run on the board
# (r339). tools/githooks/pre-push starts it in the background for a push to develop, master or release/* that its gate
# let through: the gate lints, but ./gradlew build, the browser suite and the frame checks run only in CI, and GitHub's
# failure mail reaches nobody who works the board.
#
# On a failed run: one #build task "CI red on BRANCH", with the failed jobs, steps and the end of their log. While one
# is open, a later red run is a note on it, not a second task. A success, a cancelled run (a newer push superseded it:
# that push's own watch reports) or no run within WAIT_RUN seconds (the push was rejected) adds nothing.
# Its log: .git/ci-watch/SHA.log. Proof: tools/ci-watch-test.sh (gh and atelier stubbed).
set -uo pipefail
SHA=${1:?usage: ci-watch.sh SHA BRANCH} BRANCH=${2:?usage: ci-watch.sh SHA BRANCH}
POLL=${CI_WATCH_POLL:-30} WAIT_RUN=${CI_WATCH_WAIT_RUN:-600} WAIT_DONE=${CI_WATCH_WAIT_DONE:-5400}
AGENT=${ATELIER_AGENT:-ci-watch}
TITLE="CI red on $BRANCH"
cd "$(git rev-parse --show-toplevel)" || exit 1
say() { echo "$(date -u +%FT%TZ) $*"; }

# The push's own run: the same SHA dispatched on a ci/* branch (a break= proof, red on purpose) is not it (r342).
run() { # the newest CI run of the push of SHA to BRANCH: "id status conclusion", empty when there is none yet
	gh run list --workflow ci.yml --commit "$SHA" --branch "$BRANCH" --event push --limit 1 \
		--json databaseId,status,conclusion --jq '.[] | "\(.databaseId) \(.status) \(.conclusion)"' 2>/dev/null
}

waited=0 id="" status="" conclusion=""
while :; do
	read -r id status conclusion <<<"$(run)"
	if [ -z "$id" ] && [ "$waited" -ge "$WAIT_RUN" ]; then say "no CI run of $SHA after ${waited}s: nothing to watch"; exit 0; fi
	if [ "$waited" -ge "$WAIT_DONE" ]; then say "run $id of $SHA still $status after ${waited}s: giving up"; exit 0; fi
	[ "$status" = completed ] && break
	sleep "$POLL"; waited=$((waited + POLL))
done
say "run $id of $SHA: $conclusion"
case "$conclusion" in failure | timed_out | startup_failure) ;; *) exit 0 ;; esac

url=$(gh run view "$id" --json url --jq .url 2>/dev/null)
failed=$(gh run view "$id" --json jobs --jq '.jobs[] | select(.conclusion == "failure") |
	"  \(.name): \([.steps[] | select(.conclusion == "failure") | .name] | join(", "))"' 2>/dev/null)
# The first failed job's log (the JDKs fail alike), without its timestamps, colours and the step's env block.
logtail=$(gh run view "$id" --log-failed 2>/dev/null | awk -F'\t' 'NR == 1 { job = $1 } $1 == job' | cut -f3- \
	| sed -E 's/^\xEF\xBB\xBF//; s/^[0-9-]+T[0-9:.]+Z //; s/(\x1b|\^\[)\[[0-9;]*m//g; /^env:$/,/^##\[endgroup\]/d' | tail -n 30)
session=$(git log -1 --format='%(trailers:key=Session,valueonly)' "$SHA" 2>/dev/null)
who="$(git log -1 --format='%h %s' "$SHA" 2>/dev/null) (${session:-$(git log -1 --format=%an "$SHA" 2>/dev/null)})"
detail="What is wrong: CI ($url) failed on $BRANCH at $who.
Failed jobs and steps:
$failed

End of the failed log:
$logtail

Wanted: $BRANCH green again. Reproduce the failed step locally (./gradlew build, tools/browser-test.sh, engines/godot/tools/frame-check.sh, engines/jme/tools/frame-check.sh: whichever failed), fix it in a new commit, push.
Done when: a CI run on $BRANCH at or after the fix is green (gh run list --workflow ci.yml --branch $BRANCH).
OUT of scope: anything the log does not point at.
Discovered while: tools/ci-watch.sh watching the push of ${SHA:0:7} (r339)."

open=$(atelier list --json --tags build 2>/dev/null | python3 -c '
import json, sys
title = sys.argv[1]
print(next((r["ref"] for r in json.load(sys.stdin) if r["title"] == title), ""))
' "$TITLE")
if [ -n "$open" ]; then
	say "red again: noted on $open"
	atelier note "$open" --agent "$AGENT" "Still red: run $url at $who. Failed: $(echo "$failed" | tr '\n' ';')"
else
	say "red: adding a task"
	atelier add --agent "$AGENT" --tags build --priority 1 --detail "$detail" "$TITLE"
fi
