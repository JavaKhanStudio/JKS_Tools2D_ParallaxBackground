#!/usr/bin/env bash
# offscreen-lint.sh [SCRIPT...] — fails on a tools/*.sh that opens a window outside cage (r171).
#
#   tools/offscreen-lint.sh                  every tracked tools/*.sh (CI, job build)
#   tools/offscreen-lint.sh path/to/new.sh   one script
#
# gradle/offscreen.gradle refuses a JavaExec that is neither offscreen nor headless (r144); this is the same rail for
# scripts. A script OPENS A WINDOW when a line that is not a comment runs `java ... -cp|-classpath|-jar`, an installDist
# `build/install/.../bin/`, godot without --headless, or Chrome/Chromium/Firefox without --headless. Such a script
# passes when it also runs `cage --` or tools/offscreen.sh, or calls a tools/*.sh that does (tools/parallax-lab-shots.sh), or
# carries a line `# on-screen: <why>` for a window opened on purpose (tools/start-demo.sh, browser-test.sh --open).
# A nested X server on a fixed display (`Xwayland :9`, `Xvfb :1`) fails in any script: take a free one with
# tools/nested-x.sh (r142).
#
# Exit 0 when every script passes, 1 with one line per fault (the script's name, the line, what to do), 2 on usage.
# Heuristic by design: it reads text, it does not run anything. Proof: tools/offscreen-lint-test.sh.
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd) || exit 2
cd "$ROOT" || exit 2
if [ $# -eq 0 ]; then
	mapfile -t scripts < <(git ls-files 'tools/*.sh')
else
	scripts=("$@")
fi
exec python3 - "$ROOT/tools" "${scripts[@]}" <<'PY'
import os
import re
import sys

tools_dir, scripts = sys.argv[1], sys.argv[2:]

HEADLESS = re.compile(r'--headless')
OPENERS = [
    ('runs java', re.compile(r'\bjava\b.*\s(-cp|-classpath|-jar)\b'), False),
    ('runs an installDist program', re.compile(r'build/install/\S*/bin/'), False),
    ('runs godot', re.compile(r'(\$\{?GODOT\}?|(?<![\w/.-])godot)"?\s+-'), True),
    ('runs a browser', re.compile(r'(\$\{?CHROME\}?|\$\{?FIREFOX\}?|(?<![\w/.-])(google-chrome|chromium|chromium-browser|firefox))"?\s+[-"$]'), True),
]
FIXED_X = re.compile(r'\b(Xwayland|Xvfb|Xephyr)\s+:\d')
WRAPS = re.compile(r'\bcage\s+--|(^|[\s"\'/])offscreen\.sh\b')
CALLS = re.compile(r'(?:^|[\s"\'(;&|])(?:\$\{?\w+\}?/|\./)?tools/([\w.-]+\.sh)\b')
MARKER = re.compile(r'^\s*#\s*on-screen:\s*\S')
HEREDOC = re.compile(r'<<-?\s*([\'"]?)(\w+)\1')


def code_lines(path):
    """(number, text, in_heredoc) of every line that is not a comment."""
    out, end = [], None
    with open(path, encoding='utf-8', errors='replace') as f:
        for n, line in enumerate(f, 1):
            text = line.rstrip('\n')
            if end is not None:
                if text.strip() == end:
                    end = None
                else:
                    out.append((n, text, True))
                continue
            if text.lstrip().startswith('#'):
                continue
            out.append((n, text, False))
            m = HEREDOC.search(text)
            if m:
                end = m.group(2)
    return out


def marked(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return any(MARKER.match(line) for line in f)


wrapped_memo = {}


def wrapped(path, seen=()):
    """Runs cage or offscreen.sh itself, or calls a tools/*.sh that does."""
    key = os.path.realpath(path)
    if key in wrapped_memo:
        return wrapped_memo[key]
    if key in seen or not os.path.isfile(path):
        return False
    result = False
    for _, text, in_heredoc in code_lines(path):
        if WRAPS.search(text):
            result = True
            break
        if in_heredoc:
            continue
        for callee in CALLS.findall(text):
            target = os.path.join(tools_dir, callee)
            if os.path.realpath(target) != key and wrapped(target, seen + (key,)):
                result = True
                break
        if result:
            break
    wrapped_memo[key] = result
    return result


faults = []
for path in scripts:
    lines = code_lines(path)
    for n, text, _ in lines:
        if FIXED_X.search(text) and os.path.basename(path) != 'nested-x.sh':
            faults.append('%s:%d: starts an X server on a fixed display, two runs at once collide: run the command '
                          'through tools/nested-x.sh, which takes a free one\n    %s' % (path, n, text.strip()))
    if marked(path):
        continue
    opener = None
    for n, text, _ in lines:
        for what, pattern, unless_headless in OPENERS:
            if pattern.search(text) and not (unless_headless and HEADLESS.search(text)):
                opener = (n, what, text.strip())
                break
        if opener:
            break
    if opener and not wrapped(path):
        n, what, text = opener
        faults.append('%s:%d: %s, which opens a window, and the script never runs cage: wrap it in '
                      '`WLR_BACKENDS=headless cage -- ...` or tools/offscreen.sh, or add a line '
                      '`# on-screen: <why>` if the window is meant for the screen\n    %s' % (path, n, what, text))

for fault in faults:
    print(fault)
print('offscreen-lint: %d script(s), %s' % (len(scripts), 'OK' if not faults else '%d fault(s)' % len(faults)))
sys.exit(1 if faults else 0)
PY
