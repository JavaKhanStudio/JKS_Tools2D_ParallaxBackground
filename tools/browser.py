"""The one Chrome launch of every browser test, lab and shot here (r488): the page draws its WebGL on the GPU.

Headless SwiftShader draws on the CPU, about 5 cores a browser and 3-5 fps on a loaded machine; ANGLE on EGL reaches
the real GPU (the system Chrome finds the NVIDIA one, Playwright's chromium the Intel one) at about 57 fps
(deux-generaux r401, atelier r1994). Stills differ from SwiftShader's by antialiasing and animation timing only.

    sys.path.insert(0, <repo>/tools)          # a heredoc run from the repo root: sys.path.insert(0, "tools")
    from browser import launch
    browser = launch(p, chrome)               # p = sync_playwright(); chrome the executable, None for Playwright's
    browser = launch(p, chrome, "soft")       # SwiftShader, asked for

    python3 tools/browser.py flags CHROME     # the same choice for a Chrome command line (tools/browser-test.sh):
                                              # the flags, one per line

PARALLAX_GL=soft (or gpu) overrides what the script asks. When the GPU launch fails, or its page gets no WebGL or
SwiftShader anyway (CI, a machine with no GPU), it falls back to SwiftShader and says so on stderr. Headless
SwiftShader still runs at a low frame rate: test motion against a threshold, never a wall-clock window."""
import os
import subprocess
import sys
import tempfile

GPU = ["--use-gl=angle", "--use-angle=gl-egl", "--ignore-gpu-blocklist", "--enable-gpu"]
SOFT = ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"]
RENDERER = """() => { const g = document.createElement('canvas').getContext('webgl');
  if (!g) return '';
  const x = g.getExtension('WEBGL_debug_renderer_info');
  return g.getParameter(x ? x.UNMASKED_RENDERER_WEBGL : g.RENDERER); }"""


def _choice(gl):
    return os.environ.get("PARALLAX_GL") or gl or "gpu"


def _fallback(why):
    print(f"browser.py: no GPU ({why}), drawing on the CPU (SwiftShader)", file=sys.stderr)


def launch(p, chrome=None, gl=None, **kw):
    """A chromium Browser from a sync_playwright() handle: chrome its executable (None: Playwright's chromium);
    gl 'gpu' (the default) or 'soft'."""
    where = {"executable_path": chrome} if chrome else {"channel": "chromium"}
    from playwright.sync_api import Error as PlaywrightError

    if _choice(gl) == "gpu":
        try:
            browser = p.chromium.launch(args=GPU, **where, **kw)
            page = browser.new_page()
            renderer = page.evaluate(RENDERER)
            page.close()
            if renderer and "swiftshader" not in renderer.lower():
                return browser
            browser.close()
            why = "its WebGL is " + (renderer or "missing")
        except PlaywrightError as e:  # a GPU launch that cannot start falls back, it never stops the run
            why = str(e).splitlines()[0]
        _fallback(why)
    return p.chromium.launch(args=SOFT, **where, **kw)


def flags(chrome, gl=None):
    """The GL flags of a headless Chrome command line, after one probe of the GPU ones."""
    if _choice(gl) == "gpu":
        page = "<script>document.title = (" + RENDERER + ")()</script>"
        with tempfile.TemporaryDirectory() as profile:
            try:
                dom = subprocess.run([chrome, "--headless=new", "--user-data-dir=" + profile, "--no-first-run", *GPU,
                                      "--dump-dom", "data:text/html," + page], check=False, capture_output=True,
                                     text=True, timeout=30).stdout
                renderer = dom.split("<title>")[1].split("</title>")[0] if "<title>" in dom else ""
                why = "its WebGL is " + (renderer or "missing")
            except (OSError, subprocess.TimeoutExpired) as e:
                renderer, why = "", str(e)
        if renderer and "swiftshader" not in renderer.lower():
            return GPU
        _fallback(why)
    return SOFT


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "flags":
        sys.exit("usage: python3 tools/browser.py flags CHROME")
    print("\n".join(flags(sys.argv[2])))
