#!/usr/bin/env bash
# atlas-check.sh (r565): a wide net over the atlas parsers. Every tracked .atlas is read by libGDX's own parser
# (AtlasList.java) and by the Godot reader's (PlaxAtlas, atlas_list.gd); each region, in order, must read the same:
# name, place, size, original size, offset, rotation. A page names its layers by that order (regionName and
# regionPosition), so a difference draws another image in Godot than in libGDX. The three.js parser (plax.js
# parseAtlas) is a line-for-line copy of PlaxAtlas: a fix to one goes in both.
#   atlas-check.sh               the tracked atlases: exit 1 and print the first differing line of each that differs
#   atlas-check.sh --unindented  the negative control: the same atlases with every line's leading spaces taken out, which
#                                libGDX reads the same (TextureAtlasData.readEntry trims), the Godot reader not (r567)
# Needs java, godot and gdx's jar in the Gradle cache.
set -u
cd "$(dirname "$0")/../.." || exit 1
GODOT=${GODOT:-godot}
WORK=$(mktemp -d)
TWINS=()
trap 'rm -rf "$WORK"; rm -f "${TWINS[@]}"' EXIT
GDX=$(find ~/.gradle/caches -name 'gdx-1*.jar' | grep -v sources | sort -V | tail -1)
[ -n "$GDX" ] || { echo "atlas-check: no gdx jar in ~/.gradle/caches: run ./gradlew :core:build first" >&2; exit 2; }
mapfile -t atlases < <(git ls-files '*.atlas')
[ "${#atlases[@]}" -gt 0 ] || { echo "atlas-check: no tracked .atlas" >&2; exit 2; }
paths=()
for a in "${atlases[@]}"; do
	if [ "${1:-}" = --unindented ]; then
		# Next to the original, so both parsers find its images; removed on exit.
		twin="$(dirname "$a")/.r565-unindented-$(basename "$a")"
		TWINS+=("$twin")
		sed 's/^[ \t]*//' "$a" >"$twin"
		paths+=("$PWD/$twin")
	else
		paths+=("$PWD/$a")
	fi
done
# headless: a plain java reading text files, no window; godot below runs --headless.
java -cp "$GDX" tools/r565-atlas-net/AtlasList.java "${paths[@]}" >"$WORK/libgdx.txt" 2>"$WORK/libgdx.err" ||
	{ echo "atlas-check: libGDX's parser failed (AtlasList.java):" >&2; cat "$WORK/libgdx.err" >&2; exit 2; }
"$GODOT" --headless --path engines/godot --script "$PWD/tools/r565-atlas-net/atlas_list.gd" -- "${paths[@]}" \
	>"$WORK/godot.raw" 2>"$WORK/godot.err"
grep -v -e '^Godot Engine' -e '^$' "$WORK/godot.raw" >"$WORK/godot.txt"
status=0 checked=0
for p in "${paths[@]}"; do
	checked=$((checked + 1))
	block() { awk -v p="== $p" '$0 == p {on = 1; next} /^== / {on = 0} on' "$1"; }
	if ! diff <(block "$WORK/libgdx.txt") <(block "$WORK/godot.txt") >"$WORK/d"; then
		status=1
		echo "FAIL ${p#"$PWD"/}: the Godot reader's PlaxAtlas._parse reads its regions otherwise than libGDX (< libGDX, > Godot):"
		head -4 "$WORK/d" | sed 's/^/    /'
	fi
done
if [ $status = 0 ]; then
	echo "PASS: $checked atlases, every region read alike by libGDX and the Godot reader ($(grep -vc '^== ' "$WORK/libgdx.txt") regions)"
else
	grep -i error "$WORK/godot.err" | sort -u | head -5 | sed 's/^/  godot: /'
fi
exit $status
