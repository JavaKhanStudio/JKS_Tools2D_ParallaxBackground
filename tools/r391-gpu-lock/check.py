"""r391: the AI tools' GPU lock (tools/gpu_lock.py) makes a second run wait, never fail, and ai_page's --timeout does
not count the wait.

    python3 tools/r391-gpu-lock/check.py

Fake GPU steps (a sleep under gpu_lock.held) stand in for gen.py, on a lock file of their own (PARALLAX_GPU_LOCK:
a real run on the real one would take it between them): a waiter started while a
holder runs prints WAITING once with the holder's pid, then TAKEN, and its step starts after the holder's ends; a
holder killed with SIGKILL frees the lock; ai_page.run() at a 2 s timeout lets a command wait 4 s for the lock and
finish, and still kills one that hangs after taking it. Exit 1 on any failure. No GPU, no window.
"""
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

TOOLS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(TOOLS, "r239-ai-strips"))
sys.path.insert(0, TOOLS)
_scratch = tempfile.TemporaryDirectory()
os.environ["PARALLAX_GPU_LOCK"] = os.path.join(_scratch.name, "gpu.lock")  # before gpu_lock's import; children inherit
import ai_page  # noqa: E402
import gpu_lock  # noqa: E402

STEP = """import sys, time
sys.path.insert(0, {tools!r})
import gpu_lock
with gpu_lock.held("fake"):
    t0 = time.time()
    print("start", t0, flush=True)
    time.sleep(float(sys.argv[1]))
    print("end", time.time(), flush=True)
"""
fails = 0


def check(name, ok, detail):
    global fails
    fails += not ok
    print(f"{'OK  ' if ok else 'FAIL'} {name}: {detail}")


def times(log):
    words = dict(line.split()[:2] for line in Path(log).read_text().splitlines() if line.startswith(("start", "end")))
    return float(words["start"]), float(words["end"])


with tempfile.TemporaryDirectory() as t:
    step = os.path.join(t, "step.py")
    with open(step, "w") as f:
        f.write(STEP.format(tools=os.path.abspath(TOOLS)))

    a_log, b_log = os.path.join(t, "a.log"), os.path.join(t, "b.log")
    with open(a_log, "w") as fa, open(b_log, "w") as fb:
        a = subprocess.Popen([sys.executable, step, "3"], stdout=fa, stderr=subprocess.STDOUT)
        time.sleep(1)
        b = subprocess.Popen([sys.executable, step, "0.2"], stdout=fb, stderr=subprocess.STDOUT)
        a.wait(), b.wait()
    text = Path(b_log).read_text()
    (a0, a1), (b0, b1) = times(a_log), times(b_log)
    check("second waits", text.count(gpu_lock.WAITING) == 1 and f"pid {a.pid}:" in text and gpu_lock.TAKEN in text,
          text.splitlines()[0])
    check("steps never overlap", b0 >= a1, f"first {a0 % 100:.2f}-{a1 % 100:.2f}, second {b0 % 100:.2f}-{b1 % 100:.2f}")

    k_log = os.path.join(t, "k.log")
    with open(k_log, "w") as fk:
        k = subprocess.Popen([sys.executable, step, "60"], stdout=fk, stderr=subprocess.STDOUT)
        time.sleep(1)
        k.kill()
        k.wait()
    t0 = time.time()
    with gpu_lock.held("check.py"):
        took = time.time() - t0
    check("a killed holder frees it", took < 1, f"taken {took:.2f} s after the kill")

    with open(os.path.join(t, "h.log"), "w") as fh:
        holder = subprocess.Popen([sys.executable, step, "4"], stdout=fh, stderr=subprocess.STDOUT)
        time.sleep(0.5)
        w_log = os.path.join(t, "w_gen.log")
        t0 = time.time()
        code = ai_page.run([sys.executable, step, "0.5"], w_log, timeout=2)
        took = time.time() - t0
        holder.wait()
    check("ai_page's timeout skips the wait", code == 0 and took > 3 and not os.path.exists(w_log + ".hung"),
          f"code {code} after {took:.1f} s at a 2 s timeout")

    with open(os.path.join(t, "h.log"), "w") as fh:
        holder = subprocess.Popen([sys.executable, step, "3"], stdout=fh, stderr=subprocess.STDOUT)
        time.sleep(0.5)
        g_log = os.path.join(t, "g_gen.log")
        t0 = time.time()
        code = ai_page.run([sys.executable, step, "60"], g_log, timeout=2)
        took = time.time() - t0
        holder.wait()
    check("a hang under the lock is still killed", code == ai_page.TIMED_OUT and took < 15,
          f"code {code} after {took:.1f} s")
sys.exit(1 if fails else 0)
