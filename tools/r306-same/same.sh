#!/usr/bin/env bash
# same.sh SCRIPTS IN OUT [--gen] — r306's proof that splitting layer.py, pixel.py and gen.py's main() changed nothing.
#
#   git archive HEAD tools/r239-ai-strips | tar -x -C /tmp/before
#   tools/r306-same/same.sh /tmp/before/tools/r239-ai-strips build/r239 /tmp/r306-a --gen
#   tools/r306-same/same.sh tools/r239-ai-strips build/r239 /tmp/r306-b --gen
#   diff -r /tmp/r306-a /tmp/r306-b && echo SAME
#
# Runs SCRIPTS' layer.py and pixel.py on IN's strips (run.sh's: *_ai.png, *_mask.png, pinesw*.png) with every option
# that takes its own branch, and with --gen gen.py's five modes on ComfyUI (COMFYUI_DIR, ~1.5 GB of VRAM, small and
# few steps). OUT gets each output's sha256, exit code and stderr with OUT's path and tqdm's speeds taken out: two
# runs diff equal when the scripts do the same thing.
set -u
SCRIPTS=$(cd "$1" && pwd); IN="$2"; OUT="$3"; GEN="${4:-}"
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
mkdir -p "$OUT/files"
run() { # NAME CMD...: the files it writes go to $OUT/files/NAME*
	local name="$1"; shift
	"$@" 2> "$OUT/$name.err" > "$OUT/$name.out"
	echo "exit $?" >> "$OUT/$name.err"
	sed -i -e "s#$OUT/files/##g" -e "s#$OUT/##g" -e '/it\/s\]/d' "$OUT/$name.err" "$OUT/$name.out"  # tqdm's speeds vary
}
L=(python3 "$SCRIPTS/layer.py")
run l-plain "${L[@]}" "$IN/hills_ai.png" "$OUT/files/l-plain.png"
run l-far "${L[@]}" "$IN/mountains_ai.png" "$OUT/files/l-far.png" --far 60 --near 10
run l-mask "${L[@]}" "$IN/pines_ai.png" "$OUT/files/l-mask.png" --mask "$IN/pines_mask.png" --grow 20
run l-nogap "${L[@]}" "$IN/pines_ai.png" "$OUT/files/l-nogap.png" --gap 0
run l-islands "${L[@]}" "$IN/pinesw1.png" "$OUT/files/l-islands.png" --keep-islands --no-creatures
run l-pixel "${L[@]}" "$IN/mountains_ai.png" "$OUT/files/l-pixel.png" --pixel 4 --colours 10
run l-palette "${L[@]}" "$IN/pinesw2.png" "$OUT/files/l-palette.png" --pixel 4 --colours 10 --palette "$IN/pinesw0_layer.png"
# A dark column up to the top row: both scripts exit 3.
python3 -c "from PIL import Image; m = Image.open('$IN/hills_ai.png').convert('RGB'); m.paste((20, 30, 20), (500, 0, 540, 384)); m.save('$OUT/top.png')"
run l-top "${L[@]}" "$OUT/top.png" "$OUT/files/l-top.png"
P=(python3 "$SCRIPTS/pixel.py")
run p-top "${P[@]}" "$OUT/top.png" "$OUT/files/p-top.png"
rm "$OUT/top.png"
run p-plain "${P[@]}" "$IN/hills_ai.png" "$OUT/files/p-plain.png"
run p-cell "${P[@]}" "$IN/mountains_ai.png" "$OUT/files/p-cell.png" --cell 8 --colours 6
run p-mask "${P[@]}" "$IN/pines_ai.png" "$OUT/files/p-mask.png" --mask "$IN/pines_mask.png"
run p-palette "${P[@]}" "$IN/pinesw1.png" "$OUT/files/p-palette.png" --palette "$IN/pinesw0_layer.png" --no-creatures
if [ "$GEN" = --gen ]; then
	G=("$PY" "$SCRIPTS/gen.py")
	S=(--w 512 --h 256 --steps 6 --seed 7 --prompt "rolling green hills, plain pale sky")
	run g-txt "${G[@]}" txt2img "${S[@]}" --tile x --out "$OUT/files/g-txt.png"
	run g-img "${G[@]}" img2img "${S[@]}" --init "$IN/hills_init.png" --denoise 0.65 --out "$OUT/files/g-img.png"
	run g-control "${G[@]}" img2img "${S[@]}" --init "$IN/hills_init.png" --denoise 0.65 --tile x \
		--control "$IN/hills_mask.png" --control-edge --control-end 0.8 --out "$OUT/files/g-control.png"
	run g-variants "${G[@]}" variants "${S[@]}" --src "$IN/hills_ai.png" --init "$IN/hills_init.png" --n 2 --edge 96 \
		--denoise 0.65 --tile x --out "$OUT/files/g-variants.png"
	python3 -c "from PIL import Image; m = Image.new('L', (1024, 384)); m.paste(255, (300, 100, 700, 300)); m.save('$OUT/hole.png')"
	run g-inpaint "${G[@]}" inpaint "${S[@]}" --src "$IN/hills_ai.png" --mask "$OUT/hole.png" --denoise 0.9 --out "$OUT/files/g-inpaint.png"
	run g-inpaint9 "${G[@]}" inpaint "${S[@]}" --ckpt dreamshaper_8Inpainting.safetensors --src "$IN/hills_ai.png" \
		--mask "$OUT/hole.png" --noise-mask --out "$OUT/files/g-inpaint9.png"
	rm "$OUT/hole.png"
fi
(cd "$OUT/files" && sha256sum -- *) > "$OUT/sha256.txt"
rm -r "$OUT/files"
