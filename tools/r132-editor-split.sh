#!/usr/bin/env bash
# r132-editor-split.sh OUT — how JavaKhanStudio/JKS_Tools2D_ParallaxEditor's history was made (repo split phase 2,
# docs/repo-split.md): develop of this checkout, filtered to the editor, the demo and their tools, written into a new
# repository at OUT. Neither this checkout nor its clone is rewritten: git filter-repo reads a scratch clone
# (--source) and writes OUT (--target), so the amend guard has nothing to refuse.
#
# The editor repository's own files (settings.gradle with the includeBuild, CI, README, RELEASING.md, AGENTS.md) were
# committed on top of this history there, in its first commit of its own. Needs uv (uvx git-filter-repo).
set -euo pipefail
OUT=${1:?usage: tools/r132-editor-split.sh OUT}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
[ -e "$OUT" ] && { echo "$OUT exists" >&2; exit 2; }
SRC=$(mktemp -d)
trap 'rm -rf "$SRC"' EXIT

git clone -q --no-local --branch develop "$ROOT" "$SRC"
git init -q -b develop "$OUT"
# test/assets/Hiver*: the demo's Hiver page and atlas lived there before demo/assets (their renames stay followed).
uvx git-filter-repo --source "$SRC" --target "$OUT" --refs develop --paths-from-file /dev/stdin <<'EOF'
editor/
demo/
test/assets/Hiver.atlas
test/assets/Hiver.png
test/assets/hiver/
gradle/offscreen.gradle
gradle/wrapper/
gradlew
gradlew.bat
LICENSE
NOTICE
.gitignore
.gitattributes
tools/demo-icon-probe.sh
tools/demo-shots.sh
tools/driver-probe.sh
tools/editor-stress.txt
tools/flatten-exports.sh
tools/nested-x.sh
tools/offscreen.sh
tools/parallax-lab-keys-probe.sh
tools/parallax-lab-lint-all.sh
tools/parallax_lab.py
tools/parallax_lab_test.py
tools/parallax_regions.py
tools/showcase_pages.py
tools/start-demo.sh
tools/stress-project.py
tools/r114-atlas-check.py
tools/r114-flatten-export.txt
tools/r125-etc2-check.py
tools/r125-etc2-export.txt
tools/r126-pixel-art-check.py
tools/r126-pixel-art.txt
tools/r159-every-tab.txt
tools/r160-change-listeners.txt
tools/r33-textures-tab.txt
tools/r34-color-picker.txt
tools/r35-background-wheel.txt
tools/r36-tab-reclick.txt
tools/r40-mipmap-export.txt
tools/r40-mipmap-stress.sh
tools/r41-stripped-shot.sh
tools/r53-export-place.txt
tools/r53-seam.py
tools/r74-probe-race.sh
EOF
git -C "$OUT" gc -q --prune=now --aggressive
echo "$OUT: $(git -C "$OUT" rev-list --count HEAD) commits, $(du -sh "$OUT/.git" | cut -f1)"
