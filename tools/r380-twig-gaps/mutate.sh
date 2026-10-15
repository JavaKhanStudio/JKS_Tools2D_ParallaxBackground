#!/usr/bin/env bash
# mutate.sh — r380: each of sky_hued's checks removed in turn from a copy of depth_page.py, and rules_test.py run on
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
no hue seed|    back = sky_hued(L, dn, centres, rgb, own, n, window, far) & ~moved|    back = np.zeros_like(moved)
band 1 too|return ((L > 1) & ~np.isnan(h_sky)|return ((L > 0) & ~np.isnan(h_sky)
plain mean, not the core|core = np.where(np.isnan(core), own, core)|core = own
not than grey|(h_sky < margin * np.minimum(h_own, grey))|(h_sky < margin * h_own)
any depth|& (dn < centres[L]))|)
hues not apart|(np.linalg.norm(hue_own - hue_sky, axis=-1) > apart)|True
as dark as it likes|& (rgb.mean(-1) >= dim * sky.mean(-1))|
MUT
exit $bad
