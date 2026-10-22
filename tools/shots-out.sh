# shellcheck shell=bash
# shots-out.sh — sourced by the frame checks (engines/godot/tools/frame-check.sh, engines/jme/tools/frame-check.sh): a run's own output folder (r172).
#
#   . tools/shots-out.sh; OUT=$(shots_out "$ROOT/build/godot" "$ROUND")
#
# The board's sessions share one checkout: a fixed build/godot/<round>, emptied at the start of each run, let two runs
# of the same round delete each other's stills mid-run. Each run now writes BASE/.runs/<round>-<pid>, and
# BASE/<round> is a link to the latest started, so what reads build/godot/<round> (CI's artifact upload and log greps,
# the docs) still finds it. A run folder whose process is gone is removed, unless a link points at it.
#
# A mutant run (r26: BREAK set, PLAX_MUTANT=1, or a tools/*/mutate.sh among its parent processes) links
# BASE/.mutant/<round> instead: BASE/<round> keeps the last clean run, the verdict tools/readers-health reads.
shots_mutant() {
	[ -n "${BREAK:-}" ] || [ "${PLAX_MUTANT:-0}" = 1 ] && return 0
	local p=$$
	while [ "$p" -gt 1 ] 2>/dev/null; do
		# an argument that is a mutate.sh, not any command line that mentions one
		tr '\0' '\n' <"/proc/$p/cmdline" 2>/dev/null | grep -qE '(^|/)mutate\.sh$' && return 0
		p=$(sed 's/.*) . //; s/ .*//' "/proc/$p/stat" 2>/dev/null) || return 1
	done
	return 1
}
shots_out() {
	local base=$1 name
	name=$(basename "$2")
	local runs="$base/.runs" out="$base/.runs/$name-$$" link="$base/$name" to=".runs/$name-$$" old pid
	if shots_mutant; then mkdir -p "$base/.mutant"; link="$base/.mutant/$name"; to="../$to"; fi
	mkdir -p "$runs"
	rm -rf "$out"; mkdir -p "$out"
	[ -L "$link" ] || rm -rf "$link"
	ln -s "$to" "$link.$$" && mv -T "$link.$$" "$link"
	for old in "$runs/$name"-*; do
		[ -d "$old" ] || continue
		pid=${old##*-}
		[ "$old" = "$out" ] && continue
		[ "$(readlink -f "$base/$name")" = "$(readlink -f "$old")" ] && continue
		[ "$(readlink -f "$base/.mutant/$name")" = "$(readlink -f "$old")" ] && continue
		kill -0 "$pid" 2>/dev/null || rm -rf "$old"
	done
	echo "$out"
}
