#!/usr/bin/env python3
"""health_test.py — health.py's verdicts on a made-up checkout: a FAIL report makes its reader Failing, a round never
run makes it Stale, all PASS makes it Healthy, a missing program Not installed, and only a round it plays, changed, makes it Stale (r23);
the feature table judges each feature by its own scenes, and shows a feature no scene checks as "no round" (r28).
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


def feature_table():
    """r28: features.json's features judged scene by scene. Round a: x1 passes, x2 fails at 3.0; round b is not
    played; round c is played but never drawn (unsupported, for feature 'gone')."""
    health.ROOT = checkout({'a': 'FAIL: worst mean diff 3.00 (threshold 2.0)'})
    with open(os.path.join(health.ROOT, 'build/fake/a/report.txt'), 'w') as f:
        f.write('x1-t0.png  mean diff 0.10 / 255\nx1-t6.png  mean diff 0.40 / 255\n'
                'x2-t0.png  mean diff 3.00 / 255\nFAIL: worst mean diff 3.00 (threshold 2.0)\n')
    os.makedirs(os.path.join(health.ROOT, 'engines/rounds/c'))
    with open(os.path.join(health.ROOT, 'engines/rounds/c/round.json'), 'w') as f:
        json.dump({'scenes': [{'id': 'z1'}]}, f)
    with open(os.path.join(health.ROOT, 'tools/check.sh'), 'w') as f:
        f.write('[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/rounds/a)\n')
    with open(os.path.join(health.ROOT, health.FEATURES), 'w') as f:
        json.dump({'features': [
            {'id': 'one', 'name': 'one', 'scenes': {'a': ['x1']}},
            {'id': 'two', 'name': 'two', 'scenes': {'a': '*', 'b': ['x1']}},
            {'id': 'none', 'name': 'none', 'scenes': {}},
            {'id': 'typo', 'name': 'typo', 'scenes': {'a': ['x1', 'x9']}},
            {'id': 'elsewhere', 'name': 'elsewhere', 'scenes': {'b': '*'}},
            {'id': 'gone', 'name': 'gone', 'scenes': {'c': '*', 'a': ['x1']}},
        ]}, f)
    health.READERS = [{'id': 'fake', 'name': 'Fake', 'role': '', 'needs': 'python3', 'check': 'tools/check.sh', 'watch': [],
                       'unsupported': {'engines/rounds/c': ('r0', 'cannot', 'gone')}}]
    return {r['feature']['id']: r for r in health.gather(False)['features']}


rows = feature_table()
cell = {k: (r['cells']['fake'] or {}).get('kind', 'no round') + ' ' + (r['cells']['fake'] or {}).get('text', '') for k, r in rows.items()}
cases = [
    ('a feature whose one scene passes', cell['one'], 'pass 0.40'),
    ('a feature with one failing scene of its round', cell['two'], 'fail 1 FAIL'),
    ('a feature no scene checks', cell['none'], 'no round '),
    ('a feature only in a round the reader does not play', cell['elsewhere'], 'na not played'),
    ('a feature the reader cannot draw', cell['gone'], 'na not drawn'),
    ('a scene id no round.json has', str(rows['typo']['unknown']), "[('engines/rounds/a', 'x9')]"),
    ('scene ids shown as runs', health.scene_span(['s01', 's02', 's03', 's05', 'q1']), 's01-s03, s05, q1'),
]
for name, got, want in cases:
    good = got == want
    failed += not good
    print(('ok   ' if good else 'FAIL ') + f"{name}: {got}, want {want}")
print('health_test: ' + ('PASS' if not failed else f'{failed} FAIL'))
sys.exit(1 if failed else 0)
