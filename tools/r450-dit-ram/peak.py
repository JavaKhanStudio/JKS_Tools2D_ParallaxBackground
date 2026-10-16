#!/usr/bin/env python3
"""r450: a command's peak anonymous RSS (what the kernel's OOM killer counts), sampled every 0.1 s over its process
tree, with the log line it had reached at each new peak.

    python3 tools/r450-dit-ram/peak.py LOG -- CMD...

Runs CMD with stdout and stderr in LOG, prints each new peak (GB, seconds, LOG's last line then) and the final one, and
exits with CMD's code. Wrap it in a memory-capped scope to keep a runaway off the desktop:
    systemd-run --user --scope -p MemoryMax=10G -p MemorySwapMax=0 python3 tools/r450-dit-ram/peak.py ...
"""
import subprocess
import sys
import time


def tree(pid):
    out, todo = [], [pid]
    while todo:
        p = todo.pop()
        out.append(p)
        try:
            with open(f"/proc/{p}/task/{p}/children") as f:
                todo += [int(c) for c in f.read().split()]
        except OSError:
            pass
    return out


def anon_kb(pid):
    try:
        with open(f"/proc/{pid}/status") as f:
            return next((int(line.split()[1]) for line in f if line.startswith("RssAnon:")), 0)
    except OSError:
        return 0


def last_line(log):
    try:
        with open(log, errors="replace") as f:
            lines = [x.strip() for x in f.read()[-4096:].splitlines() if x.strip()]
        return lines[-1][:100] if lines else ""
    except OSError:
        return ""


def main():
    log, cmd = sys.argv[1], sys.argv[sys.argv.index("--") + 1:]
    t0, peak, said = time.monotonic(), 0, 0
    with open(log, "w") as f:
        p = subprocess.Popen(cmd, stdout=f, stderr=subprocess.STDOUT)
        while p.poll() is None:
            kb = sum(anon_kb(q) for q in tree(p.pid))
            if kb > said + 256 * 1024:
                said = kb
                print(f"{kb / 2**20:6.2f} GB at {time.monotonic() - t0:6.1f} s  | {last_line(log)}", flush=True)
            peak = max(peak, kb)
            time.sleep(0.1)
    print(f"peak anon RSS {peak / 2**20:.2f} GB, exit {p.returncode}, {time.monotonic() - t0:.0f} s", flush=True)
    sys.exit(p.returncode if p.returncode >= 0 else 128 - p.returncode)


if __name__ == "__main__":
    main()
