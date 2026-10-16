#!/usr/bin/env bash
# mutate.sh — r475's window taken out (lint (e) excusing a small join by one column beside it, r398's rule): the
# MistySeams test must fail, TipSeams must still pass. Patches parallax_lab.BESIDE in the test process, no file edited.
# headless: no window, plain python
set -u
cd "$(dirname "$0")/../.." || exit 1
python3 - <<'PY'
import sys
import unittest
sys.path.insert(0, 'tools')
import parallax_lab as lab
import parallax_lab_test as t
lab.BESIDE = 1
load = unittest.defaultTestLoader.loadTestsFromTestCase
misty = unittest.TextTestRunner(verbosity=0).run(load(t.MistySeams))
tip = unittest.TextTestRunner(verbosity=0).run(load(t.TipSeams))
if misty.wasSuccessful():
    sys.exit('WRONG: MistySeams passes with BESIDE = 1')
if not tip.wasSuccessful():
    sys.exit('WRONG: TipSeams fails with BESIDE = 1 (r398 needs one column only)')
print('OK: BESIDE = 1 fails MistySeams, TipSeams passes')
PY
