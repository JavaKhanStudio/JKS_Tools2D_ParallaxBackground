#!/usr/bin/env bash
# r178-particle-stress/run.sh [SECONDS] — what a PARTICLES layer costs on the GPU (docs/effect-layers.md phase 1).
# Writes core/test-data/particles/p01.jplax (Hiver, snow-wide.p from the view: up to 690 flakes) as a .plax, and the
# same page without its particle layer, into build/r178-stress/ with HiverSnow.atlas and the .p, then runs the editor
# repository's ./gradlew :demo:stress --plax on each (it builds this core when checked out beside it) and prints both
# summaries. Off screen: :demo:stress renders in cage when ATELIER_AGENT or CLAUDECODE is set.
set -eu
cd "$(dirname "$0")/../.."
ROOT=$PWD EDITOR=$PWD/../JKS_Tools2D_ParallaxEditor SECONDS_RUN=${1:-10}
[ -d "$EDITOR/demo/assets" ] || { echo "needs JKS_Tools2D_ParallaxEditor beside this checkout" >&2; exit 2; }
OUT=$ROOT/build/r178-stress
rm -rf "$OUT" && mkdir -p "$OUT"
cp core/test-data/particles/snowflake.png core/test-data/particles/snow-wide.p "$OUT/"
cp core/test-data/samples/Hiver.png "$OUT/"
sed '1s|.*|Hiver.png|' core/test-data/particles/HiverSnow.atlas > "$OUT/HiverSnow.atlas"
CP=$(./gradlew -q -I tools/r178-particle-stress/classpath.gradle :core:printRuntimeClasspath | tail -1)
# Assets-relative: the stress run's working directory is the editor's demo/assets.
REL=$(realpath --relative-to="$EDITOR/demo/assets" "$OUT")
java -cp "$CP" tools/r178-particle-stress/ToPlax.java core/test-data/particles/p01.jplax "$OUT/snow.plax" "$REL/HiverSnow.atlas"
java -cp "$CP" tools/r178-particle-stress/ToPlax.java core/test-data/particles/p01.jplax "$OUT/none.plax" "$REL/HiverSnow.atlas" --no-particles
for page in none snow; do
	echo "== $page"
	(cd "$EDITOR" && ./gradlew -q :demo:stress --args="--plax $REL/$page.plax --seconds $SECONDS_RUN --shot $OUT/$page.png" 2>&1) \
		| grep -v -e WARNING -e '^$' | tail -6
done
