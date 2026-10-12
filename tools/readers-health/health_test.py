#!/usr/bin/env python3
"""health_test.py — health.py's verdicts on a made-up checkout: a FAIL report makes its reader Failing, a round never
run makes it Stale, all PASS makes it Healthy, a missing program Not installed, and only a round it plays, changed, makes it Stale (r23).
Plain Python 3: python3 tools/readers-health/health_test.py"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import health


def checkout(reports):
    root = tempfile.mkdtemp()
    for rd in ('engines/rounds/a', 'engines/rounds/b', 'engines/rounds/bench'):
        os.makedirs(os.path.join(root, rd))
        with open(os.path.join(root, rd, 'round.json'), 'w') as f:
            json.dump({'scenes': [{'id': 'x1', 'name': 'x', 'about': ''}, {'id': 'x2', 'name': 'y', 'about': ''}]}, f)
    os.makedirs(os.path.join(root, 'tools'))
    with open(os.path.join(root, 'tools/check.sh'), 'w') as f:
        f.write('[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/rounds/a engines/rounds/b)\n')
    for name, last in reports.items():
        os.makedirs(os.path.join(root, 'build/fake', name))
        with open(os.path.join(root, 'build/fake', name, 'report.txt'), 'w') as f:
            f.write(f'x1-t0.png  mean diff 0.1 / 255\n{last}\n')
    return root


def status(reports, needs='python3'):
    health.ROOT = checkout(reports)
    health.READERS = [{'id': 'fake', 'name': 'Fake', 'role': '', 'needs': needs, 'check': 'tools/check.sh', 'watch': []}]
    return health.gather(False)['readers'][0]


ok = 'PASS: worst mean diff 0.30 (threshold 2.0)'
cases = [
    ('a FAIL fails', status({'a': ok, 'b': 'FAIL: worst mean diff 9.10 (threshold 2.0)'}), 'fail'),
    ('a round never run is stale', status({'a': ok}), 'stale'),
    ('every round passed is healthy', status({'a': ok, 'b': ok}), 'ok'),
    ('no run at all', status({}), 'never'),
    ('a missing program', status({'a': ok, 'b': ok}, needs='no-such-program-r17'), 'absent'),
]
failed = 0
for name, row, want in cases:
    good = row['status'] == want
    failed += not good
    print(('ok   ' if good else 'FAIL ') + f"{name}: {row['status']} ({row['why']}), want {want}")


def after_commit(path):
    """All PASS, run an hour ago, then one commit touching only `path`: the status it leaves (r23)."""
    health.ROOT = checkout({'a': ok, 'b': ok})
    git = ['git', '-C', health.ROOT, '-c', 'user.name=t', '-c', 'user.email=t@t']
    for rep in ('a', 'b'):
        f = os.path.join(health.ROOT, 'build/fake', rep, 'report.txt')
        os.utime(f, (os.path.getmtime(f) - 3600,) * 2)
    with open(os.path.join(health.ROOT, path, 'round.json'), 'a') as f:
        f.write(' ')
    for cmd in (['init', '-q'], ['add', path], ['commit', '-qm', 'touch ' + path]):
        subprocess.run(git + cmd, check=True)
    health.READERS = [{'id': 'fake', 'name': 'Fake', 'role': '', 'needs': 'python3', 'check': 'tools/check.sh', 'watch': []}]
    return health.gather(False)['readers'][0]


cases += [
    ('a round it plays changed is stale', after_commit('engines/rounds/a'), 'stale'),
    ('a round it does not play changed leaves it healthy', after_commit('engines/rounds/bench'), 'ok'),
]
for name, row, want in cases[-2:]:
    good = row['status'] == want
    failed += not good
    print(('ok   ' if good else 'FAIL ') + f"{name}: {row['status']} ({row['why']}), want {want}")
row = status({'a': ok, 'b': ok})
assert row['count'] == 12, row['count']  # 2 rounds x 2 scenes x 3 stills
print('health_test: ' + ('PASS' if not failed else f'{failed} FAIL'))
sys.exit(1 if failed else 0)
