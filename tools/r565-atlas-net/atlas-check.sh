#!/usr/bin/env bash
# atlas-check.sh (r565, r567): a wide net over the atlas parsers. Every tracked .atlas is read by libGDX's own parser
# (AtlasList.java), by the Godot reader's (PlaxAtlas, atlas_list.gd) and by the three.js reader's (plax.js
# parseAtlas, atlas_list.mjs); each region, in order, must read the same: name, place, size, original size, offset,
# rotation. A page names its layers by that order (regionName and regionPosition), so a difference draws another
# image in a reader than in libGDX.
#   atlas-check.sh               the tracked atlases: exit 1 and print the first differing lines of each that differs
#   atlas-check.sh --unindented  the same atlases with every line's leading spaces taken out, as TexturePacker writes
#                                them without prettyPrint: libGDX reads a line by its colon, so must the readers (r567)
#   atlas-check.sh --swapped     the negative control: the readers read a twin with its first two region names
#                                swapped, libGDX the original; every atlas of two regions or more must FAIL, in both
# Needs java, godot, node and gdx's jar in the Gradle cache.
set -u
cd "$(dirname "$0")/../.." || exit 1
GODOT=${GODOT:-godot}
MODE=${1:-}
case "$MODE" in "" | --unindented | --swapped) ;; *) echo "usage: atlas-check.sh [--unindented|--swapped]" >&2; exit 2 ;; esac
WORK=$(mktemp -d)
TWINS=()
trap 'rm -rf "$WORK"; rm -f "${TWINS[@]}"' EXIT
GDX=$(find ~/.gradle/caches -name 'gdx-1*.jar' | grep -v sources | sort -V | tail -1)
[ -n "$GDX" ] || { echo "atlas-check: no gdx jar in ~/.gradle/caches: run ./gradlew :core:build first" >&2; exit 2; }
command -v node >/dev/null || { echo "atlas-check: no node, which reads the three.js parser" >&2; exit 2; }
mapfile -t atlases < <(git ls-files '*.atlas')
[ "${#atlases[@]}" -gt 0 ] || { echo "atlas-check: no tracked .atlas" >&2; exit 2; }

# The first two region lines of differing names swapped: a region line is one with no colon that is not a page's
# image (the first line after a blank one, or of the file).
swap() {
	awk -v page=1 '{ line[NR] = $0 }
		/^[ \t]*$/ { page = 1; next }
		page { page = 0; next }
		!/:/ { n = $0; gsub(/^[ \t]+|[ \t]+$/, "", n)
			if (!a) { a = NR; an = n } else if (!b && n != an) b = NR }
		END { if (b) { t = line[a]; line[a] = line[b]; line[b] = t }
			for (i = 1; i <= NR; i++) print line[i] }' "$1"
}

gdx=() readers=()
for a in "${atlases[@]}"; do
	gdx+=("$PWD/$a")
	if [ -z "$MODE" ]; then
		readers+=("$PWD/$a")
		continue
	fi
	# Next to the original, so the parsers find its images; removed on exit.
	twin="$(dirname "$a")/.r567${MODE#--}-$(basename "$a")"
	TWINS+=("$twin")
	if [ "$MODE" = --unindented ]; then sed 's/^[ \t]*//' "$a" >"$twin"; else swap "$a" >"$twin"; fi
	readers+=("$PWD/$twin")
	[ "$MODE" = --unindented ] && gdx[-1]="$PWD/$twin"
done

# headless: a plain java reading text files, no window; godot below runs --headless, node draws nothing.
java -cp "$GDX" tools/r565-atlas-net/AtlasList.java "${gdx[@]}" >"$WORK/libgdx.txt" 2>"$WORK/libgdx.err" ||
	{ echo "atlas-check: libGDX's parser failed (AtlasList.java):" >&2; cat "$WORK/libgdx.err" >&2; exit 2; }
"$GODOT" --headless --path engines/godot --script "$PWD/tools/r565-atlas-net/atlas_list.gd" -- "${readers[@]}" \
	>"$WORK/godot.raw" 2>"$WORK/godot.err"
grep -v -e '^Godot Engine' -e '^$' "$WORK/godot.raw" >"$WORK/godot.txt"
node tools/r565-atlas-net/atlas_list.mjs engines/threejs/src/plax.js "${readers[@]}" >"$WORK/threejs.txt" \
	2>"$WORK/threejs.err" ||
	{ echo "atlas-check: the three.js parser failed (atlas_list.mjs):" >&2; cat "$WORK/threejs.err" >&2; exit 2; }

block() { awk -v p="== $2" '$0 == p {on = 1; next} /^== / {on = 0} on' "$1"; }
status=0 checked=0 failed=0
for i in "${!gdx[@]}"; do
	checked=$((checked + 1))
	for reader in godot threejs; do
		if ! diff <(block "$WORK/libgdx.txt" "${gdx[$i]}") <(block "$WORK/$reader.txt" "${readers[$i]}") >"$WORK/d"; then
			status=1 failed=$((failed + 1))
			echo "FAIL ${atlases[$i]}${MODE:+ ($MODE)}: the $reader reader's parser reads its regions otherwise than libGDX (< libGDX, > $reader):"
			head -4 "$WORK/d" | sed 's/^/    /'
		fi
	done
done
regions=$(grep -vc '^== ' "$WORK/libgdx.txt")
if [ $status = 0 ]; then
	echo "PASS${MODE:+ ($MODE)}: $checked atlases, every region read alike by libGDX, the Godot reader and the three.js reader ($regions regions)"
else
	echo "$failed FAIL of $((checked * 2)) (atlas, reader) pairs${MODE:+ ($MODE)}"
	grep -i error "$WORK/godot.err" | sort -u | head -5 | sed 's/^/  godot: /'
fi
exit $status
