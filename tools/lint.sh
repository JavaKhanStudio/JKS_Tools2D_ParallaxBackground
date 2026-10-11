#!/usr/bin/env bash
# lint.sh [FILE...] — ruff on every tracked *.py, shellcheck on every *.sh, gdlint on every *.gd (r314). CI's build
# job runs it; run it before a push. Exits 1 on any finding. With FILEs, only those (tools/pre-commit-check.sh hands it
# the staged ones): a tool with none of its files is skipped.
#
# Each tool runs through uvx at a pinned version, nothing installed: a new release changes what it flags, and the
# findings would move without the code moving. Raise a pin here in a commit that fixes what the new release flags
# (ruff's in ruff.toml's required-version too: a ruff of another version refuses to run). The rules: ruff.toml,
# gdlintrc, shellcheck's defaults. Only tracked files: what CI sees, not another session's scratch.
set -u
cd "$(dirname "$0")/.." || exit 1
RUFF=0.17.0 SHELLCHECK=0.11.0.1 GDTOOLKIT=4.5.0
fail=0
lint() { # NAME CMD...
	local name="$1"; shift
	if "$@"; then echo "lint: $name OK"; else echo "lint: $name FAILED" >&2; fail=1; fi
}
if [ $# -eq 0 ]; then
	mapfile -t py < <(git ls-files '*.py')
	mapfile -t sh < <(git ls-files '*.sh')
	mapfile -t gd < <(git ls-files '*.gd')
else
	py=() sh=() gd=()
	for f in "$@"; do
		case "$f" in *.py) py+=("$f") ;; *.sh) sh+=("$f") ;; *.gd) gd+=("$f") ;; esac
	done
fi
# With -x, shellcheck follows a `. tools/x.sh` (from this directory, the checkout's top): a staged lab launcher linted alone
# would otherwise fail SC1091 on the guard it sources (r340).
# An empty list would lint the whole directory (ruff) or fail on no input.
[ ${#py[@]} -eq 0 ] || lint ruff uvx "ruff@$RUFF" check --quiet "${py[@]}"
[ ${#sh[@]} -eq 0 ] || lint shellcheck uvx --from "shellcheck-py==$SHELLCHECK" shellcheck -x "${sh[@]}"
[ ${#gd[@]} -eq 0 ] || lint gdlint uvx --from "gdtoolkit==$GDTOOLKIT" gdlint "${gd[@]}"
exit $fail
