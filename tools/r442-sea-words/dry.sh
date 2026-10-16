#!/bin/sh
# r442: every gen.py prompt and negative of the beach, forest and post-apo pages, from ai_page --dry-run (no GPU).
# OUT=DIR for the scratch dirs (default build/r442). before.txt and after.txt beside it: r442's.
cd "$(dirname "$0")/../.." || exit 1
for p in beach forest postapo; do
  case $p in
  beach) set -- --theme "tropical beach, palm trees, warm sunny afternoon" --kinds mountains,sea,dunes,palms --colours 8aa3b8,3fb0b0,e0c890,2a3a25 --sky 4f8fd0,cfe4f2 --seed 11 --ckpt DreamShaperXL_Turbo_v2_1.safetensors --steps 8 --cfg 2 --sampler dpmpp_sde;;
  forest) set -- --theme "misty pine forest in the mountains at dawn" --seed 7;;
  postapo) set -- --theme "post-apocalyptic ruined city, dusty orange haze, desolate" --kinds mountains,ruins,ruins,ruins --seed 7;;
  esac
  echo "== $p"
  python3 tools/r239-ai-strips/ai_page.py "$@" --name $p "${OUT:-build/r442}"/dry-$p --dry-run | grep "gen.py" | python3 -c "
import sys,shlex
for l in sys.stdin:
    a=shlex.split(l.split(' > ')[0]); o=a[a.index('--out')+1].split('/')[-1]
    print(o, '| P:', a[a.index('--prompt')+1], '| N:', a[a.index('--negative')+1])"
done
