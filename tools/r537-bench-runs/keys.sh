#!/usr/bin/env bash
# keys.sh BEFORE_TREE AFTER_TREE OUT — r537's proof: each cage bench (libGDX, Godot, jME) run once (RUNS=1) in two
# checkouts, and the keys of the JSON each wrote compared. Times differ run to run; the keys must not.
set -u
BEFORE=$1 AFTER=$2 OUT=$(realpath -m "$3")
mkdir -p "$OUT"
fail=0
for b in tools/parallax-bench.sh engines/godot/tools/bench.sh engines/jme/tools/bench.sh; do
	n=$(tr / - <<<"${b%.sh}")
	for side in before after; do
		tree=$BEFORE; [ $side = after ] && tree=$AFTER
		case $b in tools/*) args=(engines/rounds/bench "$OUT/$n.$side.json") ;; *) args=("$OUT/$n.$side.json") ;; esac
		(cd "$tree" && RUNS=1 "$b" "${args[@]}") >"$OUT/$n.$side.out" 2>&1 || { echo "FAIL $b ($side): see $OUT/$n.$side.out"; fail=1; }
	done
	k() { python3 -c 'import json,sys
def keys(o,p=""):
    if isinstance(o,dict):
        for k,v in o.items(): yield from keys(v,p+"/"+k)
    elif isinstance(o,list):
        for v in o[:1]: yield from keys(v,p+"[]")
    else: yield p
print("\n".join(sorted(set(keys(json.load(open(sys.argv[1])))))))' "$1"; }
	if cmp -s <(k "$OUT/$n.before.json") <(k "$OUT/$n.after.json"); then echo "SAME keys: $b"; else echo "DIFF keys: $b"; fail=1; fi
done
exit $fail
