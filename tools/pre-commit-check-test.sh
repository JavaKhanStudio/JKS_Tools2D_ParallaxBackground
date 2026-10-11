#!/usr/bin/env bash
# pre-commit-check-test.sh — proves tools/pre-commit-check.sh (r339) on a scratch clone of HEAD: a staged .py with a
# shebang and no executable bit (ruff EXE001, r276's fault) is refused, even when the working tree's copy is fixed
# (it lints what the commit holds); it passes once staged executable; a staged tools/*.sh that runs java outside cage
# is refused (offscreen-lint); a commit staging no .py/.sh/.gd passes; a staged lab launcher that sources
# tools/agent-window-guard.sh passes alone (shellcheck follows the source, r340). Exit 0 when all hold.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd) || exit 1
CHECK="$HERE/pre-commit-check.sh"
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT
git clone -q "$HERE/.." "$tmp/c" || exit 1
cd "$tmp/c" || exit 1
fails=0
expect() { # WANT NAME
	local got=0
	"$CHECK" >/dev/null 2>&1 || got=$?
	if [ "$got" = "$1" ]; then echo "ok: $2"; else echo "FAIL: $2 (exit $got, want $1)"; fails=1; fi
}
echo 'x' >> README.md && git add README.md
expect 0 "a commit staging no .py/.sh/.gd passes"
echo '# r340' >> tools/browser-test.sh && git add tools/browser-test.sh
expect 0 "a staged tools/browser-test.sh, which sources agent-window-guard.sh, passes alone"
git reset -q
printf '#!/usr/bin/env python3\nprint(1)\n' > tools/r339_probe.py
chmod 644 tools/r339_probe.py && git add tools/r339_probe.py
chmod +x tools/r339_probe.py
expect 1 "a staged non-executable shebang .py is refused, the working tree's chmod notwithstanding"
git update-index --chmod=+x tools/r339_probe.py
expect 0 "it passes once staged executable"
printf '#!/usr/bin/env bash\njava -jar x.jar\n' > tools/r339_probe.sh
chmod +x tools/r339_probe.sh && git add tools/r339_probe.sh
expect 1 "a staged tools/*.sh running java outside cage is refused"
exit $fails
