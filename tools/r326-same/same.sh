#!/usr/bin/env bash
# same.sh TREE PAINTING OUT [GALLERY_ROOT...] — r326's proof that splitting depth_page.py's main and bands, and
# gallery.py's main, changed nothing.
#
#   git worktree add --detach /tmp/before HEAD
#   tools/r326-same/same.sh /tmp/before build/r279/paint1-noloop.png /tmp/r326-a build/ai
#   tools/r326-same/same.sh . build/r279/paint1-noloop.png /tmp/r326-b build/ai
#   diff -r /tmp/r326-a /tmp/r326-b && echo SAME
#
# Runs TREE's depth_page.py on PAINTING (seeded, --no-shots; a painting whose join shows takes the loop pass too),
# then again with --keep and another --front-speed (the page laid out again, no repaint), then TREE's gallery.py over
# the GALLERY_ROOTs and that output. OUT gets the sha256 of every file written but the logs, the exit codes, and
# stdout/stderr with tqdm's speeds taken out: two runs diff equal when the scripts do the same thing. The page and
# the recipe hold the folder they were written to, so every run writes to one scratch folder, WORK (one run at a
# time), and round.json's path to it from TREE is taken out before hashing.
# Needs ComfyUI's venv (COMFYUI_DIR), its dreamshaper_8Inpainting checkpoint and ~2 GB of VRAM; ~2 min.
set -u
TREE=$(cd "$1" && pwd); PAINTING=$(realpath "$2"); OUT=$(realpath -m "$3"); shift 3
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
WORK="${TMPDIR:-/tmp}/r326-same.work"
rm -rf "$OUT" "$WORK"; mkdir -p "$OUT" "$WORK"
REL=$(realpath --relative-to="$TREE" "$WORK")  # the page's folder as round.json and stdout name it: from TREE
REL=${REL//./\\.}  # a sed pattern: its dots are dots
run() { # NAME CMD...
	local name="$1"; shift
	"$@" 2> "$OUT/$name.err" > "$OUT/$name.out"
	echo "exit $?" >> "$OUT/$name.err"
	sed -i -e "s#$REL/##g" -e "s#$WORK/##g" -e "s#$TREE/##g" -e '/it\/s\]/d' -e '/s\/it\]/d' "$OUT/$name.err" "$OUT/$name.out"
}
D=(env PYTHONHASHSEED=0 "$PY" "$TREE/tools/r279-depth-page/depth_page.py")
run depth "${D[@]}" "$PAINTING" "$WORK/depth" --layers 4 --seed 3 --theme "green valley, pines" --no-shots
cp "$WORK/depth/recipe.json" "$WORK/depth-recipe-first.json"
run keep "${D[@]}" "$PAINTING" "$WORK/depth" --layers 4 --seed 3 --theme "green valley, pines" --no-shots \
	--keep --front-speed 0.1
run gallery python3 "$TREE/tools/r239-ai-strips/gallery.py" "$WORK/gallery" "$@" "$WORK/depth"
sed -i "s#$REL/##g" "$WORK/depth/page/round.json"
(cd "$WORK" && find . -type f ! -name '*.log' -print0 | sort -z | xargs -0 sha256sum) > "$OUT/sha256.txt"
rm -r "$WORK"
