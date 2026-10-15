#!/usr/bin/env bash
# headless: runs Python unit tests, no window.
# mutate.sh — prove tools/python-tests.sh fails when one of the tests it gathers fails (r411): in a throwaway worktree
# of HEAD, turn LeftOutFields' left-out flipX default from False to True, and want python-tests.sh to exit 1 and name
# tools/parallax_lab_test.py. The same edit on a ci/* branch turns CI's build job red.
set -u
cd "$(dirname "$0")/../.." || exit 1
wt=$(mktemp -d)
trap 'git worktree remove --force "$wt" >/dev/null 2>&1; rm -rf "$wt"' EXIT
git worktree add -q --detach "$wt" HEAD || exit 1
sed -i 's/self.assertEqual((0.0, 0.0, False, 0.0, 0.0)/self.assertEqual((0.0, 0.0, True, 0.0, 0.0)/' \
	"$wt/tools/parallax_lab_test.py"
git -C "$wt" diff --quiet && { echo "mutate: the edit did not apply" >&2; exit 1; }
out=$("$wt/tools/python-tests.sh" 2>&1); code=$?
echo "$out" | grep '^python-tests:'
if [ "$code" -ne 0 ] && echo "$out" | grep -q 'FAIL tools/parallax_lab_test.py'; then
	echo "mutate: PASS (a failing LeftOutFields fails the run)"
else
	echo "mutate: FAIL (python-tests.sh exited $code on a broken LeftOutFields)" >&2; exit 1
fi
