#!/usr/bin/env bash
# mutate.sh — r378: each of sky_twigs' checks removed in turn from a copy of depth_page.py, and rules_test.py run on
# it: every mutant must FAIL. The checkout's depth_page.py is never touched. ComfyUI venv (numpy, scipy).
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
no cloud-gap check|if np.percentile(lum[piece], 10) > np.percentile(lum[ring], 10) - dark / 2:|if False:
no width check|if width[piece].max() > half:|if False:
no sky ring|if not ring.any() or (dn[ring] <= SKY).mean() < sky_share:|if not ring.any():
not only by the hole|for i in np.unique(comp[d & grow(hole, 2)]):|for i in np.unique(comp[d]):
any layer|("the back layer", lambda c: c.get("back", c["k"] == 0))|("the back layer", lambda c: True)
any theme|re.search(r"\b(tree|pine|oak|forest|wood|jungle|palm)",|re.search(r"",
MUT
exit $bad
