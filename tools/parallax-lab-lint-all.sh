#!/usr/bin/env bash
# Lints every page the lab reads, in a fixed order, to one stream (r155): run it before and after a change to
# tools/parallax_lab.py's lint and compare the two outputs byte for byte (cmp).
set -uo pipefail  # lint exits 1 on a page with faults: go on to the next
cd "$(dirname "$0")/.." || exit
for f in demo/lab/round*/*.jplax demo/showcase/*.jplax demo/assets/*/*.plaxpj editor/Files/*/*.plaxpj; do
    [ -e "$f" ] || continue
    echo "== $f"
    python3 tools/parallax_lab.py lint "$f" 2>&1
done
