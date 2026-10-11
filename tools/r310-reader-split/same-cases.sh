#!/usr/bin/env bash
# r310: proves splitting ReaderCases by subject kept every case. Runs BrowserSuiteTest and diffs the names it ran,
# sorted, against cases-before.txt (the names ReaderCases and JsonCases ran at 4498fbd); fails on any difference or
# failure. Writes build/r310/.
# headless: a JUnit run, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
mkdir -p build/r310
./gradlew :core:test --tests '*BrowserSuiteTest' -q >build/r310/same-cases.log 2>&1 || { echo "BrowserSuiteTest failed, see build/r310/same-cases.log"; exit 1; }
XML=core/build/test-results/test/TEST-jks.tools2d.parallax.browsertest.BrowserSuiteTest.xml
command grep -ho 'testcase name="[^"]*"' "$XML" | sort >build/r310/cases-after.txt
if ! diff tools/r310-reader-split/cases-before.txt build/r310/cases-after.txt; then
	echo "the suite's cases changed"; exit 1
fi
echo "same $(wc -l <build/r310/cases-after.txt) cases, $(command grep -c '<failure' "$XML") failures"
