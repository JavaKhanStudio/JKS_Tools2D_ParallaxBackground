#!/usr/bin/env bash
# r26: proves tools/shots-out.sh keeps a mutant run out of BASE/<round>, the folder tools/readers-health reads.
# In a scratch BASE: a clean run, then a run under a script named mutate.sh, under BREAK, and a clean run from a
# command line that only mentions mutate.sh. Fails unless the mutants link BASE/.mutant/x and the clean ones BASE/x.
set -u
cd "$(dirname "$0")/../.." || exit 1
ROOT="$PWD" T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/m"
printf '. "%s/tools/shots-out.sh"; shots_out "%s/b" engines/rounds/x\n' "$ROOT" "$T" >"$T/run.sh"
cp "$T/run.sh" "$T/m/mutate.sh"
status=0
expect() { # NAME LINK: LINK must point at the run just made ($OUT)
	if [ "$(readlink -f "$T/b/$2")" = "$(readlink -f "$OUT")" ]; then echo "ok: $1 -> $2"; else echo "WRONG: $1 not at $2"; status=1; fi
}
OUT=$(bash "$T/run.sh"); expect clean x; CLEAN=$OUT
OUT=$(bash "$T/m/mutate.sh"); expect "under a mutate.sh" .mutant/x
OUT=$(BREAK=scroll bash "$T/run.sh"); expect "under BREAK" .mutant/x
if [ "$(readlink -f "$T/b/x")" = "$(readlink -f "$CLEAN")" ]; then echo "ok: x still the clean run"; else echo "WRONG: a mutant moved x"; status=1; fi
OUT=$(bash -c "true mutate.sh; bash $T/run.sh"); expect "a command line naming mutate.sh" x
exit $status
