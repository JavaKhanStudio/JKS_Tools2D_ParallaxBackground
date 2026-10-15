#!/usr/bin/env bash
# headless: runs each compare tool with -h and --help, no window
# check.sh — r436: each compare tool run with -h or --help prints its usage, exits 0 and writes nothing (r401's page.py
# took --help as its OUT and wrote ./--help/page/r401-compare.html). Runs them in an empty folder and fails on a file.
set -eu
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
fail=0
for tool in "python3 $root/tools/compare_page.py" "python3 $root/tools/r401-far-range/page.py" \
            "python3 $root/tools/r384-hills/compare_page.py" "$root/tools/r384-hills/compare.sh" \
            "$root/tools/r421-misty/build.sh"; do
    for flag in -h --help; do
        # shellcheck disable=SC2086
        if ! out=$($tool $flag 2>&1) || [ -z "$out" ] || [ -n "$(ls -A)" ]; then
            echo "FAIL ${tool##*/} $flag: exit, no usage, or wrote $(ls -A)"; fail=1; rm -rf ./* ./-- ./-*
        else
            echo "ok   ${tool##*/} $flag"
        fi
    done
done
exit $fail
