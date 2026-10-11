#!/usr/bin/env bash
# pre-commit-check.sh — the git pre-commit gate (r339): CI's lint on the .py, .sh and .gd files a commit stages, before
# the commit is made. The pre-push gate (r332) refuses the same faults, but only after the commit exists: fixing it then
# means another commit or an amend, and a refused push. Here the commit never forms.
#
# Wired by `git config core.hooksPath tools/githooks` (tools/githooks/pre-commit execs this; the board's hook proxy
# runs it after its own). The staged versions are written to a scratch tree with their modes (ruff's EXE001 reads the
# mode) beside ruff.toml and gdlintrc, and linted there with tools/lint.sh's pinned tools: what the commit holds, not
# the working tree. A staged tools/*.sh also goes through tools/offscreen-lint.sh. A commit staging none costs nothing.
# Skip once: git commit --no-verify (the pre-push gate still runs). Proof: tools/pre-commit-check-test.sh.
set -uo pipefail
unset GIT_PREFIX
HERE=$(cd "$(dirname "$0")" && pwd) || exit 1
mapfile -t files < <(git diff --cached --name-only --diff-filter=ACMR -- '*.py' '*.sh' '*.gd')
[ ${#files[@]} -gt 0 ] || exit 0
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT
mapfile -t configs < <(git ls-files -- ruff.toml gdlintrc)
git checkout-index --prefix="$tmp/" -- "${files[@]}" "${configs[@]}" || {
	echo "pre-commit: cannot write the staged files out to lint them: refusing (fails closed)." >&2
	exit 1
}
abs=() offs=()
for f in "${files[@]}"; do
	abs+=("$tmp/$f")
	case "$f" in tools/*.sh) offs+=("$tmp/$f") ;; esac
done
fail=0
out=$("$HERE/lint.sh" "${abs[@]}" 2>&1) || fail=1
if [ ${#offs[@]} -gt 0 ]; then
	out+=$'\n'$("$HERE/offscreen-lint.sh" "${offs[@]}" 2>&1) || fail=1
fi
if [ $fail -ne 0 ]; then
	printf '%s\n' "${out//$tmp\//}" >&2
	echo "pre-commit: refusing this commit: CI's lint fails on what it stages (above)." >&2
	echo "  Fix the files, git add them, and commit again. CI and the pre-push gate run the same lint." >&2
fi
exit $fail
