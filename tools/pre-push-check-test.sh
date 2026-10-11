#!/usr/bin/env bash
# pre-push-check-test.sh — proves tools/pre-push-check.sh (r332) on a scratch clone of HEAD: a commit carrying r276's
# fault (a .py with a shebang and no executable bit, ruff EXE001) is refused on develop, passes once executable, and
# a ci/ branch passes unchecked. Exit 0 when all three hold.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd) || exit 1
CHECK="$HERE/pre-push-check.sh"
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT
git clone -q "$HERE/.." "$tmp/c" || exit 1
cd "$tmp/c" || exit 1
git -c user.name=t -c user.email=t@t commit -q --allow-empty -m base
printf '#!/usr/bin/env python3\nprint(1)\n' > tools/r332_probe.py
chmod 644 tools/r332_probe.py
git add tools/r332_probe.py && git -c user.name=t -c user.email=t@t commit -q -m bad
bad=$(git rev-parse HEAD)
git update-index --chmod=+x tools/r332_probe.py && chmod +x tools/r332_probe.py
git -c user.name=t -c user.email=t@t commit -q -m good
good=$(git rev-parse HEAD)
zero=0000000000000000000000000000000000000000
fails=0
expect() { # WANT NAME SHA REMOTE-REF
	local got=0
	echo "refs/heads/x $3 $4 $zero" | "$CHECK" >/dev/null 2>&1 || got=$?
	if [ "$got" = "$1" ]; then echo "ok: $2"; else echo "FAIL: $2 (exit $got, want $1)"; fails=1; fi
}
expect 1 "develop refuses a non-executable shebang .py" "$bad" refs/heads/develop
expect 0 "develop takes it once executable" "$good" refs/heads/develop
expect 0 "a ci/ branch passes unchecked" "$bad" refs/heads/ci/r332
[ -z "$(git worktree list | sed 1d)" ] || { echo "FAIL: a worktree was left behind"; fails=1; }
exit $fails
