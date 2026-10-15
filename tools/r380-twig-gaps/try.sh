#!/usr/bin/env bash
# try.sh [NAME] — r380: the cached paintings' bands with this checkout's depth_page.py, counted against HEAD's
# (build/r380/head/<p>, from bands.py with a HEAD worktree's depth_page.py), and their changed pixels drawn magenta.
# headless: no window, numpy only
set -u
cd "$(dirname "$0")/../.." || exit 1
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
OUT="build/r380/${1:-try}"
for p in valley river noloop; do
	"$PY" tools/r380-twig-gaps/bands.py bands "build/r380/cache/$p.npz" "$OUT/$p" || exit 1
	echo "$p"
	"$PY" tools/r380-twig-gaps/counts.py "build/r380/head/$p" "$OUT/$p" --diff "$OUT/diff-$p.png"
done
