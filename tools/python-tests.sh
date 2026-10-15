#!/usr/bin/env bash
# headless: runs Python unit tests, no window.
# python-tests.sh — run every tracked tools/**/*_test.py, each in its own process, and exit 1 if any fails (r411).
# CI's build job runs it; run it before a push that touches tools/. A test file must exit non-zero on a failure
# (unittest.main() does) and run on a CI runner: no GPU, no ../JKS_Tools2D_ParallaxAI checkout. One that cannot goes
# in SKIP below, with the reason.
#
# The tests run through uv at pinned numpy, Pillow and scipy (the ComfyUI venv's, which depth-page.sh runs in): a new
# release would move what they compute without the code moving. Only tracked files: what CI sees.
set -u
cd "$(dirname "$0")/.." || exit 1
PYTHON=3.12 NUMPY=2.4.4 PILLOW=12.2.0 SCIPY=1.17.1
# path → why it does not run here. Empty: every test file runs today.
declare -A SKIP=()
mapfile -t tests < <(git ls-files 'tools/*_test.py' 'tools/**/*_test.py' | sort -u)
fail=0 ran=0
for t in "${tests[@]}"; do
	if [ -n "${SKIP[$t]:-}" ]; then echo "python-tests: SKIP $t (${SKIP[$t]})"; continue; fi
	echo "python-tests: RUN  $t"
	ran=$((ran + 1))
	if uv run --no-project -q --python "$PYTHON" --with "numpy==$NUMPY" --with "pillow==$PILLOW" --with "scipy==$SCIPY" \
		python -I "$t"; then
		echo "python-tests: PASS $t"
	else
		echo "python-tests: FAIL $t" >&2
		fail=1
	fi
done
[ "$ran" -gt 0 ] || { echo "python-tests: no test file found" >&2; exit 1; }
[ "$fail" -eq 0 ] && echo "python-tests: all $ran passed"
exit "$fail"
