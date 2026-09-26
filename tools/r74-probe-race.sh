#!/usr/bin/env bash
# r74-probe-race.sh — proves driver-probe.sh refuses a port another listener takes after its pre-check.
#
#   tools/r74-probe-race.sh        (needs ./gradlew :editor:installDist)
#
# EDITOR_BIN is a wrapper that starts a stub listener (ncat -lk, which outlives the probe's own port checks) on the port, then runs the real editor, which then fails
# to bind: the race r43 meant to catch. Passes when driver-probe.sh exits 3 and the stub received nothing.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=${DRIVER_PORT:-47793}
REAL_BIN="$ROOT/editor/build/install/ParallaxEditor/bin/ParallaxEditor"
[[ -x "$REAL_BIN" ]] || { echo "missing $REAL_BIN: run ./gradlew :editor:installDist" >&2; exit 2; }
WORK=$(mktemp -d)
trap 'pkill -f "ncat -lk 127.0.0.1 $PORT" 2>/dev/null; rm -rf "$WORK"' EXIT

cat >"$WORK/editor" <<EOF
#!/usr/bin/env bash
ncat -lk 127.0.0.1 $PORT >"$WORK/stub-received" &
sleep 0.3
exec "$REAL_BIN" "\$@"
EOF
chmod +x "$WORK/editor"

echo "list" | timeout 90 env DRIVER_PORT=$PORT EDITOR_BIN="$WORK/editor" "$ROOT/tools/driver-probe.sh" >"$WORK/out" 2>&1
code=$?
cat "$WORK/out"
received=$(cat "$WORK/stub-received" 2>/dev/null)
echo "exit $code; stub received: '${received}'"
[[ $code == 3 && -z "$received" ]] && { echo PASS; exit 0; }
echo FAIL; exit 1
