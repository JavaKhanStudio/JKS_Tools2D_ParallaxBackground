#!/usr/bin/env bash
# nested-x.sh WxH COMMAND... — inside cage: a nested Xwayland of that size on a display number nobody uses, COMMAND run
# with DISPLAY set to it, then the server stopped. Exits with COMMAND's status (r142).
#
#   WLR_BACKENDS=headless cage -- tools/nested-x.sh 1280x720 bash -c "$RUN"
#
# The shot scripts used a fixed :9: two runs at once shared it ("Server is already active for display 9"), and the first
# to finish killed the other's X server mid-run. Xwayland -displayfd picks a free number and says which when it is ready.
set -u
[ $# -ge 2 ] || { echo "usage: tools/nested-x.sh WxH COMMAND..." >&2; exit 2; }
geometry=$1; shift
fifo=$(mktemp -u); mkfifo "$fifo" || exit 2
Xwayland -displayfd 3 -geometry "$geometry" 3>"$fifo" &
X=$!
read -r -t 20 display <"$fifo"
rm -f "$fifo"
if [ -z "${display:-}" ]; then
	echo "nested-x.sh: Xwayland did not start" >&2
	kill $X 2>/dev/null
	exit 2
fi
DISPLAY=:$display "$@"
status=$?
kill $X 2>/dev/null; wait $X 2>/dev/null
exit $status
