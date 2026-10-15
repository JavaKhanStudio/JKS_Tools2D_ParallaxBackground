#!/usr/bin/env python3
"""r396's proof: a depth_page run beside a gen.py run, nvidia-smi sampled every 0.5 s. depth_page's own process must
never show there: its GPU steps run in children (depth_page.in_child), which end with their step. Samples showing two
processes at once are listed, not failed: gen.py opens CUDA (about 100 MiB) before it waits for the lock.

    ~/ComfyUI/venv/bin/python tools/r396-gpu-child/smi.py PAINTING OUT_DIR

Starts tools/depth-page.sh's depth_page.py (--no-shots) on PAINTING into OUT_DIR/page, and 3 s later a gen.py txt2img
(512x512) into OUT_DIR, which waits for the lock or holds it. Writes OUT_DIR/smi.log (time, then pid:MiB per process
on the GPU) and exits 1 when depth_page's pid shows. Uses the GPU (gpu_lock).
"""
import os
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
PY = os.path.join(os.environ.get("COMFYUI_DIR", os.path.expanduser("~/ComfyUI")), "venv", "bin", "python")


def on_gpu():
    out = subprocess.run(["nvidia-smi", "--query-compute-apps=pid,used_memory", "--format=csv,noheader,nounits"],
                         capture_output=True, text=True, check=True).stdout
    return {int(p): int(m) for p, m in (line.split(",") for line in out.strip().splitlines() if line.strip())}


def main():
    painting, out = sys.argv[1], os.path.abspath(sys.argv[2])
    os.makedirs(out, exist_ok=True)
    others = on_gpu()
    if others:
        sys.exit(f"the GPU is in use already ({others}): run this when it is free")
    with open(os.path.join(out, "depth_page.log"), "w") as dl, open(os.path.join(out, "gen.log"), "w") as gl:
        dp = subprocess.Popen([PY, os.path.join(ROOT, "tools/r279-depth-page/depth_page.py"), painting,
                               os.path.join(out, "page"), "--theme", "green hills, pine forest, winding river",
                               "--no-shots"], stdout=dl, stderr=subprocess.STDOUT, cwd=ROOT)
        time.sleep(3)
        gen = subprocess.Popen([PY, os.path.join(ROOT, "tools/r239-ai-strips/gen.py"), "txt2img", "--w", "512", "--h",
                                "512", "--seed", "7", "--prompt", "green hills, painterly", "--out",
                                os.path.join(out, "gen.png")], stdout=gl, stderr=subprocess.STDOUT, cwd=ROOT)
        bad, both, seen, samples = [], [], {}, []
        while dp.poll() is None or gen.poll() is None:
            t, now = time.time(), on_gpu()
            samples.append(f"{t:.1f} " + " ".join(f"{p}:{m}" for p, m in sorted(now.items())))
            for p, m in now.items():
                seen[p] = max(seen.get(p, 0), m)
            if dp.pid in now:
                bad.append(f"{t:.1f}: depth_page's own process {dp.pid} on the GPU, {now[dp.pid]} MiB")
            if len(now) > 1:
                both.append(f"{t:.1f}: {now}")
            time.sleep(0.5)
    with open(os.path.join(out, "smi.log"), "w") as f:
        f.write("\n".join(samples) + "\n")
    print(f"depth_page exit {dp.returncode} (pid {dp.pid}), gen.py exit {gen.returncode} (pid {gen.pid}); "
          f"{len(samples)} samples; on the GPU (pid: most MiB): {seen}")
    print(f"{len(both)} samples with two processes on the GPU (gen.py is {gen.pid})" + "".join(
        f"\n  {b}" for b in both[:5]))
    for b in bad[:10]:
        print("FAIL", b)
    if bad or dp.returncode or gen.returncode:
        sys.exit(1)
    print("OK: depth_page's own process never on the GPU")


if __name__ == "__main__":
    main()
