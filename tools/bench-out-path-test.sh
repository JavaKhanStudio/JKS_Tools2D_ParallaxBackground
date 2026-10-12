#!/usr/bin/env bash
# bench-out-path-test.sh — the readers' bench.sh resolve OUT as a caller expects (parallax-readers:r25): a given relative
# OUT against the caller's directory, the default at the repository root. Runs each script's lines up to its OUT default
# (no bench, no window), from engines/. Exits 1 on a wrong path.
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$PWD" FAIL=0
for r in godot jme; do
	s="engines/$r/tools/bench.sh"
	head="$(sed -n "1,/^OUT=\"\${OUT:-/p" "$s")"
	# no default line: running "$head" would be the whole script, a real bench
	# shellcheck disable=SC2016  # the pattern is the literal text of the line
	case "$(tail -n1 <<<"$head")" in 'OUT="${OUT:-'*) ;; *) echo "FAIL $r: no OUT=\"\${OUT:-...} line in $s"; FAIL=1; continue ;; esac
	for arg in "" "x/out.json"; do
		got="$(cd engines && bash -c "$head"$'\necho "$OUT"' "../$s" ${arg:+"$arg"})"
		want="${arg:+$ROOT/engines/$arg}"; want="${want:-$ROOT/build/bench/$r.json}"
		if [ "$got" = "$want" ]; then echo "ok   $r ${arg:-(default)}: $got"; else echo "FAIL $r ${arg:-(default)}: $got, want $want"; FAIL=1; fi
	done
done
exit $FAIL
