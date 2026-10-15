#!/usr/bin/env bash
# mutate.sh — r386: each part of foot_of's hidden foot removed in turn from a copy of depth_page.py, and rules_test.py
# run on it: every mutant must FAIL. The checkout's depth_page.py is never touched. ComfyUI venv (numpy, scipy).
# headless: unit tests, no window
set -u
cd "$(dirname "$0")/../.." || exit 1
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
cp tools/r279-depth-page/depth_page.py tools/r279-depth-page/rules_test.py "$T/"
ln -s "$PWD/tools" "$T/tools"
sed -i "s|^HERE = .*|HERE = '$PWD/tools/r279-depth-page'|" "$T/depth_page.py"
"$PY" "$T/rules_test.py" >/dev/null 2>&1 || { echo "rules_test.py fails unmutated"; exit 1; }
cp "$T/depth_page.py" "$T/clean.py"
bad=0
while IFS='|' read -r name from to; do
	cp "$T/clean.py" "$T/depth_page.py"
	FROM="$from" TO="$to" python3 -c 'import os, sys; p = sys.argv[1]; s = open(p).read(); assert os.environ["FROM"] in s, os.environ["FROM"]
s = s.replace(os.environ["FROM"], os.environ["TO"]); open(p, "w").write(s)' "$T/depth_page.py" || { echo "$name: not applied"; bad=1; continue; }
	if "$PY" "$T/rules_test.py" >/dev/null 2>&1; then echo "$name: PASSED (a check no test needs)"; bad=1; else echo "$name: fails, as it must"; fi
done <<'MUT'
no halo|    seen = foot & ~grow(L > on[1], HALO)|    seen = foot
hidden judged on its foot|        return int(np.bincount(L[m], minlength=n).argmax())|        return int(np.bincount(L[foot], minlength=n).argmax())
judged on the whole foot|    return int(np.bincount(L[seen], minlength=n).argmax())|    return int(np.bincount(L[foot], minlength=n).argmax())
MUT
exit $bad
