#!/usr/bin/env bash
# pre-push-check.sh — the git pre-push gate (r332): CI's lint steps, run on the exact commit a push sends to develop,
# master or release/*, before it leaves. Refuses the push on a finding, so a red lint never reaches develop.
#
# Wired by `git config core.hooksPath tools/githooks` in a clone (tools/githooks/pre-push execs this; the board's
# own hook proxy runs it after its checks). Reads git's pre-push lines on stdin: <local ref> <sha> <remote ref> <sha>.
# Each sha is checked out alone in a throwaway worktree: what CI sees, its file modes included (ruff's EXE001 reads
# the mode, r276), not this checkout's dirty files nor another session's. ~3 s a push.
#
# Not run here: ./gradlew build and tools/browser-test.sh (minutes each): run them yourself before a push.
# Other branches (ci/*, a scratch one) pass unchecked: a ci/ branch may be red on purpose. Skip once: git push --no-verify.
set -uo pipefail
# git exports these to a hook: left set, the worktree's `git ls-files` would read this checkout's index.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_PREFIX
ROOT=$(git rev-parse --show-toplevel) || exit 1 # a hook runs at the top of the checkout pushing
fail=0
while read -r _lref lsha rref _rsha; do
	case "$lsha" in *[!0]*) ;; *) continue ;; esac # a delete sends nothing
	case "$rref" in refs/heads/develop | refs/heads/master | refs/heads/release/*) ;; *) continue ;; esac
	tmp=$(mktemp -d) || exit 1
	if git -C "$ROOT" worktree add -q --detach "$tmp/w" "$lsha" >/dev/null 2>&1; then
		echo "pre-push: lint of ${lsha:0:7} for ${rref#refs/heads/}" >&2
		out=$({ "$tmp/w/tools/lint.sh" && "$tmp/w/tools/offscreen-lint.sh"; } 2>&1) || {
			printf '%s\n' "$out" >&2
			echo "pre-push: refusing the push to ${rref#refs/heads/}: CI's lint fails on ${lsha:0:7} (above)." >&2
			echo "  Fix it in a new commit (or amend yours if unpushed), then push again." >&2
			fail=1
		}
		git -C "$ROOT" worktree remove --force "$tmp/w" >/dev/null 2>&1
	else
		echo "pre-push: cannot check out ${lsha:0:7} to lint it: refusing (fails closed)." >&2
		fail=1
	fi
	rm -rf "$tmp"
done
exit $fail
