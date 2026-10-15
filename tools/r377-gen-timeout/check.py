"""r377: ai_page.run() kills a command held past its timeout and runs it once more, the same, then fails.

    python3 tools/r377-gen-timeout/check.py

A fake gen.py (a sleep) stands in for a hung strip, at a 2 s timeout: one that hangs every time is killed twice and
returns ai_page.TIMED_OUT with both logs kept; one that hangs only on its first try (a marker file) succeeds on the
second, its argv the first's; one that finishes is run once. A killed try leaves its py-spy stack in <log>.hung.stack
(r388), naming the fake's line, one "== try" per kill (with py-spy installed; without it, a line saying so). Exit 1 on
any failure. No GPU, no window.
"""
import os
import shutil
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import ai_page

FAKE = """import sys, os, time
d = sys.argv[1]
n = len(os.listdir(d))
open(os.path.join(d, f"try{n}"), "w").write(" ".join(sys.argv[2:]))
print("1/8 [00:12<01:25, 12.25s/it]", flush=True)
if sys.argv[2] == "always" or (sys.argv[2] == "once" and n == 0):
    time.sleep(60)
"""
fails = 0
SPY = shutil.which("py-spy") or os.access(os.path.expanduser("~/.local/bin/py-spy"), os.X_OK)


def case(name, mode, want_code, want_tries, want_hung):
    global fails
    with tempfile.TemporaryDirectory() as t:
        fake, tries, log = os.path.join(t, "gen.py"), os.path.join(t, "tries"), os.path.join(t, "s_gen.log")
        os.mkdir(tries)
        with open(fake, "w") as f:
            f.write(FAKE)
        t0 = time.time()
        code = ai_page.run([sys.executable, fake, tries, mode, "--seed", "11031"], log, timeout=2)
        took = time.time() - t0
        got = sorted(os.listdir(tries))
        argvs = {Path(tries, g).read_text() for g in got}
        hung = os.path.exists(log + ".hung")
        stack = Path(log + ".hung.stack").read_text() if os.path.exists(log + ".hung.stack") else ""
        kills = want_tries if want_code == ai_page.TIMED_OUT else want_tries - 1
        named = "gen.py" in stack if SPY else "py-spy not found" in stack
        stacked = stack.count("== try") == kills and (named or kills == 0)
        ok = (code == want_code and len(got) == want_tries and len(argvs) == 1 and hung == want_hung and stacked
              and took < 10 + 5 * kills)
        fails += not ok
        print(f"{'OK  ' if ok else 'FAIL'} {name}: code {code}, {len(got)} tries, same argv {len(argvs) == 1}, "
              f".hung {hung}, stacks {stack.count('== try')} naming the fake {named}, {took:.1f} s; log: {Path(log).read_text().strip().splitlines()[-1]}")


case("hangs every time", "always", ai_page.TIMED_OUT, 2, True)
case("hangs once", "once", 0, 2, True)
case("finishes", "never", 0, 1, False)
sys.exit(1 if fails else 0)
