#!/usr/bin/env bash
# lab-mutate.sh OUT [SECONDS] — proves lab-check.sh sees a wrong cover (r369).
#
#   tools/r369-lurker-cover/lab-mutate.sh build/r369/lab-mutate
#
# Copies the built war to OUT/war, slides every interval of the lab page's lurkerCover 10 world units along its layer
# (same widths, wrong place), and runs lab-check.sh on it: it must FAIL, a covered step showing the figure. Run
# lab-check.sh once first (it builds the war).
# headless: lab-check.sh drives Chrome headless, no window
set -eu
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: lab-mutate.sh OUT [SECONDS]}")"
SECONDS_RUN="${2:-30}"
rm -rf "$OUT/war"
mkdir -p "$OUT"
cp -r core/build/browserTest/war "$OUT/war"
python3 - "$OUT"/war/assets/lurker-lab/lab-*.jplax <<'PY'
import json, sys
for path in sys.argv[1:]:
    with open(path) as f:
        page = json.load(f)
    for layer in page['pageModel']['pageList']:
        cover = layer.get('lurkerCover') or []
        for k in range(0, len(cover), 3):
            cover[k + 1] += 10
            cover[k + 2] += 10
    with open(path, 'w') as f:
        json.dump(page, f)
    # the preloader checks each asset's length against assets.txt
    name = path.split('/assets/', 1)[1]
    listing = path.split('/assets/', 1)[0] + '/assets/assets.txt'
    with open(listing) as f:
        lines = f.read().split('\n')
    for i, line in enumerate(lines):
        fields = line.split(':')
        if len(fields) > 3 and fields[2] == name:
            fields[3] = str(len(open(path, 'rb').read()))
            lines[i] = ':'.join(fields)
    with open(listing, 'w') as f:
        f.write('\n'.join(lines))
PY
if LAB_WAR="$OUT/war" tools/r369-lurker-cover/lab-check.sh "$OUT/check" "$SECONDS_RUN" --no-build > "$OUT/check.log" 2>&1; then
	echo "FAIL: lab-check.sh passed a cover slid 10 units: it sees nothing (log: $OUT/check.log)"
	exit 1
fi
grep -h 'a covered step shows' "$OUT/check/report.json" || true
echo "OK: lab-check.sh fails a wrong cover"
