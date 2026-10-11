#!/usr/bin/env bash
# refacto-scan.sh — the refacto day's measures over the TRACKED files only (r296): another session's
# uncommitted tools must not land in the counts. Prints each tool's findings by rule; changes nothing.
# Needs uvx and npx. jscpd 5 has no --gitignore: it is handed the tracked files instead.
cd "$(dirname "$0")/.." || exit 1
mapfile -t SH < <(git ls-files '*.sh')
mapfile -t PY < <(git ls-files '*.py')
mapfile -t CODE < <(git ls-files core engines shots tools | grep -E '\.(java|py|sh|gd|js|frag|vert|glsl|gdshader|html|css)$')

echo "== shellcheck, by rule"
uvx --from shellcheck-py shellcheck -f gcc "${SH[@]}" | grep -oE '\[SC[0-9]+\]' | sort | uniq -c | sort -rn
echo "== ruff, by rule"
uvx ruff check --statistics "${PY[@]}"
echo "== ruff complexity"
uvx ruff check --select C901,PLR0912,PLR0915 --output-format concise "${PY[@]}"
echo "== gdlint (gdlintrc)"
uvx --from 'gdtoolkit==4.*' gdlint . 2>&1 | grep -oE '\([a-z-]+\)$' | sort | uniq -c
echo "== jscpd, min 10 lines"
npx --yes jscpd --min-lines 10 --reporters console "${CODE[@]}" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | tail -25
echo "== longest files"
printf '%s\n' "${CODE[@]}" | xargs wc -l | sort -rn | sed -n '2,16p'
echo "== TODO/FIXME, skipped tests"
git grep -nE 'TODO|FIXME|XXX|HACK' -- ':!*.md' ':!core/test-data'
git grep -nE '@Disabled|@Ignore' -- '*.java'
exit 0
