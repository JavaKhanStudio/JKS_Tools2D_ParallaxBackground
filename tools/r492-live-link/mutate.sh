#!/usr/bin/env bash
# mutate.sh: does tools/live_link_stub_test.py see a stub that breaks docs/live-link.md? Each line below breaks one
# promise in a copy of the stub; the test must fail on every one (r492). Exits 1 if a mutant survives or if the
# unbroken copy fails.
# headless: Python over HTTP on 127.0.0.1, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
status=0
run() { # NAME SED_EXPR
	cp tools/live-link-stub.py tools/live_link_stub_test.py "$WORK/"
	if [ -n "$2" ]; then
		sed -i -E "$2" "$WORK/live-link-stub.py"
		if cmp -s tools/live-link-stub.py "$WORK/live-link-stub.py"; then
			echo "BROKEN $1: the edit matched nothing"; status=1; return
		fi
	fi
	python3 -I "$WORK/live_link_stub_test.py" >"$WORK/log" 2>&1; got=$?
	if [ -z "$2" ]; then
		if [ "$got" -eq 0 ]; then echo "control passes"; else echo "FAIL control: $(tail -3 "$WORK/log")"; status=1; fi
	elif [ "$got" -eq 0 ]; then
		echo "SURVIVED $1"; status=1
	else
		echo "caught   $1 ($(grep -cE '^(FAIL|ERROR):' "$WORK/log") tests)"
	fi
}
run control ''
run no-cors '/Access-Control-Allow-Origin/d'
run no-private-network '/Access-Control-Allow-Private-Network/d'
run rev-not-exposed '/Access-Control-Expose-Headers/d'
run cached 's/"Cache-Control", "no-store"/"Cache-Control", "max-age=60"/'
run no-rev-header '/X-Plax-Rev"\] = /d'
run rev-in-the-body 's/data, kind = state\["page"\], "application\/json"/data, kind = state["page"].replace(b"{", b"{\\"rev\\":1,", 1), "application\/json"/'
run serves-any-file 's/elif name in state\["files"\]:.*/elif True:/'
run no-preflight 's/self\.send_response\(204\)/self.send_response(405)/'
run first-event-names-nothing 's/return self\.event\(True, self\.names\(self\.state\["stamps"\]\)\)/return self.event(True, False)/'
run atlas-not-first 's/key=lambda n: \(not n\.endswith\("\.atlas"\), n\)/key=lambda n: n/'
run slider-costs-images 's/images = self\.names\(p\["images"\]\) if p\["images"\] and self\.state else False/images = self.names(self.state["stamps"]) if self.state else False/'
run image-moves-page 's/page = bool\(new and \(opened or new\["page"\] != old\["page"\]\)\)/page = bool(new)/'
run no-merging 's/EVENT_WAIT = 0\.15/EVENT_WAIT = 0.0/'
run page-edit-moves-imagesrev 's/if p\["images"\]:$/if True:/'
run restart-repeats-rev 's/self\.rev = self\.images_rev = int\(time\.time\(\) \* 1000\)/self.rev = self.images_rev = 1/'
run no-page-closes-stream 's/if self\.state is None:\n?$/if False:/'
run unindented-bounds 's/f"  bounds:/f"bounds:/'
run loose-before-atlas 's/\(text\.rstrip\("\\n"\) \+ "\\n" \+ loose_atlas_text\(loose\)\)/(loose_atlas_text(loose) + "\\n" + text)/'
run no-live-atlas-name 's/layers\["atlasName"\] = atlas_name/pass/'
run binds-everywhere 's/\("127\.0\.0\.1", args\.port\)/("0.0.0.0", args.port)/; s/http:\/\/127\.0\.0\.1:\{server/http:\/\/0.0.0.0:{server/'
exit $status
