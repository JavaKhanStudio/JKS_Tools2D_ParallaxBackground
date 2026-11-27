# r83: three.js QuadBuffer + RunPool refacto, proof

- frame-check-before.txt / -after.txt: the PASS/FAIL lines of engines/threejs/tools/frame-check.sh (all 17 default
  rounds), before = HEAD 76b7e8a in a worktree, after = this change. Identical; all 321 per-still numbers identical too.
- bench: engines/threejs/tools/bench.sh, RUNS=5, sum of the 5 scenes' meanMs. b* = before tree, a* = after tree.
  bench-before/-after.json are the first pair; b1..b4/a1..a4 run alternately (b,a,b,a then a,b,a,b).

  before: 2.737 3.428 2.606 2.433 3.848   (best 2.433, median 2.737)
  after:  3.145 4.297 2.839 2.684 2.413   (best 2.413, median 2.839)

  The same tree spreads 2.4-3.8 ms between runs (shared GPU), so the after run is not slower beyond that noise.
