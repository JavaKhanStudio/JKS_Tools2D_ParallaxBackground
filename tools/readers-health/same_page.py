#!/usr/bin/env python3
"""same_page.py OLD_HEALTH_PY — proves a refactor of health.py changed nothing it says (r530): loads OLD_HEALTH_PY
(e.g. `git show HEAD~1:tools/readers-health/health.py > /tmp/x/health.py`) beside this checkout's health.py, gathers the
same build/ results with each (no CI), and compares what each gathered, the dashboard page and the text summary each
builds from the same data, and --check-features. Exits 1 on any difference. Plain Python 3."""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
    mod.OUT = os.path.join(mod.ROOT, 'build', 'readers-health')
    return mod


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    old, new = load('health_old', sys.argv[1]), load('health_new', os.path.join(HERE, 'health.py'))
    d_old, d_new = old.gather(False), new.gather(False)
    d_new['now'] = d_old['now']
    bad = []
    if d_old != d_new:
        bad.append('gather() differs')
    for d in (d_old, d_new):
        if old.page(d) != new.page(d):
            bad.append('page() differs')
        if old.text(d) != new.text(d):
            bad.append('text() differs')
    if old.check_features() != new.check_features():
        bad.append('check_features() differs')
    print(f"{len(old.page(d_old))} bytes of page, {len(d_old['features'])} features, {len(d_old['readers'])} readers: "
          + ('; '.join(sorted(set(bad))) if bad else 'SAME'))
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
