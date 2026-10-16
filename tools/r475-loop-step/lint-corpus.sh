#!/usr/bin/env bash
# lint-corpus.sh LIST OUT — tools/parallax_lab.py lint on every page LIST names (one path a line), each page's
# faults on lines "<page> <fault>" in OUT, for a before/after diff of a lint change (r475). No window, no GPU.
set -u
cd "$(dirname "$0")/../.." || exit 1
: > "$2"
while read -r page; do
	python3 tools/parallax_lab.py lint "$page" 2>&1 | sed -n 's/^ *! //p' | sed "s|^|$page |" >> "$2"
done < "$1"
wc -l < "$2"
