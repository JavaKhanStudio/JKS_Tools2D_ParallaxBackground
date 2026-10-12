#!/usr/bin/env python3
"""serve.py PORT — the readers' dashboard served on loopback, for the board's corner button (parallax-readers:r17).

Serves build/ (the dashboard links the compare sheets under build/<reader>/ as ../<reader>/) and rebuilds
build/readers-health/index.html each time the page is asked for, so a server left running never shows an old
verdict: a build is 0.05 s. One request at a time, so two builds never write the page at once. The last CI run
(gh, ~3 s) is read again at most every CI_EVERY seconds. / goes to /readers-health/. tools/readers-health.sh --serve PORT starts it; Atelier's [viewers.readers_health] says the url.
"""
import functools
import http.server
import os
import sys
import time

import health

CI_EVERY = 300
_ci = {'at': 0.0, 'run': None}
_read_ci = health.ci


def ci_cached():
    if time.time() - _ci['at'] > CI_EVERY:
        _ci['run'], _ci['at'] = _read_ci(), time.time()
    return _ci['run']


def build(out):
    health.OUT = out                         # page() links the compare sheets relative to it
    with open(os.path.join(out, 'index.html'), 'w') as f:
        f.write(health.page(health.gather(True)))


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split('?', 1)[0]
        if path == '/':
            self.send_response(302)
            self.send_header('Location', '/readers-health/')
            self.end_headers()
            return
        if path in ('/readers-health/', '/readers-health/index.html'):
            build(os.path.join(self.directory, 'readers-health'))
        super().do_GET()

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, *a):
        pass


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('usage: serve.py PORT')
    health.ci = ci_cached
    out = os.path.join(health.ROOT, 'build')
    os.makedirs(os.path.join(out, 'readers-health'), exist_ok=True)
    build(os.path.join(out, 'readers-health'))
    port = int(sys.argv[1])
    print(f'readers dashboard: http://127.0.0.1:{port}/', flush=True)
    http.server.HTTPServer(('127.0.0.1', port), functools.partial(Handler, directory=out)).serve_forever()
