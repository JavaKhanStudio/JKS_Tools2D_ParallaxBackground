#!/usr/bin/env python3
"""serve.py ROOT OUT PORT_FILE — the three.js runner's server: serves the repository ROOT read-only, saves each
still or result the page POSTs to /…/save/<name>.png or .json into OUT, and stops when it POSTs /…/done (what the page
logged, written to OUT/threejs.log). Writes the port it took to PORT_FILE once it listens.
engines/threejs/tools/run-page.sh runs it."""
import http.server
import os
import re
import sys

root, out, port_file = sys.argv[1], sys.argv[2], sys.argv[3]
NAME = re.compile(r'^[\w.-]+\.(png|json)$')


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=root, **kw)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, *a):
        pass

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        path = self.path.split('?')[0]
        name = path.rsplit('/', 1)[-1]
        if '/save/' in path and NAME.match(name):
            with open(os.path.join(out, name), 'wb') as f:
                f.write(body)
        elif path.endswith('/done'):
            with open(os.path.join(out, 'threejs.log'), 'wb') as f:
                f.write(body)
            self.server.done = True
        else:
            self.send_error(404)
            return
        self.send_response(204)
        self.end_headers()


Handler.extensions_map['.js'] = 'text/javascript'
# One request at a time: done is seen as soon as its request is handled.
server = http.server.HTTPServer(('127.0.0.1', 0), Handler)
server.done = False
with open(port_file, 'w') as f:
    f.write(str(server.server_address[1]))
while not server.done:
    server.handle_request()
