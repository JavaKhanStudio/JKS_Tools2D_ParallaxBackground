#!/usr/bin/env bash
# headless: gen.py renders off screen on the GPU, no window
# contend.sh OUT N HOG [LIMIT] [-- GEN.PY ARGS...] — r388: loop.sh while another process holds HOG GB of the 8 GB GPU
# (and with BUSY=1 runs matmuls on it without a pause, the GPU time-sliced as under another lane's sampler),
# as before r391's gpu_lock a strip shared it with another lane's model (r377's hang came at 12 s a step: ComfyUI loads
# what fits and runs the rest from the CPU). Two whole SDXL strips at once do not show it: both run out of memory.
# The holder takes the shared gpu_lock for the whole run, so the other lanes' GPU steps wait (gpu_lock: waiting; they
# never fail) instead of piling on; the loop has a lock file of its own (PARALLAX_GPU_LOCK). Out: OUT as loop.sh's.
# HOG 3.5 leaves ~4.2 GB, less than the SDXL UNet: ComfyUI loads it in part (still ~1 s a step; BUSY=1: 2.3 s). HOG 5.2
# leaves the VAE no room (gen.py refuses), and COMFY_CPU_VAE=1 takes minutes a strip, past LIMIT: loop.sh then dumps a
# decode, not a hang.
#   BUSY=1 tools/r388-sde-hang/contend.sh build/r388/busy 40 3.5
set -u
cd "$(dirname "$0")/../.." || exit 1
O=${1:?OUT} N=${2:?N} HOG=${3:?HOG} LIMIT=${4:-300}
shift $(($# < 4 ? $# : 4))
[ "${1:-}" = "--" ] && shift
PY=${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python
mkdir -p "$O"
"$PY" -c 'import sys, time; sys.path.insert(0, "tools"); import gpu_lock, torch
with gpu_lock.held("r388 contend.sh: VRAM held while gen.py loops on a private lock"):
    hog = torch.empty(int(float(sys.argv[1]) * 2**30), dtype=torch.uint8, device="cuda")
    print("held", hog.numel() >> 20, "MiB", flush=True)
    if sys.argv[2] == "1":
        m = torch.randn(4096, 4096, device="cuda", dtype=torch.float16)
        while True:
            m = (m @ m).clamp_(-1, 1)
            torch.cuda.synchronize()
    time.sleep(86400)' "$HOG" "${BUSY:-0}" >"$O/holder.log" 2>&1 &
holder=$!
until grep -q held "$O/holder.log" 2>/dev/null; do
	kill -0 "$holder" 2>/dev/null || { cat "$O/holder.log"; exit 1; }
	sleep 2
done
PARALLAX_GPU_LOCK=$O/loop.lock tools/r388-sde-hang/loop.sh "$O" "$N" "$LIMIT" -- "$@"
kill "$holder"
