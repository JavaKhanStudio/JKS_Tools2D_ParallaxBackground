# shots-out.sh — sourced by the frame checks (godot-/jme-parallax-shots.sh): a run's own output folder (r172).
#
#   . tools/shots-out.sh; OUT=$(shots_out "$ROOT/build/godot" "$ROUND")
#
# The board's sessions share one checkout: a fixed build/godot/<round>, emptied at the start of each run, let two runs
# of the same round delete each other's stills mid-run. Each run now writes BASE/.runs/<round>-<pid>, and
# BASE/<round> is a link to the latest started, so what reads build/godot/<round> (CI's artifact upload and log greps,
# the docs) still finds it. A run folder whose process is gone is removed, unless the link points at it.
shots_out() {
	local base=$1 name
	name=$(basename "$2")
	local runs="$base/.runs" out="$base/.runs/$name-$$" link="$base/$name" old pid
	mkdir -p "$runs"
	rm -rf "$out"; mkdir -p "$out"
	[ -L "$link" ] || rm -rf "$link"
	ln -s ".runs/$name-$$" "$link.$$" && mv -T "$link.$$" "$link"
	for old in "$runs/$name"-*; do
		[ -d "$old" ] || continue
		pid=${old##*-}
		[ "$old" = "$out" ] && continue
		[ "$(readlink "$link")" = ".runs/$(basename "$old")" ] && continue
		kill -0 "$pid" 2>/dev/null || rm -rf "$old"
	done
	echo "$out"
}
