#!/usr/bin/env bash
# r154-same-output.sh BASE — runs every tools/*.py whose text r154 reformatted (% to f-strings), once from BASE (a
# checkout of the commit before, e.g. `git worktree add --detach /tmp/base HEAD~1`) and once from this checkout, on the
# same inputs, and diffs everything they print and write. Exit 1 on any difference. Output paths are in /tmp/r154-same
# and differ by one path segment (base/new), rewritten before the diff.
set -uo pipefail
shopt -s nullglob
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=$(cd "${1:?usage: $0 BASE_CHECKOUT}" && pwd) || exit 2
OUT=/tmp/r154-same
rm -rf "$OUT"
status=0

run() {  # run SIDE: every tool, from that side's checkout, into $OUT/SIDE
	local side=$1 dir
	[ "$side" = base ] && dir=$BASE || dir=$ROOT
	local o="$OUT/$side"
	mkdir -p "$o"
	(
		cd "$dir" || exit 2
		python3 tools/parallax_lab.py lint demo/showcase/*.jplax >"$o/lint-showcase.txt" 2>&1
		python3 tools/parallax_lab.py lint core/test-data/samples/*/*.plaxpj core/test-data/samples/*.plaxpj >"$o/lint-samples.txt" 2>&1
		for cmd in survey round1 round2 study; do python3 tools/parallax_lab.py $cmd "$o/$cmd" >"$o/$cmd.txt" 2>&1; done
		# Pages that break the rules the showcase does not: too few layers, too many, a missing region, a missing atlas.
		python3 - "$o/crafted" <<'PY'
import copy, json, os, sys
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
src = json.load(open('demo/showcase/one-night.jplax'))
def write(name, edit):
    page = copy.deepcopy(src)
    edit(page)
    json.dump(page, open(os.path.join(out, name + '.jplax'), 'w'))
lay = lambda page: page['pageModel']['pageList']
write('two-layers', lambda page: lay(page).__delitem__(slice(2, None)))
write('thirteen-layers', lambda page: lay(page).extend(copy.deepcopy(lay(page)[-1]) for _ in range(13 - len(lay(page)))))
write('no-region', lambda page: lay(page)[0].update(regionName='nope'))
write('no-atlas', lambda page: page['pageModel'].update(atlasName='missing.atlas'))
PY
		python3 tools/parallax_lab.py lint --atlas core/test-data/samples/Demos "$o"/crafted/*.jplax >"$o/lint-crafted.txt" 2>&1
		python3 tools/showcase_pages.py "$o/showcase" >"$o/showcase.txt" 2>&1
		python3 tools/stress-project.py 20 demo/assets/hiver/Hiver.plaxpj "$o/stress" >"$o/stress.txt" 2>&1
		python3 tools/parallax_regions.py core/test-data/samples/transfer/calm.atlas --json >"$o/regions.json" 2>&1
		python3 tools/parallax_regions.py core/test-data/samples/transfer/calm.atlas "$o/regions.png" >"$o/regions.txt" 2>&1
		for r in round1 transfer; do
			[ -d "$ROOT/demo/build/godot/$r" ] || continue
			mkdir -p "$o/frames-$r"; cp -r "$ROOT/demo/build/godot/$r/gdx" "$ROOT/demo/build/godot/$r/godot" "$o/frames-$r/"
			python3 -W ignore tools/compare-parallax-frames.py "engines/godot/tests/$r" "$o/frames-$r" 20 godot >"$o/frames-$r.txt" 2>&1
		done
		ls "$ROOT"/editor/build/r125/out/*.etc2.atlas >/dev/null 2>&1 &&
			python3 tools/r125-etc2-check.py "$ROOT"/editor/build/r125/out/{city,hiver}.atlas >"$o/r125.txt" 2>&1
		[ -d "$ROOT/editor/build/r126/out" ] && python3 tools/r126-pixel-art-check.py "$ROOT/editor/build/r126/out" >"$o/r126.txt" 2>&1
		true
	)
}
run base
run new
# Paths name the side: rewrite them, then compare text and bytes.
while IFS= read -r -d '' f; do
	rel=${f#"$OUT/base/"}
	[[ "$rel" == frames-*/gdx/* || "$rel" == frames-*/godot/* ]] && continue  # the inputs, copied
	g="$OUT/new/$rel"
	if [ ! -e "$g" ]; then echo "only in base: $rel"; status=1; continue; fi
	if ! cmp -s <(sed "s|$OUT/base|SIDE|g; s|$BASE|CHECKOUT|g" "$f") <(sed "s|$OUT/new|SIDE|g; s|$ROOT|CHECKOUT|g" "$g"); then
		echo "DIFFERS: $rel"; diff <(sed "s|$OUT/base|SIDE|g; s|$BASE|CHECKOUT|g" "$f") <(sed "s|$OUT/new|SIDE|g; s|$ROOT|CHECKOUT|g" "$g") | head -5
		status=1
	fi
done < <(find "$OUT/base" -type f -print0)
echo "$(find "$OUT/base" -type f -not -path "*/frames-*/gdx/*" -not -path "*/frames-*/godot/*" | wc -l) files compared: $([ $status = 0 ] && echo same || echo DIFFERENT)"
exit $status
