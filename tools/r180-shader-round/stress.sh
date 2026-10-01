#!/usr/bin/env bash
# r180-shader-round/stress.sh [SECONDS] — what SHADER layers cost on the GPU (docs/effect-layers.md phase 3).
# Writes engines/godot/tests/shaders/s01.jplax (Hiver: two WAVE layers, one FOG mist) as a .plax, and the same page
# with those layers drawn as plain images, into build/r180-stress/ with HiverFog.atlas and its two pages, then runs the
# editor repository's ./gradlew :demo:stress --plax on each (it builds this core when checked out beside it) and prints
# both summaries. Off screen: :demo:stress renders in cage when ATELIER_AGENT or CLAUDECODE is set.
set -eu
cd "$(dirname "$0")/../.."
ROOT=$PWD EDITOR=$PWD/../JKS_Tools2D_ParallaxEditor SECONDS_RUN=${1:-10}
[ -d "$EDITOR/demo/assets" ] || { echo "needs JKS_Tools2D_ParallaxEditor beside this checkout" >&2; exit 2; }
OUT=$ROOT/build/r180-stress
rm -rf "$OUT" && mkdir -p "$OUT"
cp core/test-data/shaders/mist.png core/test-data/samples/Hiver.png "$OUT/"
sed '1s|.*|Hiver.png|' core/test-data/shaders/HiverFog.atlas > "$OUT/HiverFog.atlas"
CP=$(./gradlew -q -I tools/r178-particle-stress/classpath.gradle :core:printRuntimeClasspath | tail -1)
# Assets-relative: the stress run's working directory is the editor's demo/assets.
REL=$(realpath --relative-to="$EDITOR/demo/assets" "$OUT")
# headless: ToPlax converts a .jplax to a .plax with core's Kryo, it opens no window
java -cp "$CP" tools/r180-shader-round/ToPlax.java engines/godot/tests/shaders/s01.jplax "$OUT/shaded.plax" "$REL/HiverFog.atlas"
# headless: the same conversion, the control page
java -cp "$CP" tools/r180-shader-round/ToPlax.java engines/godot/tests/shaders/s01.jplax "$OUT/plain.plax" "$REL/HiverFog.atlas" --plain
for page in plain shaded; do
	echo "== $page"
	(cd "$EDITOR" && ./gradlew -q :demo:stress --args="--plax $REL/$page.plax --seconds $SECONDS_RUN --shot $OUT/$page.png" 2>&1) \
		| grep -v -e WARNING -e '^$' | tail -6
done
