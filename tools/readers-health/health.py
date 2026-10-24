#!/usr/bin/env python3
"""health.py OUT_DIR [--no-ci] — the readers' dashboard (parallax-readers:r17): every reader, what checks it, when they
last ran and how, and its latency on the bench round. tools/readers-health.sh builds, opens and runs it.

Reads, never runs: each reader's frame check (the rounds in its default ROUNDS line, their scenes in round.json), the
verdicts the checks left in build/<reader>/<round>/report.txt, core's JUnit results (core/build/test-results), the
bench files (build/bench/<reader>.json, shots/.../ParallaxBench's shape), git (what changed since a run), and the last
CI run on develop through gh (skipped with --no-ci, or when gh fails). Writes OUT_DIR/index.html, self-contained but
for its links to the compare sheets, and prints the same as text.

A new reader is one entry in READERS (engines/README.md, "What a new reader must have"). The feature table (r28) reads
engines/rounds/features.json: each feature's scenes, judged scene by scene from the same reports, and libGDX's by its
browser cases' names in core's JUnit results.
"""
import datetime
import glob
import html
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

# check: its frame check, whose default ROUNDS line says what it plays. job: its job's name in ci.yml. needs: the
# program it cannot run without. watch: what, changed after its last run, makes that run stale; a reader with a check
# watches the rounds it plays too, and only those (r23: a commit to engines/rounds/bench stales nobody). bench: how its
# build/bench/<id>.json is made, or the task that will make it. unsupported: a round it does not play: (its task, why, the features
# it cannot draw, by features.json's id: one or more) (r27, r28, r39).
READERS = [
    {'id': 'gdx', 'name': 'libGDX', 'folder': 'core', 'role': 'the reference: every other reader is checked against its stills',
     'needs': 'java', 'jobs': ['Build and test (JDK 17)', 'Build and test (JDK 21)', 'Build and test (JDK 25)'],
     'watch': ['core/src', 'core/src-jvm', 'core/test', 'shots'],
     'run': './gradlew :core:test && tools/parallax-bench.sh', 'bench': 'tools/parallax-bench.sh'},
    {'id': 'godot', 'name': 'Godot 4', 'folder': 'engines/godot', 'role': 'a GDScript port of core, line for line',
     'needs': 'godot', 'check': 'engines/godot/tools/frame-check.sh', 'jobs': ['Godot frames against libGDX'],
     'watch': ['engines/godot', 'core/src', 'engines/rounds/reader'],
     'run': 'engines/godot/tools/frame-check.sh && engines/godot/tools/bench.sh', 'bench': 'engines/godot/tools/bench.sh',
     'unsupported': {'engines/rounds/rings': ('r49', 'the rings (a page round a 3D world) are not ported yet', 'rings')}},
    {'id': 'jme', 'name': 'jMonkeyEngine 3', 'folder': 'engines/jme', 'role': "runs core's reader as is, through JmeBatch",
     'needs': 'java', 'check': 'engines/jme/tools/frame-check.sh', 'jobs': ['jME frames against libGDX'],
     'watch': ['engines/jme', 'core/src', 'engines/rounds/reader'],
     'run': 'engines/jme/tools/frame-check.sh && engines/jme/tools/bench.sh', 'bench': 'engines/jme/tools/bench.sh',
     'unsupported': {'engines/rounds/rings': ('r50', 'the rings (a page round a 3D world) are not ported yet', 'rings')}},
    {'id': 'threejs', 'name': 'three.js', 'folder': 'engines/threejs',
     'role': "an ES module port of core for three.js, drawn in headless Chrome (r34, from Crypto en visuel's reader)",
     'needs': 'google-chrome', 'check': 'engines/threejs/tools/frame-check.sh', 'jobs': ['three.js frames against libGDX'],
     'watch': ['engines/threejs', 'core/src'],
     'run': 'engines/threejs/tools/frame-check.sh && engines/threejs/tools/bench.sh', 'bench': 'engines/threejs/tools/bench.sh',
     'unsupported': {
         'engines/rounds/dolly': ('r43', 'the dolly is not ported yet', 'dolly'),
         'engines/rounds/tower': ('r43', 'towers are not ported yet', 'tower'),
         'engines/rounds/planet': ('r43', 'planets are not ported yet', 'planet'),
         # And the animated round's a03, a04 (dolly, planet) and the shaders round's s12-s18 (frame-check.sh's SKIP):
         # the features dolly, planet, mirror and detail are drawn there too.
         'engines/rounds/glint': ('r44', 'glint, MIRROR and detail levels are not ported yet', 'glint', 'mirror', 'detail'),
     }},
    {'id': 'unity', 'name': 'Unity', 'folder': None, 'role': 'not written: parallax:r103, paused on its install',
     'needs': 'unity', 'paused': True},
]


def sh(*cmd):
    try:
        return subprocess.run(cmd, check=False, cwd=ROOT, capture_output=True, text=True, timeout=60).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return ''


def when(ts):
    return datetime.datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M') if ts else 'never'


def age(ts, now):
    if not ts:
        return 'never'
    d = now - ts
    return f'{int(d // 60)} min ago' if d < 3600 else f'{int(d // 3600)} h ago' if d < 172800 else f'{int(d // 86400)} days ago'


def rounds_of(check):
    """The rounds a frame check plays when given none: its `ROUNDS=(...)` default."""
    with open(os.path.join(ROOT, check)) as f:
        m = re.search(r'-eq 0 \] && ROUNDS=\(([^)]*)\)', f.read())
    return m.group(1).split() if m else []


def scenes_of(round_dir):
    with open(os.path.join(ROOT, round_dir, 'round.json')) as f:
        return json.load(f)['scenes']


def report(reader, round_dir):
    """The verdict build/<reader>/<round>/report.txt holds: (PASS/FAIL, worst diff or None, time) or None."""
    path = os.path.join(ROOT, 'build', reader, os.path.basename(round_dir), 'report.txt')
    if not os.path.exists(path):
        return None
    with open(path) as f:
        lines = [l for l in f.read().splitlines() if l.strip()]
    last = lines[-1] if lines else ''
    verdict = 'PASS' if 'PASS' in last and 'FAIL' not in last else 'FAIL'
    m = re.search(r'worst mean diff ([\d.]+)', last)
    return verdict, float(m.group(1)) if m else None, os.path.getmtime(path), os.path.relpath(os.path.dirname(path), ROOT)


FEATURES = 'engines/rounds/features.json'


def features():
    """engines/rounds/features.json's features, [] when there is none."""
    path = os.path.join(ROOT, FEATURES)
    if not os.path.exists(path):
        return []
    with open(path) as f:
        return json.load(f)['features']


def scene_verdicts(reader, round_dir):
    """Each scene's verdict in build/<reader>/<round>/report.txt: {scene id: (PASS/FAIL, worst mean diff or None)}.
    A still over the report's threshold, or over the round's pixelsOff, fails its scene, as compare-parallax-frames.py
    judges the round; the particles round's report names its failing shots (FAIL q03-t6: ...)."""
    path = os.path.join(ROOT, 'build', reader, os.path.basename(round_dir), 'report.txt')
    if not os.path.exists(path):
        return {}
    with open(path) as f:
        lines = [l for l in f.read().splitlines() if l.strip()]
    if not lines:
        return {}
    m = re.search(r'threshold ([\d.]+)', lines[-1])
    threshold = float(m.group(1)) if m else 2.0
    with open(os.path.join(ROOT, round_dir, 'round.json')) as f:
        pixels_off = json.load(f).get('pixelsOff')
    out = {}
    for l in lines:
        m = re.match(r'(\S+?)-t\d+\.png\s+mean diff ([\d.]+) / 255(?:\s+pixels off by >32: ([\d.]+)%)?', l)
        if m:
            sid, mean = m.group(1), float(m.group(2))
            bad = mean > threshold or (pixels_off is not None and m.group(3) is not None and float(m.group(3)) > pixels_off)
            was = out.get(sid, ('PASS', 0.0))
            out[sid] = ('FAIL' if bad or was[0] == 'FAIL' else 'PASS', max(was[1], mean))
            continue
        # the particles round names a failing shot (FAIL q03-t6: ...), the rings round a scene (FAIL g03: ..., r47)
        m = re.match(r'(\S+?)-t\d+\.png\s+(MISSING|size)', l) or re.match(r'FAIL (\w+?)(?:-t\d+|:)', l)
        if m:
            out[m.group(1)] = ('FAIL', None)
    if not any(v[1] is not None for v in out.values()):
        # No still compared by pixels (the particles round): the scenes it names failed, the others took its verdict.
        verdict = 'PASS' if 'PASS' in lines[-1] and 'FAIL' not in lines[-1] else 'FAIL'
        for s in scenes_of(round_dir):
            out.setdefault(s['id'], (verdict if verdict == 'PASS' or not out else 'PASS', None))
    return out


def case_names(cls, pattern):
    """The browser cases of core/browser-test/cases' class `cls` whose names match `pattern` (all when None)."""
    names = []
    for f in glob.glob(os.path.join(ROOT, 'core/browser-test/cases/**', cls + '.java'), recursive=True):
        with open(f) as fh:
            names += re.findall(r'new BrowserCase\("([^"]+)"', fh.read())
    return [n for n in names if pattern is None or re.search(pattern, n)]


def junit_cases():
    """Every JUnit test case core last ran: {name: passed}."""
    out = {}
    for f in glob.glob(os.path.join(ROOT, 'core/build/test-results/test/TEST-*.xml')):
        for c in ET.parse(f).getroot().iter('testcase'):
            out[c.get('name')] = c.find('failure') is None and c.find('error') is None
    return out


def scene_list(spec):
    """features.json's scenes, expanded: [(round dir, [scene ids], [ids no round.json has])]."""
    out = []
    for name, ids in spec.items():
        rd = 'engines/rounds/' + name
        try:
            known = [s['id'] for s in scenes_of(rd)]
        except OSError:
            out.append((rd, [], ['*'] if ids == '*' else list(ids)))
            continue
        ids = known if ids == '*' else list(ids)
        out.append((rd, [i for i in ids if i in known], [i for i in ids if i not in known]))
    return out


def feature_cell(reader, fid, scenes, cases):
    """One reader's verdict on one feature: {'kind': pass/fail/never/na, 'text', 'small'}."""
    if reader.get('paused'):
        return {'kind': 'na', 'text': 'paused', 'small': ''}
    if reader['id'] == 'gdx':
        names = [n for cls, pattern in cases for n in case_names(cls, pattern)]
        if not names:
            return {'kind': 'na', 'text': 'no case', 'small': 'draws the stills the others match'}
        ran = junit_cases()
        seen = [n for n in names if n in ran]
        if not seen:
            return {'kind': 'never', 'text': 'never run', 'small': f'{len(names)} cases'}
        bad = [n for n in seen if not ran[n]]
        return {'kind': 'fail' if bad else 'pass', 'text': f'{len(bad)} FAIL' if bad else 'PASS',
                'small': f'{len(seen)} cases' + (f', {len(names) - len(seen)} not run' if len(seen) < len(names) else '')}
    total = sum(len(ids) for _, ids, _ in scenes)
    played = [(rd, ids) for rd, ids, _ in scenes if rd in reader['rounds']]
    # A feature the reader cannot draw (its 'unsupported') is not drawn, whatever its other scenes pass: jME passes the
    # effects round's e06 by drawing no particles at all.
    refs = sorted({u[0] for u in reader.get('unsupported', {}).values() if fid in u[2:]})
    if refs or not played:
        return {'kind': 'na', 'text': 'not drawn' if refs else 'not played', 'small': ', '.join(refs)}
    verdicts, unrun = [], 0
    for rd, ids in played:
        got = scene_verdicts(reader['id'], rd)
        for i in ids:
            if i in got:
                verdicts.append(got[i])
            else:
                unrun += 1
    n = sum(len(ids) for _, ids in played)
    of = f'{n} of {total} scenes' if n < total else f'{n} scenes'
    if any(v[0] == 'FAIL' for v in verdicts):
        return {'kind': 'fail', 'text': f'{sum(v[0] == "FAIL" for v in verdicts)} FAIL', 'small': of}
    if not verdicts:
        return {'kind': 'never', 'text': 'never run', 'small': of}
    worst = max((v[1] for v in verdicts if v[1] is not None), default=None)
    return {'kind': 'pass' if not unrun else 'never', 'text': 'PASS' if worst is None else f'{worst:.2f}',
            'small': of + (f', {unrun} never run' if unrun else '')}


def feature_rows(readers):
    """The feature table: each feature, its scenes, and every reader's verdict on them."""
    rows = []
    for f in features():
        scenes = scene_list(f.get('scenes', {}))
        rows.append({'feature': f, 'scenes': scenes, 'unknown': [(rd, i) for rd, _, bad in scenes for i in bad],
                     'cells': {r['id']: (feature_cell(r, f['id'], scenes, f.get('cases', []))
                                         if any(ids for _, ids, _ in scenes) else None) for r in readers}})
    return rows


def junit():
    """core's last JUnit run: (tests, failures, time) over core/build/test-results/test."""
    files = glob.glob(os.path.join(ROOT, 'core/build/test-results/test/TEST-*.xml'))
    tests = failures = 0
    for f in files:
        r = ET.parse(f).getroot()
        tests += int(r.get('tests', 0))
        failures += int(r.get('failures', 0)) + int(r.get('errors', 0))
    return (tests, failures, max(os.path.getmtime(f) for f in files)) if files else None


def count(pattern, glob_pattern):
    n = 0
    for f in glob.glob(os.path.join(ROOT, glob_pattern), recursive=True):
        with open(f, errors='replace') as fh:
            n += len(re.findall(pattern, fh.read()))
    return n


def changed_since(paths, ts):
    """The commits touching paths since ts (what makes a run stale)."""
    if not ts:
        return []
    out = sh('git', 'log', f'--since=@{int(ts)}', '--format=%h %s', '--', *paths)
    return [l for l in out.splitlines() if l]


def ci():
    """The last CI run on develop and its jobs, through gh: {'run':..., 'jobs': {name: (conclusion, time)}} or None."""
    if not shutil.which('gh'):
        return None
    try:
        runs = json.loads(sh('gh', 'run', 'list', '--workflow', 'ci.yml', '--branch', 'develop', '--limit', '30', '--json',
                             'databaseId,headSha,createdAt,conclusion,status,url') or '[]')
        # A cancelled run was superseded (ci.yml's cancel-in-progress): it says nothing of the code, so it never judges.
        done = [r for r in runs if r['status'] == 'completed' and r['conclusion'] != 'cancelled']
        if not done:
            return None
        # The last finished run judges; one still running on a newer commit is only said.
        run = done[0]
        run['running'] = next((r['headSha'][:7] for r in runs if r['status'] != 'completed' and r['createdAt'] > run['createdAt']), None)
        view = json.loads(sh('gh', 'run', 'view', str(run['databaseId']), '--json', 'jobs') or '{}')
        jobs = {j['name']: (j.get('conclusion') or j.get('status'), j.get('completedAt') or '') for j in view.get('jobs', [])}
        return {'run': run, 'jobs': jobs}
    except (ValueError, KeyError):
        return None


def ci_covers(ci_run, jobs, paths):
    """Every one of a reader's CI jobs passed on a commit after the last change to its paths."""
    if not (ci_run and jobs and all(j[1] == 'success' for j in jobs)):
        return False
    return not sh('git', 'log', '--format=%h', ci_run['run']['headSha'] + '..HEAD', '--', *paths)


def bench(reader_id):
    path = os.path.join(ROOT, 'build', 'bench', reader_id + '.json')
    if not os.path.exists(path):
        return None
    with open(path) as f:
        data = json.load(f)
    data['time'] = os.path.getmtime(path)
    return data


def gather(use_ci):
    now = datetime.datetime.now().timestamp()
    ci_run = ci() if use_ci else None
    bench_scenes = scenes_of('engines/rounds/bench')
    rows = []
    for r in READERS:
        row = dict(r)
        row['installed'] = bool(shutil.which(r['needs'])) and not r.get('paused')
        row['rounds'] = {}
        checks, runs = [], []
        if r.get('check'):
            for rd in rounds_of(r['check']):
                scenes = scenes_of(rd)
                rep = report(r['id'], rd)
                stills = len(scenes) * (1 if os.path.basename(rd) == 'particles' else 3)
                row['rounds'][rd] = {'scenes': len(scenes), 'stills': stills, 'report': rep}
                checks.append(stills)
                if rep:
                    runs.append(rep)
            row['checks'] = f"{sum(checks)} stills over {len(checks)} rounds"
            row['count'] = sum(checks)
        elif r['id'] == 'gdx':
            tests = count(r'@Test\b', 'core/test/**/*.java')
            cases = count(r'new BrowserCase\(', 'core/browser-test/cases/**/*.java')
            row['checks'] = f'{tests} JUnit tests, {cases} browser cases; draws the stills the others match'
            row['count'] = tests + cases
            j = junit()
            if j:
                runs.append(('FAIL' if j[1] else 'PASS', None, j[2], 'core/build/reports/tests/test'))
                row['junit'] = j
        else:
            row['checks'], row['count'] = 'none', 0
        row['last'] = max((x[2] for x in runs), default=0)
        row['first'] = min((x[2] for x in runs), default=0)
        row['failed'] = [x for x in runs if x[0] == 'FAIL']
        row['missing'] = [rd for rd, v in row['rounds'].items() if not v['report']]
        watch = r.get('watch', []) + list(row['rounds'])
        row['changed'] = changed_since(watch, row['first']) if runs else []
        row['ci'] = [(name,) + ci_run['jobs'][name] for name in r.get('jobs', []) if ci_run and name in ci_run['jobs']]
        row['bench'] = bench(r['id'])
        if r.get('paused'):
            row['status'], row['why'] = 'paused', 'not installed on this machine'
        elif not row['installed']:
            row['status'], row['why'] = 'absent', f"{r['needs']} not found"
        elif row['failed']:
            row['status'], row['why'] = 'fail', f"{len(row['failed'])} check(s) FAIL"
        elif not runs:
            row['status'], row['why'] = 'never', 'never run on this machine'
        elif row['missing']:
            row['status'], row['why'] = 'stale', f"{len(row['missing'])} round(s) never run here"
        elif row['changed'] and not ci_covers(ci_run, row['ci'], watch):
            row['status'], row['why'] = 'stale', f"{len(row['changed'])} commit(s) since its oldest run"
        elif row['changed']:
            row['status'], row['why'] = 'ok', 'CI passed since its last change; changed since its last run here'
        else:
            row['status'], row['why'] = 'ok', 'every check passed, nothing changed since'
        if any(c[1] in ('failure', 'cancelled', 'timed_out') for c in row['ci']):
            row['status'], row['why'] = 'fail', 'CI: ' + ', '.join(f'{c[0]} {c[1]}' for c in row['ci'] if c[1] != 'success')
        rows.append(row)
    head = sh('git', 'log', '-1', '--format=%h %s')
    return {'now': now, 'head': head, 'ci': ci_run, 'readers': rows, 'bench_scenes': bench_scenes,
            'features': feature_rows(rows)}


STATUS = {'ok': ('Healthy', '#1a7f37'), 'stale': ('Stale', '#9a6700'), 'never': ('Never run', '#9a6700'),
          'fail': ('Failing', '#cf222e'), 'absent': ('Not installed', '#6e7781'), 'paused': ('Paused', '#6e7781')}


def text(d):
    out = [f"Readers' health, {when(d['now'])}, at {d['head']}"]
    if d['ci']:
        r = d['ci']['run']
        out.append(f"CI on develop: {r['conclusion'] or r['status']} at {r['headSha'][:7]}, {r['createdAt']}")
    for r in d['readers']:
        out.append(f"  {r['name']:16} {STATUS[r['status']][0]:13} {r['why']}; {r['checks']}; last run {age(r['last'], d['now'])}")
    lost = [f['feature']['name'] for f in d['features'] if not any(ids for _, ids, _ in f['scenes'])]
    out.append(f"  {len(d['features'])} features" + (f"; no round: {', '.join(lost)}" if lost else ''))
    for f in d['features']:
        for rd, i in f['unknown']:
            out.append(f"  {FEATURES} names {i} in {rd}, which has no such scene")
    return '\n'.join(out)


def page(d):
    e = html.escape
    now = d['now']
    readers = d['readers']
    played = [r for r in readers if r.get('check')]
    all_rounds = []
    for r in played:
        for rd in r['rounds']:
            if rd not in all_rounds:
                all_rounds.append(rd)
    p = ['<!doctype html><meta charset="utf-8"><title>Readers health</title>', f'<style>{CSS}</style>',
         '<h1>Readers health</h1>',
         (f'<p class="sub">Built {e(when(now))} at <code>{e(d["head"])}</code>. '
          'Rebuild: <code>tools/readers-health.sh</code>; rerun every check first: <code>tools/readers-health.sh --run</code>.</p>')]
    if d['ci']:
        r = d['ci']['run']
        p.append(f'<p class="sub">Last CI on develop: <a href="{e(r["url"])}">{e(r["conclusion"] or r["status"])}</a> '
                 f'at <code>{e(r["headSha"][:7])}</code>, {e(r["createdAt"].replace("T", " ")[:16])} UTC'
                 + (f'; a run on <code>{e(r["running"])}</code> is still going' if r['running'] else '') + '.</p>')
    else:
        p.append('<p class="sub">CI: not read (gh missing, offline, or --no-ci).</p>')

    p.append('<h2>Readers</h2><div class="cards">')
    for r in readers:
        label, colour = STATUS[r['status']]
        p.append(f'<div class="card" style="border-top-color:{colour}">'
                 f'<div class="top"><b>{e(r["name"])}</b><span class="badge" style="background:{colour}">{label}</span></div>'
                 f'<div class="role">{e(r["role"])}</div><div class="why">{e(r["why"])}</div><dl>'
                 f'<dt>Checks ready</dt><dd>{e(r["checks"])}</dd>'
                 f'<dt>Last run here</dt><dd>{e(age(r["last"], now))}{" (" + e(when(r["last"])) + ")" if r["last"] else ""}</dd>')
        if r['ci']:
            p.append('<dt>CI</dt><dd>' + '<br>'.join(f'{e(c[0])}: <b>{e(str(c[1]))}</b>' for c in r['ci']) + '</dd>')
        b = r['bench']
        if b:
            worst = max(b['scenes'], key=lambda s: s['meanMs'])
            p.append(f'<dt>Latency</dt><dd>{worst["meanMs"]:.2f} ms a frame at worst ({e(worst["id"])}, '
                     f'{worst["layers"]} layers), {age(b["time"], now)}</dd>')
        elif r.get('bench_task'):
            p.append(f'<dt>Latency</dt><dd>not measured yet: parallax-readers:{e(r["bench_task"])}</dd>')
        if r['changed']:
            p.append('<dt>Changed since</dt><dd>' + '<br>'.join(e(c) for c in r['changed'][:4])
                     + (f'<br>and {len(r["changed"]) - 4} more' if len(r['changed']) > 4 else '') + '</dd>')
        if r.get('run'):
            p.append(f'<dt>Run it</dt><dd><code>{e(r["run"])}</code></dd>')
        p.append('</dl></div>')
    p.append('</div>')

    p.append('<h2 id="features">Features: each one, its scenes, each reader</h2><p class="sub">From <code>engines/rounds/features.json</code>. '
             'Godot and jME: the worst still of the scenes that check the feature (mean difference from libGDX, out of 255; a '
             'check fails over 2), judged scene by scene. libGDX: its browser cases for the feature, in core\'s last JUnit run. '
             '"No round": no scene checks it. Tickets are the parallax board\'s.</p>'
             '<table><tr><th>Feature</th><th>Format</th><th>Tickets</th><th>Scenes</th>'
             + ''.join(f'<th>{e(r["name"])}</th>' for r in readers) + '</tr>')
    for row in d['features']:
        f = row['feature']
        where = '; '.join(f'{os.path.basename(rd)} {scene_span(ids)}' for rd, ids, _ in row['scenes'] if ids)
        bad = ''.join(f'<small class="bad">{e(i)}: no such scene in {e(os.path.basename(rd))}</small>' for rd, i in row['unknown'])
        p.append(f'<tr><td>{e(f["name"])}<small>{e(f.get("about", ""))}</small></td>'
                 f'<td class="n">{f.get("format", "")}</td>'
                 f'<td>{e(", ".join("parallax:" + t for t in f.get("tickets", [])))}</td>'
                 + (f'<td>{e(where)}{bad}</td>' if where else f'<td class="fail">no round{bad}</td>'))
        for r in readers:
            c = row['cells'][r['id']]
            p.append('<td class="na">&ndash;</td>' if c is None else
                     f'<td class="{c["kind"]}">{e(c["text"])}<small>{e(c["small"])}</small></td>')
        p.append('</tr>')
    p.append('</table>')

    p.append('<h2>Frame checks: each round, each reader</h2><p class="sub">Mean difference from libGDX\'s stills per '
             'channel, out of 255; a check fails over 2. "Not drawn": the reader cannot draw that round yet, its task named. '
             'A dash: the reader does not play that round.</p>'
             '<table><tr><th>Round</th><th>Scenes</th>' + ''.join(f'<th>{e(r["name"])}</th>' for r in played) + '</tr>')
    for rd in all_rounds:
        name = os.path.basename(rd)
        scenes = next(r['rounds'][rd]['scenes'] for r in played if rd in r['rounds'])
        p.append(f'<tr><td><code>{e(name)}</code></td><td class="n">{scenes}</td>')
        for r in played:
            v = r['rounds'].get(rd)
            if not v and rd in r.get('unsupported', {}):
                ref, why = r['unsupported'][rd][:2]
                p.append(f'<td class="na" title="{e(why)}">not drawn<small>{e(ref)}</small></td>')
            elif not v:
                p.append('<td class="na">&ndash;</td>')
            elif not v['report']:
                p.append('<td class="never">never run</td>')
            else:
                verdict, worst, ts, folder = v['report']
                link = os.path.relpath(os.path.join(ROOT, folder, 'compare.png' if worst is not None else 'report.txt'), OUT)
                cls = 'pass' if verdict == 'PASS' else 'fail'
                val = f'{worst:.2f}' if worst is not None else verdict
                p.append(f'<td class="{cls}"><a href="{e(link)}">{e(val)}</a><small>{e(age(ts, now))}</small></td>')
        p.append('</tr>')
    p.append('</table>')

    p.append('<h2>Latency: the bench round</h2><p class="sub"><code>engines/rounds/bench</code>: each scene loaded (load: '
             'page, atlas and first frame), then 600 frames at 1/60 s, vsync off, each finished on the GPU, each scene\'s best of a '
             'few runs (this GPU is shared: another session on it slows a run tenfold). '
             'Frame time in ms, mean / 95th percentile. Numbers from different GPUs do not compare: each column names its own.</p>')
    benches = [r for r in readers if r['bench']]
    p.append('<table><tr><th>Scene</th><th>Layers</th>' + ''.join(
        f'<th>{e(r["name"])}<small>{e(r["bench"].get("renderer", ""))}<br>{e(r["bench"].get("measure", ""))}, best of {r["bench"].get("runs", 1)}'
        f'<br>{e(age(r["bench"]["time"], now))}</small></th>'
        for r in benches) + '</tr>')
    for s in d['bench_scenes']:
        by = {r['id']: next((x for x in r['bench']['scenes'] if x['id'] == s['id']), None) for r in benches}
        layers = next((x['layers'] for x in by.values() if x), '')
        p.append(f'<tr><td><code>{e(s["id"])}</code> {e(s["name"])}<small>{e(s["about"])}</small></td><td class="n">{layers}</td>')
        for r in benches:
            x = by[r['id']]
            p.append(f'<td class="n">{x["meanMs"]:.2f} / {x["p95Ms"]:.2f}<small>load {x["loadMs"]:.0f} ms</small></td>'
                     if x else '<td class="na">&ndash;</td>')
        p.append('</tr>')
    p.append('</table>')
    missing = [r for r in readers if not r['bench'] and r.get('bench_task')]
    if missing:
        p.append('<p class="sub">No bench yet: ' + ', '.join(
            f'{e(r["name"])} (parallax-readers:{e(r["bench_task"])})' for r in missing) + '.</p>')
    return '\n'.join(p)


def scene_span(ids):
    """s01 s02 s03 s05 as s01-s03, s05."""
    out, run = [], []
    for i in ids + [None]:
        m = re.match(r'([a-z]+)(\d+)$', i or '')
        if run and m and m.group(1) == run[-1][0] and int(m.group(2)) == run[-1][1] + 1:
            run.append((m.group(1), int(m.group(2)), i))
            continue
        if run:
            out.append(run[0][2] if len(run) == 1 else f'{run[0][2]}-{run[-1][2]}')
        run = [(m.group(1), int(m.group(2)), i)] if m else []
        if i and not m:
            out.append(i)
    return ', '.join(out)


CSS = """
:root { color-scheme: light dark; --fg: #1f2328; --mute: #59636e; --line: #d1d9e0; --bg: #fff; --cell: #f6f8fa; }
@media (prefers-color-scheme: dark) { :root { --fg: #e6edf3; --mute: #9198a1; --line: #3d444d; --bg: #0d1117; --cell: #151b23; } }
body { font: 15px/1.45 system-ui, sans-serif; color: var(--fg); background: var(--bg); margin: 24px auto; max-width: 1280px; padding: 0 16px; }
h1 { margin: 0 0 4px; } h2 { margin: 28px 0 6px; font-size: 18px; }
.sub { color: var(--mute); margin: 2px 0; } code { font-size: 13px; }
a { color: inherit; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 12px; }
.card { border: 1px solid var(--line); border-top: 5px solid; border-radius: 8px; padding: 12px 14px; background: var(--cell); }
.top { display: flex; justify-content: space-between; align-items: center; font-size: 17px; }
.badge { color: #fff; border-radius: 10px; padding: 1px 10px; font-size: 13px; }
.role { color: var(--mute); font-size: 14px; } .why { margin: 6px 0; font-weight: 600; font-size: 14px; }
dl { margin: 0; font-size: 14px; } dt { color: var(--mute); margin-top: 6px; font-size: 13px; } dd { margin: 0; overflow-wrap: anywhere; }
table { border-collapse: collapse; font-size: 14px; }
th, td { border: 1px solid var(--line); padding: 4px 10px; text-align: left; vertical-align: top; }
th small, td small { display: block; color: var(--mute); font-weight: normal; font-size: 12px; max-width: 420px; }
td.n { text-align: right; } td.na { color: var(--mute); text-align: center; }
small.bad { color: #cf222e; }
td.pass { background: #1a7f3722; } td.fail { background: #cf222e33; font-weight: 700; } td.never { background: #9a670022; color: var(--mute); }
"""

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 1:
        sys.exit('usage: health.py OUT_DIR [--no-ci]')
    OUT = os.path.abspath(args[0])
    os.makedirs(OUT, exist_ok=True)
    data = gather('--no-ci' not in sys.argv)
    with open(os.path.join(OUT, 'index.html'), 'w') as f:
        f.write(page(data))
    print(text(data))
    print('dashboard: ' + os.path.join(OUT, 'index.html'))
    sys.exit(1 if any(r['status'] == 'fail' for r in data['readers']) else 0)
