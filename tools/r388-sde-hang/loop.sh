#!/usr/bin/env bash
# headless: gen.py renders off screen on the GPU, no window
# loop.sh OUT N [LIMIT] [-- GEN.PY ARGS...] — r388: R1 dsxlt-beach's palms strip (DreamShaperXL Turbo, dpmpp_sde, 8
# steps, img2img 1536x576, seed 11030) painted N times by gen.py, each into OUT/run<i>.png with its log. A run still
# going after LIMIT s (default 90; one takes ~30 with the model load) is a hang: py-spy dumps its stack, Python and
# native, into OUT/hang<i>.txt, then it is killed. Prints one line per run and the md5 of each strip (same seed: the
# strips must match). Extra gen.py arguments after -- go to every run (r388's fix is tried that way).
#   tools/r388-sde-hang/loop.sh build/r388/before 50
set -u
cd "$(dirname "$0")/../.." || exit 1
O=${1:?OUT} N=${2:?N} LIMIT=${3:-90}
shift $(($# < 3 ? $# : 3))
[ "${1:-}" = "--" ] && shift
PY=${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python
INIT=../JKS_Tools2D_ParallaxAI/build/rounds/r1/dsxlt-beach/strips/l3_init.png
SPY=$(command -v py-spy || echo "$HOME/.local/bin/py-spy")
mkdir -p "$O"
for i in $(seq 1 "$N"); do
	t0=$(date +%s)
	"$PY" tools/r239-ai-strips/gen.py img2img --ckpt DreamShaperXL_Turbo_v2_1.safetensors --w 1536 --h 576 \
		--steps 8 --cfg 2.0 --sampler dpmpp_sde --denoise 0.65 \
		--negative 'text, watermark, frame, border, people, buildings, foreground grass, birds, animals, creatures' \
		--prompt '2d game parallax background layer, tropical beach, palm trees, warm sunny afternoon, foreground palm trees silhouette on a sandy shore, painterly, plain pale sky' \
		--init "$INIT" --tile x --seed 11030 --out "$O/run$i.png" "$@" >"$O/run$i.log" 2>&1 &
	pid=$!
	state=ok
	while kill -0 "$pid" 2>/dev/null; do
		# a queue for the GPU (gpu_lock) is not a hang: the clock starts when it holds the GPU
		grep -q "gpu_lock: waiting" "$O/run$i.log" && ! grep -q "it/s\|s/it" "$O/run$i.log" && t0=$(date +%s)
		if [ $(($(date +%s) - t0)) -gt "$LIMIT" ]; then
			state=HANG
			"$SPY" dump --native --pid "$pid" >"$O/hang$i.txt" 2>&1
			"$SPY" dump --locals --pid "$pid" >"$O/hang$i-locals.txt" 2>&1
			kill "$pid"
			break
		fi
		sleep 2
	done
	wait "$pid" 2>/dev/null
	md5=$(md5sum "$O/run$i.png" 2>/dev/null | cut -c1-12)
	echo "run $i $state $(($(date +%s) - t0)) s ${md5:-no-strip}"
done
