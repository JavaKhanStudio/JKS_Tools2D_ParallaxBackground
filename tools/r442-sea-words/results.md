# r442: "sea, ocean, water" in the land layers' negative on a page with a sea layer

`dry.sh > after.txt`: every gen.py prompt and negative of the beach, forest and post-apo pages. Against `before.txt`
(HEAD before r442) only the beach's mountains (l0), dunes (l2) and palms (l3, l3v1-3) change, their negative gaining
"sea, ocean, water"; its sea layer (l1) keeps the whole theme. Forest and post-apo: identical.

The dunes strip (layer 2, DreamShaperXL Turbo, cfg 2, dpmpp_sde), seeds 11020-11029: `tools/r438-dunes/repaint.sh`
(r437's negative) against `repaint.sh` here (r442's). gen.py is deterministic here: r438's 11020 repainted is 0.02
levels off. Sea px: the painted strip's pixels with b > r + 60 and g > r + 20.

| seed | before | sea px | after | sea px |
|------|--------|--------|-------|--------|
| 11020 | refused | 882 | refused | 959 |
| 11021 | refused | 1071 | kept | 877 |
| 11022 | refused | 1237 | refused | 1176 |
| 11023 | kept | 0 | kept | 0 |
| 11024 | kept | 1220 | kept | 376 |
| 11025 | kept | 3629 | kept | 2198 |
| 11026 | refused | 2090 | refused | 2050 |
| 11027 | refused | 853 | refused | 783 |
| 11028 | kept | 0 | kept | 0 |
| 11029 | kept | 0 | kept | 0 |
| | 5 refused | 10982 | 4 refused | 8419 |

The page (`ai-page.sh ... --seed 11`, build/r442/beach): one refusal (dunes 11020), 11021 kept, against three
(11020-11022) on c8cd2e2. That is the one seed the negative flipped: at cfg 2 a negative pulls little, and SD still
paints sea slivers between the dunes on 6 seeds of 10.
