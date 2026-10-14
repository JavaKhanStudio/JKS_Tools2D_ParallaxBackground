#!/usr/bin/env bash
# lurker-cover.sh PAGE.jplax [ATLAS_DIR] [WORLD_W WORLD_H] — bakes every LURKER layer's lurkerCover into PAGE (r369).
#
#   tools/lurker-cover.sh build/r363/round/v01.jplax
#
# core's LurkerCover (docs/lurker-layers.md, "Where it is covered") swept over the page's atlas, read from disk
# (tools/lurker-cover/LurkerBake.java, no GL), in a world WORLD_W x WORLD_H (40 x 22.5 by default: a 16:9 heart's).
# ATLAS_DIR defaults to the page's folder. The page is rewritten with its cover; nothing else in it changes.
# LURKER_OPAQUE and LURKER_MARGIN bake with another alpha and margin than LurkerCover's, to measure them.
# LURKER_TRACK="SPEED FPS FRAMES" also prints, a film frame a line, whether the figure is covered (see LurkerBake).
set -eu
cd "$(dirname "$0")/.." || exit 1
PAGE="$1" ATLAS_DIR="${2:-$(dirname "$1")}" WORLD_W="${3:-40}" WORLD_H="${4:-22.5}"
CP=$(./gradlew -q -I tools/r178-particle-stress/classpath.gradle :core:printRuntimeClasspath | tail -1)
# shellcheck disable=SC2086 # LURKER_TRACK is three words
# headless: LurkerBake reads the atlas with ImageIO and draws nothing, it opens no window
OUT=$(java -Djava.awt.headless=true -Dlurker.opaque="${LURKER_OPAQUE:-250}" -Dlurker.margin="${LURKER_MARGIN:-0.04}" -cp "$CP" tools/lurker-cover/LurkerBake.java "$PAGE" "$ATLAS_DIR" "$WORLD_W" "$WORLD_H" \
	${LURKER_TRACK:+--track $LURKER_TRACK})
python3 - "$PAGE" "$(head -1 <<<"$OUT")" <<'PY'
import json, sys
path, covers = sys.argv[1], json.loads(sys.argv[2])
with open(path) as f:
    page = json.load(f)
for index, cover in covers.items():
    page['pageModel']['pageList'][int(index)]['lurkerCover'] = cover
with open(path, 'w') as f:
    json.dump(page, f, indent=1)
print(f'{path}: ' + ', '.join(f'layer {i}: {len(c) // 3} intervals' for i, c in covers.items()))
PY
tail -n +2 <<<"$OUT"
