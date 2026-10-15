#!/usr/bin/env bash
# mutate.sh [OUT] — proves LurkerCases (core/browser-test/cases) sees a wrong lurker rule (r370).
#
#   tools/r370-lurker/mutate.sh build/r370/mutants
#
# In a worktree of HEAD (gradle reads the checkout mid-run: never mutate it in place), each mutant of
# ParallaxPageReader's sightings is written over the committed file and :core:test runs BrowserSuiteTest: every mutant
# must fail it, and the case that caught it is printed. HEAD itself must pass first.
#   in-sight  the figure decided again on entering sight too: a switch in a gap
#   seen      the seen rule left out: the figure stays after it was seen
#   wide      each cover interval 1 unit too wide: a switch while part of the figure shows
#   reveal    reveal() shows it at once, in sight too
# headless: gradle tests only, no window
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:-build/r370/mutants}")"
WT="$OUT/wt"
FILE=core/src/jks/tools2d/parallax/ParallaxPageReader.java
mkdir -p "$OUT"
git worktree remove --force "$WT" 2>/dev/null
git worktree add --detach "$WT" HEAD >/dev/null || exit 1
trap 'git worktree remove --force "$WT"' EXIT

run() {
	(cd "$WT" && ./gradlew -q :core:test --tests '*BrowserSuiteTest*' >"$OUT/$1.log" 2>&1)
}

caught() {
	python3 - "$WT/core/build/test-results/test" <<'PY'
import glob, sys, xml.etree.ElementTree as E
for path in glob.glob(sys.argv[1] + '/*.xml'):
    for case in E.parse(path).getroot().iter('testcase'):
        for failure in case.findall('failure'):
            print(f"    {case.get('name')}: {(failure.get('message') or '')[:160]}")
PY
}

if ! run head; then
	echo "FAIL: HEAD does not pass BrowserSuiteTest ($OUT/head.log)"
	exit 1
fi
status=0
for mutant in in-sight seen wide reveal; do
	git -C "$WT" checkout -q -- "$FILE"
	if ! python3 - "$WT/$FILE" "$mutant" <<'PY'
import sys
path, mutant = sys.argv[1], sys.argv[2]
source = open(path).read()
before, after = {
    'in-sight': ('else if (!inSight && figure.lurkerInSight)', 'else if (inSight != figure.lurkerInSight)'),
    'seen': ('else if (figure.lurkerSeen)', 'else if (false)'),
    'wide': ('<= cover[k + 2] - cover[k + 1])', '<= cover[k + 2] - cover[k + 1] + 1)'),
    'reveal': ('if (figure.lurkerInSight)\n\t\t\t\tfigure.lurkerForced = true;', 'if (false)\n\t\t\t\tfigure.lurkerForced = true;'),
}[mutant]
if source.count(before) != 1:
    sys.exit(f'{mutant}: the line to mutate is not in {path} once')
open(path, 'w').write(source.replace(before, after))
PY
	then
		status=1
		continue
	fi
	if run "$mutant"; then
		echo "FAIL: mutant $mutant passes BrowserSuiteTest"
		status=1
	else
		echo "caught: $mutant"
		caught
	fi
done
git -C "$WT" checkout -q -- "$FILE"
[ $status = 0 ] && echo "OK: every mutant fails LurkerCases"
exit $status
