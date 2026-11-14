#!/bin/bash
# r496: warp_test.py must fail on a warp that does not wrap, a warp that moves nothing, a warp of the wrong seed, a
# warp on any kind, and ruins left straight by default (Simon picked the warp on).
# A control copy (unchanged) must pass. Works on copies in a temp dir, never the tree.
set -u
here=$(cd "$(dirname "$0")/.." && pwd)/r239-ai-strips
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
try() {  # name, expected (pass|fail), python replace from, to
  cp "$here/sil.py" "$here/warp_test.py" "$tmp/"
  python3 - "$tmp/sil.py" "$3" "$4" <<'PY' || exit 1
import sys
p, a, b = sys.argv[1:]
s = open(p).read()
if a and a not in s:
    sys.exit(f"mutant's text is gone: {a}")
open(p, "w").write(s.replace(a, b) if a else s)
PY
  if python3 "$tmp/warp_test.py" >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [ "$got" = "$2" ]; then echo "ok   $1: $got"; else echo "BAD  $1: $got, wanted $2"; bad=1; fi
}
bad=0
try control pass "" ""
try "not wrapped" fail "x0, x1, y1 = x0 % w, (x0 + 1) % w, np.minimum(y0 + 1, h - 1)" "x0, x1, y1 = x0.clip(0, w - 1), (x0 + 1).clip(0, w - 1), np.minimum(y0 + 1, h - 1)"
try "moves nothing" fail "mask = warp(mask, amount, a.seed if a.edges_of is None else a.edges_of)" "pass"
try "variant's own field" fail "a.seed if a.edges_of is None else a.edges_of" "a.seed"
try "any kind" fail 'amount = spec["warp"] if "warp" in spec and a.warp is None else a.warp if "warp" in spec else 0' 'amount = a.warp or 0'
try "off by default" fail '"sky": (210, 225, 240), "warp": 0.0243},' '"sky": (210, 225, 240), "warp": 0.0},'
exit $bad
