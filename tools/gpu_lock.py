"""One advisory lock on the GPU for the AI tools (r391): gen.py, anim.py and depth_page.py's GPU steps wait for each
other instead of running out of its 8 GB together.

    import gpu_lock
    with gpu_lock.held("depth"):
        ...  # model load through sample and decode, its memory freed before the block ends

An flock on one file ($XDG_RUNTIME_DIR/parallax-gpu.lock, else /tmp; PARALLAX_GPU_LOCK, a test's own), held only round a GPU step, never a session:
two lanes (#ai, #depth) then take turns by the minute. A waiter prints WAITING once, with the holder's pid and argv,
and waits; it never fails. The kernel drops the lock when its holder dies, a killed one too. Not reentrant: a step
never runs another under it (depth_page runs gen.py, which takes the lock itself, outside its own steps).
ai_page.py reads WAITING and TAKEN in a command's log, so the time spent waiting is not counted in its --timeout.
"""
import contextlib
import fcntl
import os
import sys
import time

PATH = os.environ.get("PARALLAX_GPU_LOCK") or os.path.join(os.environ.get("XDG_RUNTIME_DIR") or "/tmp", "parallax-gpu.lock")
WAITING = "gpu_lock: waiting for the GPU"
TAKEN = "gpu_lock: GPU taken"


def take(step):
    """Blocks until the GPU is this process's; returns the open file holding the lock (release() it)."""
    f = open(PATH, "a+")  # noqa: SIM115 (held open: the lock lives as long as the file)
    try:
        fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        waited = None
    except BlockingIOError:
        holder = ""
        for _ in range(20):  # a holder started the same moment writes its name just after its flock
            f.seek(0)
            holder = f.read().strip()
            if holder:
                break
            time.sleep(0.05)
        print(f"{WAITING} (held by {holder or '?'}), for {step}", file=sys.stderr, flush=True)
        t0 = time.monotonic()
        fcntl.flock(f, fcntl.LOCK_EX)
        waited = time.monotonic() - t0
    f.seek(0)
    f.truncate()
    f.write(f"pid {os.getpid()}: {' '.join(sys.argv)} [{step}]\n")
    f.flush()
    if waited is not None:
        print(f"{TAKEN} after {waited:.0f} s, for {step}", file=sys.stderr, flush=True)
    return f


def release(f):
    f.seek(0)
    f.truncate()
    f.flush()
    fcntl.flock(f, fcntl.LOCK_UN)
    f.close()


@contextlib.contextmanager
def held(step):
    f = take(step)
    try:
        yield
    finally:
        release(f)
