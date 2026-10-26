// The rings lab (parallax-readers r48): a page of the rings round (engines/rounds/rings) as the sky of a small 3D world,
// drawn by PlaxRings over one PlaxBackground: a valley, a snowy ridge round it at about the page's foot, a camera
// flying round the valley. Panel A stands the bands at the library's depths (front / speed), panel B compresses them
// into front..far, as the saga's sky does (Simon's r35 pick 2); click a panel to give it the whole stage, again to go
// back. Sliders for every option of PlaxRings, the world's fog and the flight; Copy settings puts them as JSON on the
// clipboard. engines/threejs/tools/rings-lab.sh opens it (a window), or shoots it headless through window.ringsLab.
// URL: ?scene=g02&view=both&front=330... starts a scene, a view and any slider at a value.
import * as THREE from 'three';
import { PlaxBackground, PlaxRings, loadAtlas, loadPage } from '../src/plax.js';

const ROUND = '/engines/rounds/rings';
const query = new URLSearchParams(location.search);
const VIEWS = [['both', 'A | B side by side'], ['a', 'A: library depth'], ['b', 'B: compressed to far']];
// The sliders: key, label, min, max, step, section. Their start values: DEFAULTS (Simon's look on r48, 26 Oct: a low
// flight, the bands compressed into 510..3000, 28 degrees of page, the eye melt), the page's fog strength, then the URL's.
// The round's fit of each page (lift, scale, degrees) is for the numbers check, not the look.
const SLIDERS = [
  ['front', 'front', 50, 800, 5, 'Rings'], ['far', 'far (B)', 100, 3000, 10, 'Rings'],
  ['foot', 'foot', 0, 120, 1, 'Rings'], ['lift', 'lift', -12, 12, 0.5, 'Rings'], ['scale', 'scale', 0.2, 2, 0.05, 'Rings'],
  ['pageDegrees', 'page degrees', 10, 90, 1, 'Rings'],
  ['fogStrength', 'fog strength', 0, 0.2, 0.002, 'Fog'], ['melt', 'melt', 0, 0.6, 0.01, 'Fog'], ['eyeMelt', 'eye melt', 0, 0.8, 0.01, 'Fog'],
  ['horizon', 'horizon', 0, 0.5, 0.01, 'Fog'], ['worldFog', 'world fog far', 200, 3000, 10, 'Fog'],
  ['dusk', 'dusk tint', 0, 1, 0.01, 'World'], ['tintSky', 'tint sky (0/1)', 0, 1, 1, 'World'], ['orbit', 'flight radius', 20, 240, 5, 'World'],
  ['height', 'flight height', 5, 120, 1, 'World'], ['speed', 'flight speed', 0, 3, 0.05, 'World'],
];
const DEFAULTS = { front: 510, far: 3000, foot: 51, lift: -2, scale: 1, pageDegrees: 28, fogStrength: 0, melt: 0,
  eyeMelt: 0.3, horizon: 0.5, worldFog: 200, dusk: 0, tintSky: 0, orbit: 60, height: 10, speed: 0.4 };
const NIGHT = [0x30 / 255, 0x38 / 255, 0x6e / 255, 1];

const stage = document.getElementById('stage');
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.autoClear = false;
stage.appendChild(renderer.domElement);
const camera = new THREE.PerspectiveCamera(60, 1, 0.5, 20000);
const scene = new THREE.Scene();
const ground = valley();
scene.add(ground, new THREE.HemisphereLight(0xdfe8ff, 0x4a4030, 1.6));
const sun = new THREE.DirectionalLight(0xfff2dd, 2.2);
sun.position.set(-300, 400, -200);
scene.add(sun);

const bg = new PlaxBackground();
const panels = { a: new PlaxRings(bg), b: new PlaxRings(bg) };
scene.add(panels.a.group, panels.b.group);

let scenes = [], current = null, knobs = { ...DEFAULTS }, opened = { ...DEFAULTS }, view = query.get('view') ?? 'b', zoomed = null;
let flight = 0, playing = !query.has('still');
const tags = {};

// A valley 300 units across, its floor rolling, a ridge round it peaking near 50 (the rings' foot), snow on top.
function valley() {
  const rings = 70, around = 256, R = 300, position = [], color = [], index = [];
  const noise = (a, r) => Math.sin(a * 7 + 1.3) * 0.5 + Math.sin(a * 13 + r * 0.02) * 0.3 + Math.sin(a * 29 + 2.1) * 0.2;
  for (let i = 0; i <= rings; i++) {
    const r = R * i / rings;
    for (let j = 0; j <= around; j++) {
      const a = 2 * Math.PI * j / around;
      const ridge = THREE.MathUtils.smoothstep(r, 150, 265) * (40 + 12 * noise(a, r));
      const floor = 2.5 * Math.sin(r * 0.05 + a * 3) * Math.cos(a * 5 - r * 0.03);
      const y = Math.max(-1, ridge + floor);
      position.push(r * Math.sin(a), y, -r * Math.cos(a));
      const c = y > 42 ? [0.92, 0.94, 0.97] : y > 22 ? [0.45, 0.43, 0.4] : [0.32, 0.45, 0.22];
      color.push(...c);
    }
  }
  for (let i = 0; i < rings; i++) {
    for (let j = 0; j < around; j++) {
      const a = i * (around + 1) + j, b = a + 1, c = a + around + 1, d = c + 1;
      index.push(a, b, c, b, d, c);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(position, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(color, 3));
  g.setIndex(index);
  g.computeVertexNormals();
  return new THREE.Mesh(g, new THREE.MeshLambertMaterial({ vertexColors: true }));
}

async function loadScene(id) {
  current = scenes.find(s => s.id === id) ?? scenes[0];
  const page = await loadPage(current.page);
  bg.setPage(page, await loadAtlas(`${current.atlasDir}/${page.atlasName}`));
  knobs = { ...DEFAULTS, fogStrength: page.fogStrength };
  for (const [key] of SLIDERS) if (query.has(key)) knobs[key] = Number(query.get(key));
  opened = { ...knobs };
  document.getElementById('about').textContent = `${current.id} ${current.name}: ${current.about}`;
  buildSliders();
  apply();
}

function apply() {
  const common = { front: knobs.front, foot: knobs.foot, lift: knobs.lift, scale: knobs.scale,
    pageDegrees: knobs.pageDegrees, fogStrength: knobs.fogStrength, melt: knobs.melt, eyeMelt: knobs.eyeMelt, horizon: knobs.horizon,
    tintSky: knobs.tintSky > 0 };
  panels.a.set({ ...common, far: 0 });
  panels.b.set({ ...common, far: knobs.far });
  bg.tintTo([1 + (NIGHT[0] - 1) * knobs.dusk, 1 + (NIGHT[1] - 1) * knobs.dusk, 1 + (NIGHT[2] - 1) * knobs.dusk, 1], 0);
  for (const p of Object.values(panels)) p.update(0);
  // the world's fog in the rings' fog colour, tinted with tint sky
  const fog = panels.b._fogColor(bg.page);
  scene.fog = new THREE.Fog(new THREE.Color(fog[0], fog[1], fog[2]), knobs.front * 0.3, knobs.worldFog);
  describe();
}

// The bands' radii, per panel, under the controls.
function describe() {
  const line = (name, p) => {
    const bands = PlaxRings.bands(bg.layers, bg.worldWidth, p.ringOptions(bg.page));
    const radii = bands.map(b => b.radius === Infinity ? 'sky' : Math.round(b.radius)).join(' ');
    return `${name}: ${bands.length} bands, radii ${radii}`;
  };
  document.getElementById('numbers').textContent = `${line('A', panels.a)}\n${line('B', panels.b)}`;
}

function chips(id, items, isOn, pick) {
  const box = document.getElementById(id);
  box.replaceChildren(...items.map(([key, text]) => {
    const b = document.createElement('button');
    b.textContent = text;
    b.className = isOn(key) ? 'on' : '';
    b.onclick = () => pick(key);
    return b;
  }));
}

function buildChips() {
  chips('views', VIEWS, k => k === view, k => { view = k; zoomed = null; buildChips(); });
  chips('scenes', scenes.map(s => [s.id, `${s.id} ${s.name}`]), k => k === current?.id,
    k => loadScene(k).then(buildChips));
}

function buildSliders() {
  const box = document.getElementById('sliders');
  box.replaceChildren();
  let section = null;
  for (const [key, text, min, max, step, part] of SLIDERS) {
    if (part !== section) {
      section = part;
      const h = document.createElement('h3');
      h.textContent = part;
      box.appendChild(h);
    }
    const label = document.createElement('label'), input = document.createElement('input'), value = document.createElement('span');
    Object.assign(input, { type: 'range', min, max, step, value: knobs[key] });
    input.dataset.key = key;
    value.className = 'v';
    value.textContent = knobs[key];
    input.oninput = () => { knobs[key] = Number(input.value); value.textContent = input.value; apply(); };
    label.append(Object.assign(document.createElement('span'), { textContent: text }), input, value);
    box.appendChild(label);
  }
}

// Each panel's viewport, in CSS pixels from the stage's bottom left (WebGL's).
function viewports() {
  const w = stage.clientWidth, h = stage.clientHeight;
  const shown = zoomed ? [zoomed] : view === 'both' ? ['a', 'b'] : [view];
  return shown.map((name, i) => ({ name, x: i * w / shown.length, y: 0, w: w / shown.length, h }));
}

stage.onclick = e => {
  if (zoomed) { zoomed = null; return; }
  const ports = viewports(), x = e.clientX - stage.getBoundingClientRect().left;
  if (ports.length > 1) zoomed = ports.find(p => x >= p.x && x < p.x + p.w)?.name ?? null;
};

// The camera round the valley, looking ahead and out toward the ridge and the sky.
function fly(t) {
  const a = t * 0.12, r = knobs.orbit, y = knobs.height + 6 * Math.sin(t * 0.3);
  camera.position.set(r * Math.sin(a), y, -r * Math.cos(a));
  const heading = a + Math.PI / 2 - 0.6;
  camera.lookAt(camera.position.x + Math.sin(heading) * 100, y + 14, camera.position.z - Math.cos(heading) * 100);
}

function draw() {
  const w = stage.clientWidth, h = stage.clientHeight;
  if (renderer.domElement.width !== w || renderer.domElement.height !== h) renderer.setSize(w, h, false);
  fly(flight);
  renderer.setScissorTest(true);
  renderer.setClearColor(0x000000);
  for (const port of viewports()) {
    for (const [name, p] of Object.entries(panels)) p.group.visible = name === port.name;
    renderer.setViewport(port.x, port.y, port.w, port.h);
    renderer.setScissor(port.x, port.y, port.w, port.h);
    camera.aspect = port.w / port.h;
    camera.updateProjectionMatrix();
    renderer.clear();
    renderer.render(scene, camera);
  }
  renderer.setScissorTest(false);
  placeTags();
}

function placeTags() {
  const ports = viewports();
  for (const name of ['a', 'b']) {
    tags[name] ??= stage.appendChild(Object.assign(document.createElement('div'), { className: 'tag' }));
    const port = ports.find(p => p.name === name);
    tags[name].style.display = port ? '' : 'none';
    if (!port) continue;
    tags[name].style.left = `${port.x + 10}px`;
    tags[name].style.top = '10px';
    tags[name].textContent = name === 'a' ? 'A  library depth (front / speed)' : `B  compressed into ${knobs.front}..${knobs.far}`;
  }
}

let last = performance.now();
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (playing) {
    flight += dt * knobs.speed;
    bg.act(dt);
  }
  for (const p of Object.values(panels)) p.update(0);
  draw();
  requestAnimationFrame(frame);
}

// Cross-fades the rings into the next scene's page over `seconds` (PlaxBackground.transfertInto): each band at its
// layer's fade alpha; the sliders stay the scene's that opened.
async function fadeToNext(seconds) {
  const next = scenes[(scenes.indexOf(current) + 1) % scenes.length];
  const page = await loadPage(next.page);
  bg.transfertInto(page, await loadAtlas(`${next.atlasDir}/${page.atlasName}`), seconds);
  current = next;
  document.getElementById('about').textContent = `fading into ${next.id} ${next.name}: ${next.about}`;
  buildChips();
}
document.getElementById('fade').onclick = () => fadeToNext(4);

document.getElementById('play').onclick = e => {
  playing = !playing;
  e.target.textContent = playing ? 'Pause' : 'Play';
};
// Every knob's value, what it opened at with this scene, and the ones moved, as JSON: on the clipboard, and selected
// in the box under the button (the clipboard is refused on some origins).
document.getElementById('copy').onclick = async () => {
  const moved = Object.fromEntries(Object.keys(knobs).filter(k => knobs[k] !== opened[k]).map(k => [k, [opened[k], knobs[k]]]));
  const text = JSON.stringify({ scene: current.id, view, values: knobs, opened, moved }, null, 1);
  const box = document.getElementById('settings');
  box.value = text;
  box.style.display = 'block';
  box.select();
  const button = document.getElementById('copy');
  button.textContent = await navigator.clipboard.writeText(text).then(() => 'Copied', () => 'Selected below');
  setTimeout(() => { button.textContent = 'Copy settings'; }, 1500);
};

// For rings-lab.sh's stills: the flight at `t` seconds, the page's drift and fades `t` seconds in, drawn now.
window.ringsLab = {
  ready: (async () => {
    scenes = (await (await fetch(`${ROUND}/round.json`)).json()).scenes
      .map(s => ({ ...s, page: `/${s.page}`, atlasDir: `/${s.atlasDir}` }));
    await loadScene(query.get('scene') ?? 'g02');
    buildChips();
    if (!playing) document.getElementById('play').textContent = 'Play';
    requestAnimationFrame(frame);
  })(),
  at(t) {
    playing = false;
    bg.resetPositions();
    bg.act(t);
    flight = t * knobs.speed;
    for (const p of Object.values(panels)) p.update(0);
    draw();
  },
  // Starts a cross-fade into the next scene's page, over `seconds`; at(t) then steps it from its start.
  async fade(seconds) {
    await fadeToNext(seconds);
    playing = false;
  },
  step(t) {
    bg.act(t);
    for (const p of Object.values(panels)) p.update(0);
    draw();
  },
  set(values) { Object.assign(knobs, values); buildSliders(); apply(); },
  knobs: () => ({ scene: current.id, view, ...knobs }),
};
