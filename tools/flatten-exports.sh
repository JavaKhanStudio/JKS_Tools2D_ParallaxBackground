#!/usr/bin/env bash
# r161: every file the three flatten scripts export (r114 sizes, r126 pixel art, r125 ETC2), and their checkers'
# reports, into one folder, so two editor builds can be compared byte for byte:
#   ./gradlew :editor:installDist, copy editor/build/install/ParallaxEditor to editor/build/r161/before-install, then
#   tools/flatten-exports.sh editor/build/r161/before-install editor/build/r161/before
#   (change the code, installDist, copy to after-install, run again into after)
#   diff -r editor/build/r161/before editor/build/r161/after
# The scripts' screenshots (*-before, *-reopened, 1-soft...) are left out: they are the preview, not the export.
set -u
cd "$(dirname "$0")/.." || exit 1
[ $# = 2 ] || { echo "usage: $0 EDITOR_INSTALL_DIR OUT_DIR"; exit 2; }
INSTALL=$(realpath "$1"); OUT=$2
rm -rf "$OUT"; mkdir -p "$OUT"
status=0
for r in r114:flatten-export r126:pixel-art r125:etc2-export; do
	id=${r%%:*}; script=tools/$id-${r#*:}.txt
	rm -rf "editor/build/$id/out"
	DRIVER_PORT=47961 EDITOR_BIN="$INSTALL/bin/ParallaxEditor" tools/driver-probe.sh < "$script" > "$OUT/$id-driver.log" 2>&1 \
		|| { echo "$id: driver failed, $OUT/$id-driver.log"; status=1; }
	mkdir -p "$OUT/$id"
	# The export: atlases, their pages, pages in ETC2, pages and projects; not the preview shots.
	find "editor/build/$id/out" -maxdepth 1 -type f ! -name '*-before.png' ! -name '*-reopened.png' ! -name '[0-9]-*.png' \
		-exec cp {} "$OUT/$id/" \;
done
python3 tools/r114-atlas-check.py editor/build/r114/out/city.atlas editor/build/r114/out/hiver.atlas > "$OUT/r114-check.txt" 2>&1
python3 tools/r126-pixel-art-check.py editor/build/r126/out > "$OUT/r126-check.txt" 2>&1
python3 tools/r125-etc2-check.py editor/build/r125/out/city.atlas editor/build/r125/out/hiver.atlas > "$OUT/r125-check.txt" 2>&1
exit $status
