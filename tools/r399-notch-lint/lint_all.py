"""r399: the (b) faults lint finds on every page named in a list, one line a fault, to compare two lints.

    python3 tools/r399-notch-lint/lint_all.py MODULE PAGES.txt [PREFIX]

MODULE is a parallax_lab importable from tools/ (parallax_lab, or a copy of HEAD's saved there); PREFIX the faults'
start to keep ('(b)' by default). A page lint cannot read prints ERR and the exception's name."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
lab = __import__(sys.argv[1])
prefix = sys.argv[3] if len(sys.argv) > 3 else '(b)'
with open(sys.argv[2]) as f:
    paths = f.read().split('\n')
for path in paths:
    if not path:
        continue
    try:
        page = lab.load(path)
        for fault in lab.lint(page, lab.find_atlas_dir(path, page)):
            if fault.startswith(prefix):
                print(path, fault)
    except Exception as e:  # noqa: BLE001 (a page lint cannot read is a line, not the end)
        print(path, 'ERR', type(e).__name__)
