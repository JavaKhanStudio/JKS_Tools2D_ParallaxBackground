#!/usr/bin/env bash
# r182-sequence/stress.sh [SECONDS] — what SEQUENCE layers cost on the GPU (docs/sequence-layers.md phase 1).
# Writes engines/godot/tests/shaders/s01.jplax (Hiver) as a .plax, each parallax4 layer a 64-slot SEQUENCE of the four
# parallax4 regions, and the same page with those layers as single images, into build/r182-stress/ with
# HiverFog.atlas and its two pages, then runs the
# editor repository's ./gradlew :demo:stress --plax on each (it builds this core when checked out beside it) and prints
# both summaries. Off screen: :demo:stress renders in cage when ATELIER_AGENT or CLAUDECODE is set.
set -eu
cd "$(dirname "$0")/../.."
ROOT=$PWD EDITOR=$PWD/../JKS_Tools2D_ParallaxEditor SECONDS_RUN=${1:-10}
[ -d "$EDITOR/demo/assets" ] || { echo "needs JKS_Tools2D_ParallaxEditor beside this checkout" >&2; exit 2; }
OUT=$ROOT/build/r182-stress
rm -rf "$OUT" && mkdir -p "$OUT"
cp core/test-data/shaders/mist.png core/test-data/samples/Hiver.png "$OUT/"
sed '1s|.*|Hiver.png|' core/test-data/shaders/HiverFog.atlas > "$OUT/HiverFog.atlas"
CP=$(./gradlew -q -I tools/r178-particle-stress/classpath.gradle :core:printRuntimeClasspath | tail -1)
# Assets-relative: the stress run's working directory is the editor's demo/assets.
REL=$(realpath --relative-to="$EDITOR/demo/assets" "$OUT")
# headless: ToPlax converts a .jplax to a .plax with core's Kryo, it opens no window
java -cp "$CP" tools/r182-sequence/ToSequencePlax.java engines/godot/tests/shaders/s01.jplax "$OUT/sequence.plax" "$REL/HiverFog.atlas"
# headless: the same conversion, the control page
java -cp "$CP" tools/r182-sequence/ToSequencePlax.java engines/godot/tests/shaders/s01.jplax "$OUT/plain.plax" "$REL/HiverFog.atlas" --plain
for page in plain sequence; do
	echo "== $page"
	(cd "$EDITOR" && ./gradlew -q :demo:stress --args="--plax $REL/$page.plax --seconds $SECONDS_RUN --shot $OUT/$page.png" 2>&1) \
		| grep -v -e WARNING -e '^$' | tail -6
done
