# r443: the sea between the beach's dunes, over seeds 11020-11029

`python3 tools/r443-dunes-sea/try.py build/r443` paints the beach's dunes strip (DreamShaperXL Turbo, cfg 2,
dpmpp_sde, layer.py --kind dunes) under each candidate; sea px are the painted strip's pixels with b > r + 60 and
g > r + 20, refused is layer.py's exit 4 (creature.on_ridge). `sheet.py` lays them out.

- r442: the r442 negative ("sea, ocean, water"), the theme whole.
- inland: r442 with "desert sand dunes, endless sand" for the dunes' words.
- nobeach: r442 with "tropical beach" struck from the dunes' theme.
- both: inland and nobeach. Its sky grows suns (11023, 11025).
- tropical: only "beach" struck, and "beach" negated.
- today: what ai_page paints since r443 (nobeach, "tropical beach" in the negative too).

```
candidate	refused	sea px	per seed (exit/sea px)
r442	4	8013	4/918 0/838 4/1180 0/0 0/0 0/2206 4/2067 4/804 0/0 0/0
inland	3	1439	4/700 0/0 0/0 0/0 0/0 0/0 4/16 4/723 0/0 0/0
nobeach	1	0	0/0 0/0 0/0 0/0 0/0 0/0 4/0 0/0 0/0 0/0
both	0	0	0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0
tropical	1	524	0/0 0/0 0/0 0/0 0/0 0/0 0/0 4/524 0/0 0/0
today	0	0	0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0 0/0
```

The page (`ai-page.sh ... --seed 11`, the r442 command): no refusal on any layer (three on c8cd2e2, one on r442).
The r442 row is 8013 here and 8419 in tools/r442-sea-words/results.md: 11024 painted 0 sea px here, 376 there.
