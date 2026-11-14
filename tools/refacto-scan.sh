#!/usr/bin/env bash
# refacto-scan.sh — the refacto day's measures over the TRACKED files only (r296): another session's
# uncommitted tools must not land in the counts. Prints each tool's findings by rule; changes nothing.
# Needs uvx and npx. jscpd 5 has no --gitignore: it is handed the tracked files instead.
# The linters run at tools/lint.sh's pinned versions (r524): an unpinned ruff refuses ruff.toml's required-version and
# measures nothing, and another shellcheck or gdtoolkit counts other rules than CI's.
cd "$(dirname "$0")/.." || exit 1
eval "$(grep -m1 '^RUFF=' tools/lint.sh)"
mapfile -t SH < <(git ls-files '*.sh')
mapfile -t PY < <(git ls-files '*.py')
mapfile -t GD < <(git ls-files '*.gd')
mapfile -t CODE < <(git ls-files core engines shots tools | grep -E '\.(java|py|sh|gd|js|frag|vert|glsl|gdshader|html|css)$')

echo "== shellcheck, by rule"
uvx --from "shellcheck-py==$SHELLCHECK" shellcheck -x -f gcc "${SH[@]}" | grep -oE '\[SC[0-9]+\]' | sort | uniq -c | sort -rn
echo "== ruff, by rule"
uvx "ruff@$RUFF" check --statistics "${PY[@]}"
echo "== ruff complexity"
uvx "ruff@$RUFF" check --select C901,PLR0912,PLR0915 --output-format concise "${PY[@]}"
echo "== gdlint (gdlintrc)"
uvx --from "gdtoolkit==$GDTOOLKIT" gdlint "${GD[@]}" 2>&1 | grep -oE '\([a-z-]+\)$' | sort | uniq -c
echo "== jscpd, min 10 lines"
npx --yes jscpd --min-lines 10 --reporters console "${CODE[@]}" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | tail -25
echo "== longest files"
printf '%s\n' "${CODE[@]}" | xargs wc -l | sort -rn | sed -n '2,16p'
echo "== TODO/FIXME, skipped tests"
git grep -nwE 'TODO|FIXME|XXX|HACK' -- ':!*.md' ':!core/test-data' ':!tools/refacto-scan.sh'
git grep -nE '@Disabled|@Ignore' -- '*.java'
exit 0
