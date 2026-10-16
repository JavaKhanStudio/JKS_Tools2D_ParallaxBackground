"""How much memory a process may still take before the kernel kills it (r450): the system's MemAvailable, or less
where a cgroup it runs in (a systemd scope's MemoryMax) leaves less. No imports beyond the standard library: gen.py
asks it before loading a DiT, and ram_test.py runs it on a fake /proc and /sys."""
import os

GB = 1 << 30


def available(root="/"):
    """Bytes the calling process may still take: MemAvailable, capped by each cgroup v2 ancestor's memory.max less its
    memory.current, its page cache counted as free."""
    with open(os.path.join(root, "proc/meminfo")) as f:
        free = next(int(line.split()[1]) * 1024 for line in f if line.startswith("MemAvailable:"))
    try:
        with open(os.path.join(root, "proc/self/cgroup")) as f:
            path = next(line.strip().split(":", 2)[2] for line in f if line.startswith("0::"))
    except (OSError, StopIteration):
        return free
    while True:
        d = os.path.join(root, "sys/fs/cgroup", path.lstrip("/"))
        try:
            with open(os.path.join(d, "memory.max")) as f:
                cap = f.read().strip()
            if cap != "max":
                with open(os.path.join(d, "memory.current")) as f:
                    used = int(f.read())
                free = min(free, int(cap) - used + reclaimable(d))
        except (OSError, ValueError):
            pass
        if path in ("", "/"):
            return free
        path = os.path.dirname(path)


def reclaimable(cgroup_dir):
    """A cgroup's page cache (memory.stat's active_file + inactive_file), bytes: charged to memory.current, but the
    kernel drops it before it kills (r450: the model files a strip had read left the next strip's check 1 GB short)."""
    try:
        with open(os.path.join(cgroup_dir, "memory.stat")) as f:
            stat = dict(line.split() for line in f if line.strip())
        return int(stat.get("active_file", 0)) + int(stat.get("inactive_file", 0))
    except (OSError, ValueError):
        return 0


def anon(root="/"):
    """The calling process's own anonymous RSS, bytes: what it already holds of a peak measured from its start."""
    with open(os.path.join(root, "proc/self/status")) as f:
        return next((int(line.split()[1]) * 1024 for line in f if line.startswith("RssAnon:")), 0)


def shortfall(peak, root="/"):
    """Bytes missing for a run whose anonymous RSS peaks at `peak` from its start; 0 when it fits."""
    return max(0, peak - anon(root) - available(root))
