#!/usr/bin/env bash
# headless: runs shell unit tests, no window.
# shell-tests.sh — run every tracked tools/**/*-test.sh, each in its own process, and exit 1 if any fails (r534).
# CI's build job runs it, as it runs python-tests.sh: a new tools/*-test.sh runs there without a word. A test must exit
# non-zero on a failure and run on a CI runner: no GPU, no cage, no window. One that cannot goes in SKIP below, with the
# reason. Only tracked files: what CI sees.
#
# A *-check.sh is not a test: tools/concurrent-shots-check.sh (r172) runs two Godot or jME frame checks at once in cage
# and on the GPU, a probe kept to run by hand when tools/shots-out.sh changes; the frame jobs are out of here.
set -u
cd "$(dirname "$0")/.." || exit 1
# path → why it does not run here.
declare -A SKIP=(
	[tools/browser-test.sh]="not a unit test: the core suite in headless Chrome, CI's own step"
)
mapfile -t tests < <(git ls-files 'tools/*-test.sh' 'tools/**/*-test.sh' | sort -u)
fail=0 ran=0
for t in "${tests[@]}"; do
	if [ -n "${SKIP[$t]:-}" ]; then echo "shell-tests: SKIP $t (${SKIP[$t]})"; continue; fi
	echo "shell-tests: RUN  $t"
	ran=$((ran + 1))
	if bash "$t"; then
		echo "shell-tests: PASS $t"
	else
		echo "shell-tests: FAIL $t" >&2
		fail=1
	fi
done
[ "$ran" -gt 0 ] || { echo "shell-tests: no test file found" >&2; exit 1; }
[ "$fail" -eq 0 ] && echo "shell-tests: all $ran passed"
exit "$fail"
