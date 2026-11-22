"""r556: the clouds lab's CUMULUS shader drawn alone, in WebGL, in seconds: no GWT build per try.

    python3 tools/r556-clouds/probe.py OUT.png [--time S] [--scroll U] [--style JSON] [--only N] [--frag FILE]

Draws CloudsLab.CUMULUS_FRAGMENT's body, read out of CloudsLab.java (--frag FILE: a GLSL file tried in its place), over
each setting's sky gradient and sun or moon, as the lab's sky of three layers (the far bank, the middle, the near
clouds), a dark band standing for the land: the three settings side by side, each a view as tall as wide. The settings'
colours and the layers' numbers copy CloudsLab's (SKY_TOP, SKY_LOW, LIT, SHADE, SKY_LAYERS, LOOKS): change them there,
change them here. A picture to read while tuning, not a test: the lab is what is judged.
"""
import argparse
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.join(HERE, "../../core/browser-test/launcher/jks/tools2d/parallax/browsertest/CloudsLab.java")

WORLD_W = 40.0
SETTINGS = [
    {"name": "Mediterranean day", "top": [0.16, 0.45, 0.86], "low": [0.74, 0.88, 0.97], "lit": [1, 1, 1],
     "shade": [0.50, 0.64, 0.86], "disc": [1, 0.98, 0.88], "sun": [0.30, 0.80], "radius": 1.5,
     "land": [0.20, 0.42, 0.50]},
    {"name": "cold forest night", "top": [0.015, 0.03, 0.085], "low": [0.09, 0.15, 0.27], "lit": [0.50, 0.60, 0.80],
     "shade": [0.045, 0.07, 0.15], "disc": [0.92, 0.94, 1], "sun": [0.68, 0.74], "radius": 1.1,
     "land": [0.02, 0.04, 0.08]},
    {"name": "medieval fantasy", "top": [0.27, 0.33, 0.64], "low": [0.99, 0.74, 0.50], "lit": [1, 0.90, 0.68],
     "shade": [0.56, 0.38, 0.56], "disc": [1, 0.86, 0.55], "sun": [0.76, 0.36], "radius": 2,
     "land": [0.12, 0.25, 0.22]},
]
# A sky's layers, back to front: its foot (world units up), its height, its width, the lobes across it, a slot's length
# (lobes), how tall (lobes), the base, the coverage, the haze toward the horizon's colour, its speed, its seed.
LAYERS = [
    {"foot": 7.99, "height": 11.16, "width": 60, "lobes": 40, "slot": 5, "tall": 1.6, "base": 0.12, "coverage": 0.75,
     "haze": 0.55, "speed": 0.35, "seed": 1},
    {"foot": 12.49, "height": 13.39, "width": 72, "lobes": 22, "slot": 4, "tall": 2.0, "base": 0.10, "coverage": 0.6,
     "haze": 0.25, "speed": 0.6, "seed": 5},
    {"foot": 20.03, "height": 26.04, "width": 140, "lobes": 22, "slot": 4, "tall": 2.4, "base": 0.08, "coverage": 0.6,
     "haze": 0.0, "speed": 1, "seed": 11},
]
STYLE = {"soft": 0.03, "flat": 0.8, "rim": 0.6, "opacity": 1, "wisps": 0.3}

HEAD = """precision highp float;
uniform vec4 u_viewport;
uniform vec2 u_world;
uniform vec4 u_layer;
uniform vec2 u_size;
uniform vec3 u_sun;
uniform vec3 u_lit;
uniform vec3 u_shade;
uniform vec3 u_fog;
uniform float u_haze;
const float TAU = 6.2831853;
const vec4 v_color = vec4(1.0);
vec2 v_view;
vec4 hazed(vec4 color)
{
	return vec4(mix(color.rgb, u_fog, u_haze), color.a);
}
vec2 local()
{
	vec2 w = v_view * u_world;
	return vec2(mod(w.x - u_layer.x, u_size.x), w.y - u_layer.y);
}
vec2 sunward()
{
	vec2 d = (u_sun.xy - v_view) * vec2(u_sun.z, 1.0);
	return d / max(length(d), 0.0001);
}
float sunNear()
{
	return 1.0 - smoothstep(0.0, 0.55, length((u_sun.xy - v_view) * vec2(u_sun.z, 1.0)));
}
#define main cloudMain
"""
TAIL = """
#undef main
void main()
{
	v_view = (gl_FragCoord.xy - u_viewport.xy) / u_viewport.zw;
	float y = v_view.y * u_world.y - u_layer.y;
	if (y < 0.0 || y > u_size.y)
		discard;
	cloudMain();
}
"""
SKY = """precision highp float;
uniform vec4 u_viewport;
uniform vec2 u_world;
uniform vec3 u_top, u_low, u_disc, u_land;
uniform vec3 u_sun;
uniform float u_radius;
uniform float u_pass;
void main()
{
	vec2 v = (gl_FragCoord.xy - u_viewport.xy) / u_viewport.zw;
	if (u_pass > 0.5)
	{
		float ridge = 7.0 + 1.2 * sin(v.x * 9.0) + 0.6 * sin(v.x * 23.0 + 1.0) + 0.3 * sin(v.x * 61.0);
		if (v.y * u_world.y > ridge)
			discard;
		gl_FragColor = vec4(mix(u_land, u_low, 0.25 * v.y * u_world.y / 8.0), 1.0);
		return;
	}
	vec3 c = mix(u_low, u_top, smoothstep(0.2, 1.0, v.y));
	float r = length((v - u_sun.xy) * u_world) / u_radius;
	float a = max(clamp((1.0 - r) * 12.0, 0.0, 1.0), 0.45 * pow(max(0.0, 1.0 - r / 3.3), 2.0));
	gl_FragColor = vec4(mix(c, u_disc, a), 1.0);
}
"""
PAGE = """<!DOCTYPE html><canvas id=c></canvas><script>
const cfg = CONFIG;
const c = document.getElementById('c'); c.width = cfg.width; c.height = cfg.height;
const gl = c.getContext('webgl', {preserveDrawingBuffer: true, antialias: false, premultipliedAlpha: false});
window.errors = [];
function prog(fs) {
  const p = gl.createProgram();
  for (const [t, s] of [[gl.VERTEX_SHADER, 'attribute vec2 a; void main() { gl_Position = vec4(a, 0., 1.); }'], [gl.FRAGMENT_SHADER, fs]]) {
    const sh = gl.createShader(t); gl.shaderSource(sh, s); gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) window.errors.push(gl.getShaderInfoLog(sh));
    gl.attachShader(p, sh);
  }
  gl.bindAttribLocation(p, 0, 'a'); gl.linkProgram(p); return p;
}
const sky = prog(cfg.sky), cloud = prog(cfg.cloud);
gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
gl.enable(gl.SCISSOR_TEST);
function u(p, n, ...v) { const l = gl.getUniformLocation(p, n); if (l) gl['uniform' + v.length + 'f'](l, ...v); }
window.draw = function (time, scroll) {
  const n = cfg.settings.length, gap = 4, w = Math.floor((cfg.width - gap * (n - 1)) / n), h = cfg.height;
  const world = [cfg.worldW, cfg.worldW * h / w];
  cfg.settings.forEach((s, k) => {
    const vp = [k * (w + gap), 0, w, h];
    gl.viewport(...vp); gl.scissor(...vp);
    const common = p => { gl.useProgram(p); u(p, 'u_viewport', ...vp); u(p, 'u_world', ...world); u(p, 'u_sun', s.sun[0], s.sun[1], w / h); };
    common(sky); u(sky, 'u_top', ...s.top); u(sky, 'u_low', ...s.low); u(sky, 'u_disc', ...s.disc); u(sky, 'u_land', ...s.land);
    u(sky, 'u_radius', s.radius); u(sky, 'u_pass', 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    cfg.layers.forEach((L, i) => {
      if (cfg.only >= 0 && cfg.only !== i) return;
      common(cloud);
      u(cloud, 'u_layer', -(scroll + time) * L.speed, L.foot, 0, 0); u(cloud, 'u_size', L.width, L.height);
      u(cloud, 'u_lit', ...s.lit); u(cloud, 'u_shade', ...s.shade); u(cloud, 'u_fog', ...s.low); u(cloud, 'u_haze', L.haze);
      u(cloud, 'u_cloud', L.coverage, L.lobes, L.slot, cfg.style.soft);
      u(cloud, 'u_shape', L.base, L.tall, (time * cfg.churn) % 100, L.seed);
      u(cloud, 'u_tone', cfg.style.flat, cfg.style.rim, cfg.style.opacity, cfg.style.wisps);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    });
    common(sky); u(sky, 'u_pass', 1); u(sky, 'u_low', ...s.low); u(sky, 'u_land', ...s.land);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  });
};
window.ready = true;
</script>"""


def lab_body():
    """CUMULUS_FRAGMENT's GLSL after its HEAD, out of CloudsLab.java: its string literals, joined."""
    with open(LAB, encoding="utf-8") as f:
        java = f.read()
    start = java.index("static final String CUMULUS_FRAGMENT = HEAD")
    text = java[start:java.index(";\n\n", start)]
    return "".join(json.loads('"' + piece.replace("\t", "\\t") + '"') for piece in re.findall(r'\+ "((?:[^"\\]|\\.)*)"', text))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--time", type=float, default=0)
    ap.add_argument("--scroll", type=float, default=0)
    ap.add_argument("--churn", type=float, default=0.03)
    ap.add_argument("--frag", default="")
    ap.add_argument("--only", type=int, default=-1)
    ap.add_argument("--width", type=int, default=1520)
    ap.add_argument("--height", type=int, default=503)
    ap.add_argument("--style", default="{}")
    ap.add_argument("--times", default="", help="comma list: one still per time, OUT with -N before .png")
    a = ap.parse_args()
    if a.frag:
        with open(a.frag, encoding="utf-8") as f:
            body = f.read()
    else:
        body = lab_body()
    cfg = {"width": a.width, "height": a.height, "worldW": WORLD_W, "settings": SETTINGS, "layers": LAYERS,
           "style": {**STYLE, **json.loads(a.style)}, "only": a.only, "churn": a.churn, "sky": SKY,
           "cloud": HEAD + body + TAIL}
    from playwright.sync_api import sync_playwright
    chrome = os.environ.get("CHROME") or shutil.which("google-chrome") or shutil.which("chromium") \
        or shutil.which("chromium-browser")
    with sync_playwright() as p:
        browser = launch(p, chrome)
        page = browser.new_page(viewport={"width": a.width, "height": a.height})
        page.set_content(PAGE.replace("CONFIG", json.dumps(cfg)))
        page.wait_for_function("window.ready === true")
        errors = page.evaluate("window.errors")
        if errors:
            sys.exit("the shader does not compile:\n" + "\n".join(errors))
        times = [float(t) for t in a.times.split(",")] if a.times else [a.time]
        for n, t in enumerate(times):
            page.evaluate("([t, s]) => window.draw(t, s)", [t, a.scroll])
            out = a.out if len(times) == 1 else a.out.replace(".png", f"-{n}.png")
            page.locator("#c").screenshot(path=out)
            print("still:", out)
        browser.close()


if __name__ == "__main__":
    main()
