// A three.js reader for JKS parallax pages: the port of the libGDX runtime's Parallax_Heart + ParallaxPageReader +
// ParallaxLayer (core/src/jks/tools2d/parallax), drawn by three.js r170 or later. Same numbers, same picture:
// engines/threejs/tools/frame-check.sh compares its frames with the libGDX ones. Born as Crypto en visuel's
// lib/plax.js (deux-generaux r195, r449), brought to core's drawing here (parallax-readers r34) from the Godot port
// (engines/godot/addons/jks_parallax), which copies core line for line.
//
//   import { PlaxBackground } from './plax.js';
//   const bg = new PlaxBackground();
//   await bg.load('backgrounds/calm.jplax');          // its .atlas and images next to it, or bg.load(page, atlasDir)
//   bg.speedConstantX = 60;                            // optional auto-scroll, in the page's units per second
//   bg.setSize(innerWidth, innerHeight);
//   renderer.setAnimationLoop(() => { bg.act(1 / 60); bg.render(renderer); });   // or render bg.scene, bg.camera
//
// Screen-anchored as the libGDX one: bg.camera is its own orthographic camera in screen pixels. The world is
// worldWidth units wide (40, the libGDX heart's default) and as high as the screen's aspect ratio makes it; a layer's
// decal is in percent of that world. Draw it first and the game over it (renderer.autoClear = false after it).
//
// Colours are libGDX's: no colour management, blending in the PNG's own (sRGB) values, straight alpha. The renderer
// is left as the game set it except for its output colour space, which render() sets to LinearSRGBColorSpace (no
// conversion) for its own draw and puts back.
//
// Cross-fading into another page and tinting, as Parallax_Heart.transfertIntoPage and
// ParallaxPageReader.addColorTransfert; a style (PlaxTransfertStyle) staggers, dissolves, or goes through a colour:
//
//   bg.transfertInto(await loadPage(url), await loadAtlas(atlasUrl), 2)
//   bg.transfertInto(page, atlas, 3, PlaxTransfertStyle.dissolve(5, 0.08, 0.5))
//   bg.tintTo([1, 0.6, 0.4, 1], 2)
//
// An EMPTY layer (kind "EMPTY") is drawn by the game, as ParallaxPageReader.setLayerHook:
//
//   const bird = bg.atlas.findRegion('bird');
//   bg.setLayerHook('birds', (batch, rect, color, l) => batch.drawRegion(bird, rect.x, rect.y, rect.width, rect.height));
//
// called in the layer's place in the draw order, once per tile of it the view shows, `rect` in screen pixels (y down),
// `batch` the layer's quads, already in `color`: the page's tint at the layer's opacity, packed as libGDX packs it. A
// layer with no hook draws nothing; setLayerHook(name, null) removes one. Round a planet the hook is not bent: it draws
// what stands at a point where bg.planetPlaceAt(l, point) puts it, turned by bg.planetAngleAt(l, x) (r347):
//
//   const foot = [rect.x + rect.width / 2, rect.y + rect.height];
//   batch.drawRegion(tower, -w / 2, -h, w, h, bg.planetPlaceAt(l, foot), bg.planetAngleAt(l, foot[0]));
//
// A PARTICLES layer draws the libGDX effect (.p) its page names, from the atlas's folder (PlaxParticles: libGDX's
// emitter, ported), anchored to the view or tiled with its layer. bg.load fetches it; a page given to setPage or
// transfertInto needs `await loadParticles(page, atlas)` first. PlaxParticles.random.setSeed(n) repeats a run's snow.
//
// A SEQUENCE layer chains its segments (atlas regions) in a cycle drawn once, when the page is set (PlaxSequence), by
// drawCycle from the page's seed: the same picks as core's SequenceCycle. The cycle is the layer's tile. A game draws
// a new ground each run with its own seed, XORed with each layer's stored one, as ParallaxPageReader.setSequenceSeed:
//
//   bg.setSequenceSeed(seed)                           // Java's int: 32 bits; clearSequenceSeed() goes back
//
// A FLOOR layer is drawn as rows, each at its depth (PlaxFloor: MODE7 or LINE_SCROLL), and the layers in front of it
// scroll at the speed of the row under their foot. A LURKER layer is drawn as an IMAGE one while its sightings say its
// figure is there (PlaxLurker): it appears or vanishes only while nothing of it is on screen; a game's sure first
// scare is bg.revealLurker(name). An IMAGE or SHADER layer with an animRegion draws its animated patch (PlaxAnim) over
// each tile, at floor(time x animFps) on act()'s clock.
//
// A SHADER layer is drawn as an image layer, through its effect (PlaxEffects, the shaders of core's GdxLayerEffects),
// which moves on act()'s clock. The page's depth fog (fogStrength, fogColor) mixes each IMAGE, SEQUENCE and SHADER
// layer toward the fog's colour, more the slower it scrolls (PlaxEffects.fogOf), through the same shaders.
//
// Ported: IMAGE layers (decal, sizeRatio, flips, speeds, speedXAtRest, pads, mirror, repeat on X and Y,
// useOriginalSize), FLOOR and LURKER layers, the animated patch, SEQUENCE layers, SHADER layers (WAVE, FOG, SWELL,
// MIRROR) and their glint, the page's depth fog, the two gradients, cross-fades and their styles (depth stagger,
// dissolve, through a colour, fog creep), tints, zoom and the dolly (bg.dolly = true: each layer zoomed by its depth,
// about the view's middle), towers and the panoramas round a near one (PlaxTower, PlaxPanorama), planets (PlaxPlanet:
// the page's planetRadius), resizing, EMPTY layers (setLayerHook), PARTICLES layers, detail levels (PlaxDetail:
// bg.detail, setLayerDetail, setLayerDefaultDetail; r44).
//
// A 3D game can stand the page round its world instead (PlaxRings, r48: each layer a cylinder as far as its speed
// says, docs/page-as-3d-sky.md): new PlaxRings(bg, { front: 330, foot: 50 }), its group in the scene, update(delta).
import * as THREE from 'three';

export const WORLD_WIDTH = 40;

// What a layer of a page has when its file leaves a field out (Parallax_Model's defaults, as PlaxPage.LAYER_DEFAULTS).
const LAYER_DEFAULTS = {
  regionName: '', regionPosition: 0, flipX: false, flipY: false,
  parallaxScalingSpeedX: 0, parallaxScalingSpeedY: 0, speedXAtRest: 0, sizeRatio: 1,
  decal_X_Ratio: 0, decal_Y_Ratio: 0, padX: 0, padXFactor: 0, padY: 0, padYFactor: 0,
  mirror: false, kind: 'IMAGE', name: '',
  shaderEffect: 'WAVE', shaderAmplitude: 0, shaderWavelength: 0, shaderSpeed: 0, shaderHaze: 0,
  sequenceSegments: [], sequenceSeed: 0, sequenceLength: 16,
  floorMapping: 'MODE7', floorTopSpeed: 0, floorRows: 96, floorHeight: 0,
  towerRadius: 0, towerShade: 0.65, towerCamera: 0,
  lurkerSeed: 0, lurkerOdds: 3, lurkerCover: [],
  animRegion: '', animX: 0, animY: 0, animFps: 12,
  glintStrength: 0, mirrorHorizon: 1, mirrorDepth: 3,
  particlesLibgdx: '', particlesAnchor: 'LAYER',
};
// The kinds this reader draws; another fails the page's load. A PARTICLES layer's effect is loadParticles'. A SEQUENCE layer's cycle is drawn by drawCycle.
// A FLOOR layer's rows by PlaxFloor.draw, a LURKER layer as an IMAGE one while its sightings say it is there (PlaxLurker).
const KINDS = ['IMAGE', 'EMPTY', 'SEQUENCE', 'SHADER', 'PARTICLES', 'FLOOR', 'LURKER'];
// How a FLOOR layer's rows map its image (Enum_FloorMapping).
const FLOOR_MAPPINGS = ['MODE7', 'LINE_SCROLL'];
// The kinds a dissolve masks and a transfert through a colour grades; an EMPTY one fades to its share instead.
const MASKED = ['IMAGE', 'SEQUENCE', 'SHADER', 'FLOOR', 'LURKER'];
// The effects a SHADER layer is drawn through (PlaxEffects.EFFECTS); another fails the page's load, as in libGDX.
const EFFECTS = ['WAVE', 'FOG', 'SWELL', 'MIRROR'];
// What a seed of 0 starts from (SequenceCycle.ZERO_SEED): xorshift never leaves 0.
const ZERO_SEED = 0x6D2B79F5;
const WHITE = [1, 1, 1, 1];

// --- reading ---------------------------------------------------------------------------------------------------------

// A libGDX .atlas: both layouts (xy/size/orig/offset, and bounds/offsets from libGDX 1.9.13). Regions keep file
// order, then index order when any region has one, a region without one last, as libGDX does: regionPosition counts
// in that order. Each page's image is loaded as its own texture, filtered as the atlas says.
export async function loadAtlas(url) {
  const text = await (await fetchOk(url)).text(), dir = url.replace(/[^/]*$/, '');
  const pages = [], regions = [];
  let page = null, region = null, header = false;
  const close = () => {
    if (region && !region.originalWidth && !region.originalHeight) {
      region.originalWidth = region.width; region.originalHeight = region.height;
    }
    if (region) regions.push(region);
    region = null;
  };
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trimEnd();
    // A blank line ends a page: the next line names the image of the next one.
    if (!line.trim()) { close(); page = null; continue; }
    const indented = /^\s/.test(line), colon = line.indexOf(':');
    if (!page) {
      page = { file: line.trim(), mipmaps: false, nearest: false };
      pages.push(page); header = true;
    } else if (header && !indented && colon >= 0) {
      if (line.slice(0, colon).trim() === 'filter') {
        page.mipmaps = line.includes('MipMap');
        // "Nearest,Nearest": a pixel-art export (r126). A minification filter alone set to Nearest is drawn linear.
        page.nearest = line.slice(colon + 1).trim().endsWith('Nearest');
      }
    } else if (!indented) {
      close(); header = false;
      region = { name: line.trim(), index: -1, page, rotate: false, x: 0, y: 0, width: 0, height: 0,
        originalWidth: 0, originalHeight: 0, offsetX: 0, offsetY: 0 };
    } else if (colon >= 0 && region) {
      const key = line.slice(0, colon).trim(), value = line.slice(colon + 1).trim();
      const n = value.split(',').map(Number);
      if (key === 'rotate') region.rotate = value === 'true' || value === '90';
      else if (key === 'xy') [region.x, region.y] = n;
      else if (key === 'size') [region.width, region.height] = n;
      else if (key === 'bounds') [region.x, region.y, region.width, region.height] = n;
      else if (key === 'orig') [region.originalWidth, region.originalHeight] = n;
      else if (key === 'offset') [region.offsetX, region.offsetY] = n;
      else if (key === 'offsets') [region.offsetX, region.offsetY, region.originalWidth, region.originalHeight] = n;
      else if (key === 'index') region.index = n[0];
    }
  }
  close();
  if (regions.some(r => r.index !== -1)) {
    // A stable sort: equal indexes keep file order.
    const key = r => r.index === -1 ? Infinity : r.index;
    regions.sort((a, b) => key(a) - key(b));
  }
  await Promise.all(pages.map(async p => { p.texture = await loadTexture(dir + p.file, p.mipmaps, p.nearest); }));
  return {
    pages, regions, dir,
    // The n-th region called `name`, counting from 0 in libGDX's order: how a page names its layers.
    findRegion(name, position = 0) {
      return regions.filter(r => r.name === name)[position] ?? null;
    },
  };
}

// The image as the PNG holds it: no premultiplied alpha, no colour conversion, row 0 at v = 0. A libGDX texture keeps
// its own filter: a pixel-art page stays sharp mid-fade with a smooth one.
async function loadTexture(url, mipmaps, nearest) {
  const blob = await (await fetchOk(url)).blob();
  const bitmap = await createImageBitmap(blob, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
  const t = new THREE.Texture(bitmap);
  t.flipY = false;
  t.premultiplyAlpha = false;
  t.colorSpace = THREE.NoColorSpace;
  t.generateMipmaps = mipmaps && !nearest;
  t.magFilter = nearest ? THREE.NearestFilter : THREE.LinearFilter;
  t.minFilter = nearest ? THREE.NearestFilter : t.generateMipmaps ? THREE.LinearMipmapLinearFilter : THREE.LinearFilter;
  t.wrapS = t.wrapT = THREE.ClampToEdgeWrapping;
  t.needsUpdate = true;
  return t;
}

// A saved page: the JSON export (.jplax) or the editor project (.plaxpj), read field by field as
// core's Utils_Page_Json does. A field missing from the file keeps its default.
export async function loadPage(url) {
  const json = await (await fetchOk(url)).json();
  // A project holds its page under "saving", with a flag per layer telling whether it comes from the atlas.
  return json.saving ? readPage(json.saving, json.saving.inside) : readPage(json, null);
}

export function readPage(json, inside) {
  const color = (c, fallback) => c && typeof c === 'object' ? [c.r ?? 0, c.g ?? 0, c.b ?? 0, c.a ?? 0] : fallback;
  const page = {
    topHalfTop: color(json.topHalf_top, WHITE), topHalfBottom: color(json.topHalf_bottom, WHITE),
    bottomHalfTop: color(json.bottomHalf_top, WHITE), bottomHalfBottom: color(json.bottomHalf_bottom, WHITE),
    // The part of the screen each gradient leaves UNCOVERED: 0 fills the screen, 0.5 half of it, 1 nothing.
    topHalfSize: json.topHalfSize ?? 0.5, bottomHalfSize: json.bottomHalfSize ?? 0.5,
    repeatOnX: json.repeatOnX ?? true, repeatOnY: json.repeatOnY ?? false,
    useOriginalSize: json.useOriginalSize ?? false,
    // The depth fog (WholePage_Model.getFogStrength, format 10): 0 or more; PlaxEffects.fogOf mixes each layer toward
    // fogColor by it, more the slower the layer. Read from a page saved before format 10, it is the largest FOG
    // layer's shaderHaze.
    fogStrength: json.fogStrength != null ? Math.max(0, json.fogStrength) : 0,
    fogColor: color(json.fogColor, [0.93, 0.95, 0.97, 1]),
    // The planet the page's ground runs round (WholePage_Model.planetRadius, format 13): its radius in percent of the
    // world width; 0 flat (PlaxPlanet).
    planetRadius: json.planetRadius != null ? json.planetRadius : 0,
    atlasName: json.pageModel?.atlasName ?? '',
    layers: [],
  };
  const list = json.pageModel?.pageList ?? [];
  list.forEach((m, i) => {
    // A project layer without a flag comes from the atlas, as the editor reads it.
    if (Array.isArray(inside) && i < inside.length && !inside[i]) return;
    const layer = { ...LAYER_DEFAULTS };
    for (const key in m) if (m[key] !== null && m[key] !== undefined) layer[key] = m[key];
    // A seed is the int Java stored; a segment without a weight weighs 1.
    layer.sequenceSeed |= 0;
    layer.sequenceSegments = layer.sequenceSegments.map(segment => ({
      regionName: segment.regionName ?? '', regionPosition: segment.regionPosition ?? 0, weight: segment.weight ?? 1,
    }));
    if (!KINDS.includes(layer.kind)) {
      throw new Error(`plax: layer ${i} (${layer.name || layer.regionName}) is a ${layer.kind} layer: not ported to `
        + 'three.js yet (engines/threejs/README.md)');
    }
    if (!FLOOR_MAPPINGS.includes(layer.floorMapping)) {
      throw new Error(`plax: layer ${i} (${layer.name || layer.regionName}) has the floor mapping ${layer.floorMapping}: `
        + 'unknown to this reader');
    }
    if (layer.kind === 'SHADER' && !EFFECTS.includes(layer.shaderEffect)) {
      throw new Error(`plax: layer ${i} (${layer.name || layer.regionName}) has the ${layer.shaderEffect} effect: not `
        + 'ported to three.js yet (engines/threejs/README.md)');
    }
    page.layers.push(layer);
  });
  if (!('fogStrength' in json)) {
    for (const layer of page.layers) {
      if (layer.kind === 'SHADER' && layer.shaderEffect === 'FOG' && layer.shaderHaze > page.fogStrength) {
        page.fogStrength = Math.min(1, layer.shaderHaze);
      }
    }
  }
  return page;
}

// SequenceCycle.next: the 32-bit xorshift step (13, 17, 5) of `state`, both unsigned 32-bit ints (0 .. 2^32 - 1).
// JS shifts work on 32 bits already: << and ^ give a signed int, >>> 0 brings it back unsigned, and a right shift of
// an unsigned value is Java's >>>.
export function sequenceNext(state) {
  state = (state ^ (state << 13)) >>> 0;
  state = (state ^ (state >>> 17)) >>> 0;
  state = (state ^ (state << 5)) >>> 0;
  return state;
}

// SequenceCycle.draw, bit for bit: `length` segment indexes picked from `seed` (Java's int: negative or not) and
// `weights`, one per segment. For each slot the state steps once and (state >>> 1) % total walks the weights; a weight
// of 0 or less is never picked, and when none is above 0 each weighs 1. Integers only: a float would pick otherwise.
export function drawCycle(seed, weights, length) {
  let total = 0;
  for (const weight of weights) if (weight > 0) total += weight;
  let state = seed >>> 0;
  if (state === 0) state = ZERO_SEED;
  const cycle = new Int32Array(Math.max(1, length));
  for (let slot = 0; slot < cycle.length; slot++) {
    state = sequenceNext(state);
    let pick = (state >>> 1) % (total > 0 ? total : weights.length);
    let segment = 0;
    while (segment < weights.length - 1) {
      const weight = total > 0 ? Math.max(weights[segment], 0) : 1;
      if (pick < weight) break;
      pick -= weight;
      segment++;
    }
    cycle[slot] = segment;
  }
  return cycle;
}

async function fetchOk(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`plax: ${url}: ${r.status}`);
  return r;
}

// --- the background --------------------------------------------------------------------------------------------------

export class PlaxBackground {
  constructor({ worldWidth = WORLD_WIDTH } = {}) {
    this.worldWidth = worldWidth;
    // Scroll speed, as Parallax_Heart.screenSpeedConstantX/Y; the consumable ones count for the next act only.
    this.speedConstantX = 0;
    this.speedConstantY = 0;
    this.speedConsumableX = 0;
    this.speedConsumableY = 0;
    // The camera's zoom, as libGDX's OrthographicCamera.zoom: the page zooms as one picture about the view's bottom
    // left corner, where its layers are placed from.
    this.zoom = 1;
    // ParallaxPageReader.setDolly (r278): the zoom scales each layer by its depth (PlaxEffects.dollyZoom), the near ones
    // more, a still one not at all, about the view's middle.
    this.dolly = false;
    // The page on screen; during a cross-fade, the one fading out. Its layers: the ParallaxLayer state.
    this.page = null;
    this.atlas = null;
    this.layers = [];
    // The page fading in, and its layers; null and empty outside a cross-fade.
    this.transferPage = null;
    this.transferLayers = [];
    // Multiplied into every layer (not the gradients); tintTo fades it.
    this.tint = [...WHITE];
    this.scene = new THREE.Scene();
    this.camera = new THREE.OrthographicCamera(0, 1280, 0, 720, -1, 1);
    this.width = 1280;
    this.height = 720;
    this.worldHeight = worldWidth * 720 / 1280;
    this._repeatX = true;
    this._repeatY = false;
    this._newAlpha = 0;
    this._oldAlpha = 1;
    this._fadeSpeed = 0;
    // How the cross-fade under way is drawn (ParallaxPageReader.transfertStyle): null outside one, and for a plain fade.
    this._style = null;
    this._tintFrom = [...WHITE];
    this._tintTo = [...WHITE];
    this._tintDuration = 0;
    this._tintElapsed = 0;
    this._gradient = [WHITE, WHITE, WHITE, WHITE];
    this._gradientFrom = null;
    this._gradientTo = null;
    this._gradientDuration = 0;
    this._gradientElapsed = 0;
    this._gradientFading = false;
    // How the gradients fade (TransfertStyle.gradient): null straight, as a plain fade.
    this._gradientStyle = null;
    this._topHalfSize = 0.5;
    this._bottomHalfSize = 0.5;
    // The game's seed for the SEQUENCE layers, when _hasSequenceSeed (setSequenceSeed).
    this._sequenceSeed = 0;
    this._hasSequenceSeed = false;
    // The clock the SHADER layers' effects run on: act()'s seconds.
    this._effectTime = 0;
    // What draws each EMPTY layer, by layer name (setLayerHook).
    this._hooks = new Map();
    // ParallaxPageReader.setDetail: how much of the page is drawn (bg.detail), HIGH as stored; from which levels each
    // layer named is drawn (setLayerDetail), and every other layer (PlaxDetail.ruleOf).
    this._detail = PlaxDetail.Level.HIGH;
    this._details = new Map();
    this._defaultDetail = PlaxDetail.DEFAULT;
    // The camera zoom of the last draw of a LURKER layer, which act()'s sightings run against; NaN before it (PlaxLurker).
    this._lurkerZoom = NaN;
    // Which of a SHADER layer's two draws _tile is on: 0 both, its tiles (1) and its patches (2) apart (_drawLayer).
    this._part = 0;
    // Set by _drawLayer: the radius of the planet the layer drawn is wrapped round, in its view (PlaxPlanet.radiusOf; 0
    // for none), how far over the view its top corners reach, and the view's width.
    this._planet = 0;
    this._reach = 0;
    this._viewW = 0;
    // Set by _drawLayer: the panorama the layer drawn is wrapped in round its page's near tower (PlaxPanorama: rho, q,
    // the strips' edges and scales; null for none), and the eye's height it is scaled about.
    this._panorama = null;
    this._eyeY = 0;
    this._gradients = gradientMesh();
    this.scene.add(this._gradients);
  }

  // Reads a .jplax or .plaxpj, and its atlas from `atlasDir` (the page's folder when not given).
  async load(pageUrl, atlasDir = pageUrl.replace(/[^/]*$/, '')) {
    const page = await loadPage(pageUrl), atlas = await loadAtlas(join(atlasDir, page.atlasName));
    await loadParticles(page, atlas);
    this.setPage(page, atlas);
  }

  setPage(page, atlas) {
    this.page = page;
    this.atlas = atlas;
    this._drop(this.layers);
    this._drop(this.transferLayers);
    this._resetTransfert();
    this._updateWorld();
    this.layers = this._buildLayers(page, atlas);
    this._repeatX = page.repeatOnX;
    this._repeatY = page.repeatOnY;
    this._gradient = [page.topHalfTop, page.topHalfBottom, page.bottomHalfTop, page.bottomHalfBottom];
    this._gradientFading = false;
    this._topHalfSize = page.topHalfSize;
    this._bottomHalfSize = page.bottomHalfSize;
    this.resetPositions();
  }

  // Cross-fades into `page` over `seconds` (0: at once), as Parallax_Heart.transfertIntoPage. The pages are matched
  // from their front layer: an incoming layer takes the distance its counterpart has scrolled, so nothing jumps. The
  // gradients' colours fade too; the repeat and the gradients' sizes stay those of the page given to setPage. A fade
  // started during another drops the page that was fading in. `style` (null: every layer slot at once) is how the
  // layers fade, as Parallax_Heart.transfertIntoPage(page, seconds, style): PlaxTransfertStyle.depthStagger(s) fades
  // each slot in its own window, the back ones first; PlaxTransfertStyle.dissolve(patches, softness, s) has each slot's
  // incoming layer eat the outgoing one in patches; PlaxTransfertStyle.throughColor(color, s) goes through a colour,
  // white possible, the gradients too; PlaxTransfertStyle.fogCreep(mist, s) sinks the page into a mist and brings the
  // new one out of it.
  transfertInto(page, atlas, seconds, style = null) {
    if (!this.page) { this.setPage(page, atlas); return; }
    this._drop(this.transferLayers);
    this._unmask(this.layers);
    this._resetTransfert();
    const incoming = this._buildLayers(page, atlas);
    if (incoming.length) {
      this.transferPage = page;
      this.transferAtlas = atlas;
      this.transferLayers = incoming;
      for (const l of incoming) l.incoming = true;
      this._syncTransferPositions();
      if (seconds <= 0) this._finishTransfert();
      else {
        this._fadeSpeed = Math.fround(1 / seconds);
        this._style = style;
      }
    }
    this._fadeGradients([page.topHalfTop, page.topHalfBottom, page.bottomHalfTop, page.bottomHalfBottom], seconds,
      style);
  }

  // Draws every SEQUENCE layer's cycle, on screen and fading in, from `seed` XOR the seed its page stores rather than
  // from the stored one alone: a new ground each run (ParallaxPageReader.setSequenceSeed). `seed` is Java's int: 32 bits.
  setSequenceSeed(seed) {
    this._sequenceSeed = seed | 0;
    this._hasSequenceSeed = true;
    this._drawCycles();
  }

  // Makes `hook` draw every EMPTY layer named `name`; null removes it. See the class doc.
  setLayerHook(name, hook) {
    if (hook) this._hooks.set(name, hook);
    else this._hooks.delete(name);
  }

  // ParallaxPageReader.setDetail: how much of the page is drawn, a PlaxDetail.Level, each layer by its rule.
  get detail() {
    return this._detail;
  }

  set detail(level) {
    this._detail = level;
    PlaxDetail.applyAll(this);
  }

  // ParallaxPageReader.setLayerDetail: the layers named `name` drawn from the levels `rule` names over
  // PlaxDetail.DEFAULT ({ shown, effect, glint, mirror }); empty or null: as every other layer.
  setLayerDetail(name, rule) {
    if (!rule || !Object.keys(rule).length) this._details.delete(name);
    else this._details.set(name, PlaxDetail.rule(rule));
    PlaxDetail.applyAll(this);
  }

  // ParallaxPageReader.setLayerDefaultDetail: every layer not named drawn from the levels `rule` names over the default.
  setLayerDefaultDetail(rule) {
    this._defaultDetail = PlaxDetail.rule(rule ?? {});
    PlaxDetail.applyAll(this);
  }

  // ParallaxPageReader.planetPlaceAt (r347): where the point `at` ([x, y]) of layer `l` is drawn round its page's
  // planet, both in the screen pixels a hook draws in (y down): the view is the side of a world turning round one
  // centre under the middle of its bottom, x along the ground's arc and y along the radius. For a hook to place what it
  // draws, turned by planetAngleAt; `at` for no planet. Called from a hook, the view is the layer's own (dolly).
  planetPlaceAt(l, at) {
    const radius = this._planetOf(l);
    if (!(radius > 0)) return at;
    const to = PlaxPlanet.place(radius, this._viewW, at[0] / this._ppu, (this.height - at[1]) / this._ppu);
    return [to[0] * this._ppu, this.height - to[1] * this._ppu];
  }

  // ParallaxPageReader.planetAngleAt: how far what stands at `x` (screen pixels) in layer `l` leans round its page's
  // planet, in radians clockwise on screen (y down): out along the radius. 0 for no planet.
  planetAngleAt(l, x) {
    return PlaxPlanet.angleAt(this._planetOf(l), this._viewW, x / this._ppu);
  }

  // The radius of the planet layer `l`'s page runs round, in its view (PlaxPlanet.radiusOf); 0 for none.
  _planetOf(l) {
    const fromPage = l.incoming ? this.transferPage : this.page;
    return PlaxPlanet.radiusOf(fromPage.planetRadius, this.worldWidth, this._zoomOf(l), this.zoom);
  }

  // The zoom `l` is drawn at: the camera's (zoom), or dollied, by its depth against its page's front
  // (ParallaxPageReader.drawLayer).
  _zoomOf(l) {
    if (!this.dolly) return this.zoom;
    return PlaxEffects.dollyZoom(this.zoom, PlaxEffects.frontSpeedOf(l.incoming ? this.transferLayers : this.layers), l.model);
  }

  // Where the layers are placed from in the view at zoom `z`, from its bottom left corner: the corner itself, or
  // dollied, the corner of the view at zoom 1, so that a zoom scales them about the middle (ParallaxPageReader.view).
  _anchorOf(z) {
    if (!this.dolly) return [0, 0];
    return [this.worldWidth * (z - 1) / 2, this.worldHeight * (z - 1) / 2];
  }

  // What a tower's piece at `bright` multiplies the layer's colour by (Quads.shade), as libGDX darkens it
  // (ParallaxLayer.drawTower): through the page fog's shader the darkness goes in the batch colour's blue, cut to 8 bits,
  // and the shader multiplies the tint by 1 minus it; else the tint times bright is packed (Color.toFloatBits).
  _towerShade(bright) {
    if (this._towerFog) {
      const dim = 1 - packed(1 - bright);
      return [dim, dim, dim];
    }
    const c = this._quads.color, rgb = [c.r, c.g, c.b];
    return rgb.map((v, i) => v > 0 ? packed(this.tint[i] * bright) / v : 0);
  }

  // ParallaxPageReader.reveal: the next sighting of every LURKER layer named `name` shows its figure, at once when it
  // is not in sight now, else from the end of this sighting: a game's sure first scare (PlaxLurker).
  revealLurker(name) {
    PlaxLurker.reveal(this.layers, name);
    PlaxLurker.reveal(this.transferLayers, name);
  }

  // Back to the seeds the pages store: what the editor previews.
  clearSequenceSeed() {
    this._hasSequenceSeed = false;
    this._drawCycles();
  }

  _drawCycles() {
    for (const l of [...this.layers, ...this.transferLayers]) this._drawCycleOf(l);
  }

  // Draws a SEQUENCE layer's cycle from the seed this background gives it, when it was drawn from another.
  _drawCycleOf(l) {
    if (l.model.kind !== 'SEQUENCE') return;
    const seed = (this._hasSequenceSeed ? this._sequenceSeed ^ l.model.sequenceSeed : l.model.sequenceSeed) >>> 0;
    if (l.drawnSeed === seed) return;
    l.drawnSeed = seed;
    PlaxSequence.layOut(l, seed);
    this._sizeLayer(l);
  }

  isInTransfer() { return this.transferLayers.length > 0; }

  // Fades `tint` toward `color` ([r, g, b, a], 0 to 1) over `seconds` (0: at once), as
  // ParallaxPageReader.addColorTransfert.
  tintTo(color, seconds) {
    this._tintFrom = [...this.tint];
    this._tintTo = [...color];
    this._tintElapsed = 0;
    this._tintDuration = seconds;
    if (seconds <= 0) this.tint = [...color];
  }

  // The screen's size in pixels: the world keeps its width and follows its aspect ratio, as Parallax_Heart.resize
  // does; nothing moves.
  setSize(width, height) {
    this.width = width;
    this.height = height;
    this.camera.right = width;
    this.camera.bottom = height;
    this.camera.updateProjectionMatrix();
    this._updateWorld();
    for (const l of [...this.layers, ...this.transferLayers]) this._sizeLayer(l);
  }

  // Puts every layer back at its decal.
  resetPositions() {
    for (const l of this.layers) this._resetPosition(l);
  }

  // Scrolls the layers and moves the fades by one step of `delta` seconds (Parallax_Heart.act).
  act(delta) {
    const speedX = this.speedConstantX + this.speedConsumableX;
    const speedY = this.speedConstantY + this.speedConsumableY;
    this.speedConsumableX = this.speedConsumableY = 0;
    if (!this.page) return;
    this._effectTime += delta;
    this._actGradients(delta);
    PlaxFloor.plant(this.layers, this.worldHeight);
    PlaxFloor.plant(this.transferLayers, this.worldHeight);
    for (const l of this.layers) this._actLayer(l, delta, speedX, speedY);
    if (this.transferLayers.length) {
      for (const l of this.transferLayers) this._actLayer(l, delta, speedX, speedY);
      // In floats, as libGDX counts them: the fade ends on the same step, and a PARTICLES layer of the page going
      // updates, and draws from the random, as many times (q04).
      this._newAlpha = Math.min(1, f32p(this._newAlpha + f32p(delta * this._fadeSpeed)));
      this._oldAlpha = Math.max(0, f32p(this._oldAlpha - f32p(delta * this._fadeSpeed)));
      if (this._newAlpha >= 1) this._finishTransfert();
    }
    if (this._tintElapsed < this._tintDuration) {
      this._tintElapsed = Math.min(this._tintDuration, this._tintElapsed + delta);
      this.tint = lerp(this._tintFrom, this._tintTo, this._tintElapsed / this._tintDuration);
    }
    // Every step, drawn or not: a sighting between two draws counts. Against the last draw's view.
    PlaxLurker.sight(this, this._lurkerZoom);
  }

  // Lays the scene out as the background is now: the gradients, then the layers in draw order.
  update() {
    this._drawGradients();
    let order = 1;
    for (const l of this._drawOrder()) {
      l.quads.begin(order++);
      if (this.page) this._drawLayer(l);
      l.quads.end();
      if (l.animQuads) {
        // A SHADER layer's patches after its tiles, through their own material (_buildLayers); drawn plain at its detail
        // level, with its tiles (PlaxDetail).
        l.animQuads.begin(order++);
        if (this.page && l.drawnEffect) this._drawLayer(l, true);
        l.animQuads.end();
      }
    }
  }

  // update(), and the scene drawn with `renderer`, in libGDX's colours (see the head of this file).
  render(renderer) {
    this.update();
    const output = renderer.outputColorSpace;
    renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
    renderer.render(this.scene, this.camera);
    renderer.outputColorSpace = output;
  }

  dispose() {
    this._drop(this.layers);
    this._drop(this.transferLayers);
    this._gradients.geometry.dispose();
    this._gradients.material.dispose();
  }

  // --- the layers' state (ParallaxLayer) -----------------------------------------------------------------------------

  _buildLayers(page, atlas) {
    const built = [];
    for (const model of page.layers) {
      if (model.kind === 'SEQUENCE') {
        const sequence = PlaxSequence.build(this, model, page, atlas);
        if (!sequence) continue;
        this._drawCycleOf(sequence);
        this._resetPosition(sequence);
        this.scene.add(sequence.quads.group);
        built.push(sequence);
        continue;
      }
      if (model.kind === 'PARTICLES') {
        const l = { model, distanceX: 0, distanceY: 0, quads: new ParticleQuads(), particles: particlesOf(model, page, atlas) };
        this._sizeLayer(l);
        this._resetPosition(l);
        this.scene.add(l.quads.group);
        built.push(l);
        continue;
      }
      if (model.kind === 'EMPTY') {
        const l = { model, distanceX: 0, distanceY: 0, quads: new Quads() };
        this._sizeLayer(l);
        this._resetPosition(l);
        this.scene.add(l.quads.group);
        built.push(l);
        continue;
      }
      const region = atlas?.findRegion(model.regionName, model.regionPosition);
      if (!region) {
        console.error(`plax: region '${model.regionName}' #${model.regionPosition} not found in ${page.atlasName}`);
        continue;
      }
      if (region.rotate) console.warn(`plax: region '${model.regionName}' is packed rotated, which is not supported: drawn as is`);
      const l = this._buildLayer(model, region, page.useOriginalSize);
      if (!PlaxAnim.build(l, model, atlas, page.useOriginalSize)) continue;
      if (model.kind === 'FLOOR') PlaxFloor.measure(l);
      this._resetPosition(l);
      this.scene.add(l.quads.group);
      if (model.kind === 'SHADER' && l.anim.length) {
        // Its patches after its tiles, in quads of their own: their effect is given the frame's region, which one
        // material cannot change between draws (ParallaxPageReader.drawImageLayer, r397).
        l.animQuads = new Quads();
        l.animQuads.setEffect(model.shaderEffect);
        this.scene.add(l.animQuads.group);
      }
      built.push(l);
    }
    // The depth fog of each layer, by its speed against the page's front: what ParallaxPageReader.drawImageLayer reckons.
    const front = PlaxEffects.frontSpeedOf(built);
    for (const l of built) {
      l.haze = l.model.kind === 'EMPTY' || l.model.kind === 'PARTICLES' ? 0 : PlaxEffects.fogOf(page.fogStrength, front, l.model);
      l.fogColor = page.fogColor.slice(0, 3);
      // What a MIRROR layer mirrors: its page's top gradient (WholePage_Model.buildLayer).
      l.sky = page.topHalfBottom.slice(0, 3);
      l.zenith = page.topHalfTop.slice(0, 3);
      if (l.model.kind === 'SHADER') l.quads.setEffect(l.model.shaderEffect);
      else if (l.haze > 0 && ['IMAGE', 'SEQUENCE', 'FLOOR', 'LURKER'].includes(l.model.kind)) l.quads.setEffect('PLAIN');
      PlaxDetail.apply(l, this._detail, PlaxDetail.ruleOf(this, l));
    }
    return built;
  }

  _buildLayer(model, region, useOriginalSize) {
    const l = measureRegion(region, useOriginalSize);
    l.model = model;
    l.distanceX = l.distanceY = 0;
    l.wrappedX = l.wrappedY = 0;
    l.incoming = false;
    l.quads = new Quads();
    this._sizeLayer(l);
    return l;
  }

  // A layer is sizeRatio worlds wide; its height follows the image. An EMPTY one is sizeRatio worlds high. A SEQUENCE
  // one is as high as its first segment makes it, and as wide as its cycle, without the padX after the last slot
  // (ParallaxLayer.getWidth).
  _sizeLayer(l) {
    l.width = this.worldWidth * l.model.sizeRatio;
    l.height = l.model.kind === 'EMPTY' || l.model.kind === 'PARTICLES' ? this.worldHeight * l.model.sizeRatio
      : l.imageH * (this.worldWidth / l.imageW) * l.model.sizeRatio;
    if (l.model.kind === 'FLOOR') {
      // Its box (ParallaxLayer.getHeight): its own height, or the tile's; mode 7's depths follow it.
      l.floorH = l.model.floorHeight > 0 ? l.model.floorHeight * (this.worldHeight / 100) : l.height;
      if (l.rowStrips) PlaxFloor.measure(l);
    }
    if (l.model.kind === 'SEQUENCE' && l.edges) {
      l.width = l.height * l.edges[l.cycle.length] + (l.cycle.length - 1) * l.model.padX;
    }
  }

  _resetPosition(l) {
    l.distanceX = l.model.decal_X_Ratio * (this.worldWidth / 100);
    l.distanceY = l.model.decal_Y_Ratio * (this.worldHeight / 100);
    l.wrappedX = l.wrappedY = 0;
  }

  // ParallaxLayer.act.
  _actLayer(l, delta, speedX, speedY) {
    const m = l.model;
    const moveY = -delta * speedY * m.parallaxScalingSpeedY;
    // Planted on a floor: the speed of the row under its foot (PlaxFloor.plant).
    const planted = l.planted ?? NaN;
    const moveX = -delta * (m.speedXAtRest + speedX) * (Number.isNaN(planted) ? m.parallaxScalingSpeedX : planted);
    l.distanceY += moveY;
    l.distanceX += moveX;
    if (l.particles) {
      // Emitted from the view, the particles still move with the depth they are at.
      if (m.particlesAnchor === 'VIEW') l.particles.translateParticles(moveX, moveY);
      l.particles.update(delta);
    }
    // Kept within one tile so the tiling loops stay short and floats stay precise.
    const totalW = l.width + m.padX, totalH = l.height + m.padY;
    if (this._repeatX && totalW > 0) {
      const before = l.distanceX;
      l.distanceX %= totalW;
      // What was wrapped, within the effect's period: FOG's noise runs on across the tiles (ParallaxLayer.wrappedX).
      const period = PlaxEffects.period(m);
      l.wrappedX = period > 0 ? (l.wrappedX + before - l.distanceX) % period : 0;
    }
    if (this._repeatY && totalH > 0) {
      const below = l.distanceY;
      l.distanceY %= totalH;
      // The same on Y, within its period up (ParallaxLayer.wrappedY, r262).
      const periodY = PlaxEffects.periodY(m);
      l.wrappedY = periodY > 0 ? (l.wrappedY + below - l.distanceY) % periodY : 0;
    }
    if (l.rowDistance) PlaxFloor.scroll(l, delta * (m.speedXAtRest + speedX));
  }

  _updateWorld() {
    this.worldHeight = this.worldWidth * this.height / this.width;
  }

  _drop(layers) {
    for (const l of layers) {
      for (const quads of [l.quads, l.animQuads]) {
        if (!quads) continue;
        this.scene.remove(quads.group);
        quads.dispose();
      }
    }
  }

  // --- cross-fades ---------------------------------------------------------------------------------------------------

  // ParallaxPageReader.syncTransferPositions: matched from the front, the incoming layer keeps its own decal and takes
  // the distance its counterpart has scrolled from its decal.
  _syncTransferPositions() {
    const total = Math.max(this.layers.length, this.transferLayers.length);
    const oldOffset = total - this.layers.length, newOffset = total - this.transferLayers.length;
    const unitX = this.worldWidth / 100, unitY = this.worldHeight / 100;
    for (let slot = Math.max(oldOffset, newOffset); slot < total; slot++) {
      const from = this.layers[slot - oldOffset], to = this.transferLayers[slot - newOffset];
      to.distanceX = to.model.decal_X_Ratio * unitX + from.distanceX - from.model.decal_X_Ratio * unitX;
      to.distanceY = to.model.decal_Y_Ratio * unitY + from.distanceY - from.model.decal_Y_Ratio * unitY;
    }
  }

  _finishTransfert() {
    this.page = this.transferPage;
    this.atlas = this.transferAtlas;
    this._drop(this.layers);
    this._unmask(this.transferLayers);
    this.layers = this.transferLayers;
    for (const l of this.layers) l.incoming = false;
    this._resetTransfert();
  }

  _resetTransfert() {
    this.transferPage = null;
    this.transferAtlas = null;
    this.transferLayers = [];
    this._style = null;
    this._newAlpha = 0;
    this._oldAlpha = 1;
  }

  // SquareBackground.transfertInto: both colours of both gradients, alpha included, toward the incoming page's, as
  // `style` goes (null: straight).
  _fadeGradients(target, seconds, style = null) {
    this._gradientStyle = style;
    this._gradientFrom = [...this._gradient];
    this._gradientTo = target;
    this._gradientElapsed = 0;
    this._gradientDuration = seconds;
    this._gradientFading = true;
    if (seconds <= 0) this._actGradients(0);
  }

  _actGradients(delta) {
    if (!this._gradientFading) return;
    this._gradientElapsed += delta;
    const progress = this._gradientDuration > 0 ? Math.min(1, this._gradientElapsed / this._gradientDuration) : 1;
    this._gradient = this._gradientFrom.map((from, i) => this._gradientStyle
      ? this._gradientStyle.gradient(from, this._gradientTo[i], progress) : lerp(from, this._gradientTo[i], progress));
    if (progress >= 1) this._gradientFading = false;
  }

  // During a cross-fade, slot by slot from the front, the outgoing layer then the incoming one (ParallaxPageReader.draw).
  _drawOrder() {
    const total = Math.max(this.layers.length, this.transferLayers.length);
    const oldOffset = total - this.layers.length, newOffset = total - this.transferLayers.length;
    const order = [];
    for (let slot = 0; slot < total; slot++) {
      // Its slot, for a depth stagger (_alphaOf).
      if (slot >= oldOffset) order.push(Object.assign(this.layers[slot - oldOffset], { slot }));
      if (slot >= newOffset) order.push(Object.assign(this.transferLayers[slot - newOffset], { slot }));
    }
    return order;
  }

  // --- drawing (ParallaxPageReader.draw), in the page's units, y up, turned into pixels at the last moment -----------

  // The two gradients, in screen pixels and opaque: the libGDX heart draws them without blending.
  _drawGradients() {
    const w = this.width, h = this.height, [tt, tb, bt, bb] = this._gradient;
    const topH = h * (1 - this._topHalfSize), bottomH = h * (1 - this._bottomHalfSize);
    const g = this._gradients.geometry, pos = g.attributes.position.array, col = g.attributes.color.array;
    const band = (i, y0, y1, top, bottom) => {
      // An empty band is folded to nothing.
      if (y1 <= y0) y1 = y0;
      pos.set([0, y0, 0, w, y0, 0, w, y1, 0, 0, y1, 0], i * 12);
      // As ShapeRenderer packs them (Color.toFloatBits): each channel cut to 8 bits, (int) (255 * c), not rounded.
      const t = top.slice(0, 3).map(packed), b = bottom.slice(0, 3).map(packed);
      col.set([...t, ...t, ...b, ...b], i * 12);
    };
    band(0, 0, topH, tt, tb);
    band(1, h - bottomH, h, bt, bb);
    g.attributes.position.needsUpdate = g.attributes.color.needsUpdate = true;
    this._gradients.visible = !!this.page;
  }

  // Whether libGDX draws the page's layers without an effect through its page fog's shader: this page or the incoming
  // one has a fog, and no dissolve or grade begins each layer with its own shader (ParallaxPageReader.beginDraw).
  _pageFog() {
    if (this.transferLayers.length && this._style && (this._style.kind === 'DISSOLVE' || this._style.grades())) {
      return false;
    }
    return this.page?.fogStrength > 0 || (this.transferLayers.length > 0 && this.transferPage?.fogStrength > 0);
  }

  // The opacity of the page the layer belongs to: during a cross-fade, the incoming page's or the outgoing one's; with
  // a depth stagger, its slot's, each slot at its own point of the fade (ParallaxPageReader.draw). In a dissolve a
  // layer the mask draws (_masked) is at full opacity while its share of the slot is above 0; an EMPTY one fades to its
  // share (ParallaxPageReader.drawDissolved). Through a colour or in a fog creep only one side of a slot shows, the
  // outgoing layer until the slot is all colour, the incoming one after (PlaxTransfertStyle.showsIncoming): at full
  // opacity when graded (_graded), else faded out by as much as the grade (ParallaxPageReader.drawGraded).
  _alphaOf(l) {
    if (!this.transferLayers.length) return 1;
    if (!this._style || this._style.kind === 'FADE') return l.incoming ? this._newAlpha : this._oldAlpha;
    if (this._style.grades()) {
      const total = Math.max(this.layers.length, this.transferLayers.length);
      if (this._style.showsIncoming(this._newAlpha, l.slot, total) !== !!l.incoming) return 0;
      return this._graded(l) ? 1 : 1 - this._style.gradeAt(this._newAlpha, l.slot, total);
    }
    const share = this._shareOf(l);
    return this._masked(l) ? (share > 0 ? 1 : 0) : share;
  }

  // The layer's share of its slot, 0 to 1: its slot's ramp, the outgoing layer's 1 minus it.
  _shareOf(l) {
    const ramp = this._style.slotRamp(this._newAlpha, l.slot, Math.max(this.layers.length, this.transferLayers.length));
    return l.incoming ? ramp : 1 - ramp;
  }

  // Whether a dissolve draws the layer through its mask now: an IMAGE, SEQUENCE, SHADER, FLOOR or LURKER one, during
  // a dissolve.
  _masked(l) {
    return this.transferLayers.length > 0 && this._style?.kind === 'DISSOLVE' && MASKED.includes(l.model.kind);
  }

  // Whether a transfert through a colour or a fog creep mixes the layer toward it now: an IMAGE, SEQUENCE, SHADER,
  // FLOOR or LURKER one, during one.
  _graded(l) {
    return this.transferLayers.length > 0 && !!this._style?.grades() && MASKED.includes(l.model.kind);
  }

  // Takes the dissolve's mask and the colour's grade off `page`'s layers: the PLAIN shader either gave a layer that had
  // none goes.
  _unmask(page) {
    for (const l of page) {
      if (l.dissolvePlain) {
        l.quads.setEffect(null);
        l.dissolvePlain = false;
      } else if (l.quads.uniforms) {
        for (const quads of [l.quads, l.animQuads]) {
          if (!quads) continue;
          quads.uniforms.dissolve.value.set(0, 0, 0, 0);
          quads.uniforms.grade.value.set(0, 0, 0, 0);
        }
      }
    }
  }

  // A layer's quads draw it; a layer at alpha 0 is not drawn. `patches`: a SHADER layer's patches' quads draw them
  // (_buildLayers).
  _drawLayer(l, patches = false) {
    // Not drawn at its detail level, it scrolls on (PlaxDetail); drawn plain, a SHADER layer's patches go with its tiles.
    if (!l.shown) return;
    this._part = l.animQuads && l.drawnEffect ? (patches ? 2 : 1) : 0;
    if (l.model.kind === 'LURKER') {
      // The lurkers against this view; a figure not there is not drawn (PlaxLurker).
      this._lurkerZoom = this.zoom;
      PlaxLurker.sight(this, this.zoom);
      if (!PlaxLurker.isThere(l)) return;
    }
    const quads = patches ? l.animQuads : l.quads;
    const alpha = this.tint[3] * this._alphaOf(l);
    if (!(alpha > 0)) return;
    // As libGDX packs the batch colour (Color.toFloatBits): the alpha to 254 levels, and, but through the page fog's
    // shader (ParallaxPageReader.drawImageLayer: a page with a fog, a layer without an effect), the tint to 8 bits.
    // That shader takes the tint as a uniform and the layer's fog in the batch colour's red: the fog is cut instead.
    // A SHADER layer drawn plain at its detail level is an image layer there (PlaxEffects.page_fogged).
    const pageFog = this._pageFog() && !['EMPTY', 'PARTICLES'].includes(l.model.kind) && !l.drawnEffect;
    const color = pageFog ? this.tint : this.tint.map(packed);
    quads.setColor(color, packedAlpha(alpha));
    this._hookColor = [...color.slice(0, 3), packedAlpha(alpha)];
    this._quads = quads;
    // Its own view, at its zoom: tiled across, and drawn into the screen at as many pixels a unit.
    const z = this._zoomOf(l);
    this._ppu = this.width / (this.worldWidth * z);
    const m = l.model, viewW = this.worldWidth * z, viewH = this.worldHeight * z;
    const anchor = this._anchorOf(z);
    const x = l.distanceX + anchor[0], y = l.distanceY + anchor[1];
    this._viewW = viewW;
    this._planet = 0;
    this._reach = 0;
    this._panorama = null;
    this._eyeY = viewH / 2;
    // Behind a tower seen from close by: a panorama round its axis (ParallaxPageReader.drawImageLayer); else, round the
    // page's planet.
    const near = PlaxPanorama.nearTowerOf(l.incoming ? this.transferLayers : this.layers);
    const pano = PlaxPanorama.radiusOf(near, l, this.worldWidth);
    if (pano > 0) {
      const camera = PlaxPanorama.cameraOf(near, this.worldWidth);
      const front = camera - near.model.towerRadius * (this.worldWidth / 100);
      this._panorama = [front * pano / (camera + pano), camera / pano, new Float32Array(PlaxPanorama.STRIPS + 1),
        new Float32Array(PlaxPanorama.STRIPS)];
      PlaxPanorama.layOut(this._panorama[0], this._panorama[1], viewW, this._panorama[2], this._panorama[3]);
    } else if (m.kind === 'IMAGE' || m.kind === 'SHADER' || m.kind === 'SEQUENCE') {
      this._planet = this._planetOf(l);
      this._reach = PlaxPlanet.reachOf(this._planet, viewW, viewH);
    }
    if (m.kind === 'EMPTY') {
      // No mirrored copy: the hook draws what it likes in each tile.
      const hook = this._hooks.get(m.name);
      if (hook) this._tile(l, x, y, this._repeatX, this._repeatY, false, viewW, viewH, hook);
      return;
    }
    if (m.kind === 'PARTICLES') {
      this._drawParticleLayer(l, [this.tint[0], this.tint[1], this.tint[2], alpha], x, y, anchor, viewW, viewH);
      return;
    }
    const total = Math.max(this.layers.length, this.transferLayers.length);
    if ((this._masked(l) || this._graded(l)) && !quads.effect) {
      quads.setEffect('PLAIN');
      quads.setColor(color, packedAlpha(alpha));
      l.dissolvePlain = true;
    }
    if (this._masked(l)) {
      const ramp = this._style.slotRamp(this._newAlpha, l.slot, total);
      quads.uniforms.dissolve.value.set(l.incoming ? 2 : 1, ramp, this._style.softness,
        PlaxTransfertStyle.drift(this._effectTime));
      quads.uniforms.cells.value.set(this._style.patches, this._style.cellsUp(viewW, viewH));
    }
    if (this._graded(l)) {
      const c = this._style.color;
      quads.uniforms.grade.value.set(c[0], c[1], c[2], clamp(this._style.gradeAt(this._newAlpha, l.slot, total), 0, 1));
    }
    const u = quads.uniforms;
    if (u) {
      u.view.value.set(this.width, this.height);
      if (m.kind === 'SHADER' && l.drawnEffect) {
        PlaxEffects.apply(u, l, this._effectTime, viewW, viewH, anchor[0], anchor[1],
          [this._repeatX ? 1 : 0, this._repeatY ? 1 : 0]);
      }
      u.haze.value = pageFog ? packed(l.haze) : l.haze;
      u.fog_color.value.set(...l.fogColor);
    }
    // The animated patch's frame, on the clock the effects run on: drawn after each tile (PlaxAnim.draw).
    PlaxAnim.setTime(l, this._effectTime);
    if (patches) PlaxAnim.apply(u, l);
    if (m.kind === 'FLOOR') {
      // Its rows across the whole view, whatever the page repeats; no mirrored copy.
      PlaxFloor.draw(this, l, y, viewW, viewH);
      return;
    }
    if (PlaxTower.isTower(l)) {
      this._planet = 0;
      this._reach = 0;
      // Round its tower in the view's middle, tiled up as the page says; no mirrored copy. Its darkness goes as libGDX
      // puts it: in the colour's blue through the page fog's shader, else multiplied into it (ParallaxLayer.drawTower).
      this._towerFog = pageFog;
      PlaxTower.drawRows(this, l, x, y, viewW, viewH);
      quads.shade = null;
      return;
    }
    this._tile(l, x, y, this._repeatX, this._repeatY, false, viewW, viewH);
    if (m.mirror && this._repeatX !== this._repeatY) {
      // A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
      if (this._repeatX) this._tile(l, x, y + m.padY + l.height, true, false, true, viewW, viewH);
      else this._tile(l, x + m.padX + l.width, y, false, true, true, viewW, viewH);
    }
  }

  // A PARTICLES layer: its effect from the view's corner plus its decal (VIEW), or in each tile (LAYER), its tile's
  // corner at (x, y), in `tint` (ParallaxPageReader.drawParticleLayer). Round a planet each effect is turned as one
  // block, moved and turned as the middle of its layer's box is (PlaxPlanet.turn, r347).
  _drawParticleLayer(l, tint, x, y, anchor, viewW, viewH) {
    const particles = l.particles;
    if (!particles || particles.empty) return;
    this._particlePlanet = this._planetOf(l);
    if (l.model.particlesAnchor === 'VIEW') {
      const vx = anchor[0] + l.model.decal_X_Ratio * (this.worldWidth / 100);
      const vy = anchor[1] + l.model.decal_Y_Ratio * (this.worldHeight / 100);
      // Round a planet, where the middle of its box goes, as far down and across as it goes.
      const midX = vx + l.width / 2, midY = vy + l.height / 2;
      let dx = 0, dy = 0, turn = null;
      if (this._particlePlanet > 0) {
        turn = PlaxPlanet.turn(this._particlePlanet, viewW, midX, midY);
        dx = turn.toX - midX;
        dy = turn.toY - midY;
      }
      if (vx + dx + particles.maxX > 0 && vx + dx + particles.minX < viewW && vy + dy + particles.maxY > 0
        && vy + dy + particles.minY < viewH) {
        particles.draw(l.quads, vx, vy, tint, this._ppu, this.height, turn);
      }
    } else {
      this._particleTint = tint;
      this._tile(l, x, y, this._repeatX, this._repeatY, false, viewW, viewH);
    }
  }

  // The layer at (x, y) plus every repetition, on the requested axes, that intersects the view
  // (x 0..viewW, y 0..viewH).
  _tile(l, x, y, onX, onY, mirror, viewW, viewH, hook = null) {
    const { width, height } = l;
    const sequence = l.model.kind === 'SEQUENCE';
    // A SEQUENCE layer whose pads outweigh its segments is no wider than 0, its slots still drawn: once, below.
    if ((width <= 0 && !sequence) || height <= 0) return;
    // A PARTICLES layer's tile is where its particles are, which may reach past its box.
    const particles = l.model.kind === 'PARTICLES' ? l.particles : null;
    // What is drawn of a tile, from its corner: the box, the particles, or a SEQUENCE layer's slots
    // (ParallaxLayer.getCycleLeft/Right).
    const left = particles ? particles.minX : sequence ? PlaxSequence.cycleLeft(l) : 0;
    const right = particles ? particles.maxX : sequence ? PlaxSequence.cycleRight(l) : width;
    let bottom = particles ? particles.minY : 0;
    const top = particles ? particles.maxY : height;
    if (particles && this._particlePlanet > 0) {
      // Round a planet each tile's effect is turned as its box's middle is: lowered, at most, as the ground is under
      // one whose particles just reach into the view at its side.
      bottom -= PlaxPlanet.dropAt(this._particlePlanet, viewW, 0 - Math.max(right - width / 2, width / 2 - left));
    }
    // Round a planet the view's top corners show what lies a little over it.
    const viewTop = viewH + this._reach;
    // A step <= 0 (a negative padding larger than the image) can't tile: the layer is drawn once.
    const stepX = width + l.model.padX, stepY = height + l.model.padY;
    const tileX = onX && stepX > 0, tileY = onY && stepY > 0;
    const startX = tileX ? x - Math.ceil((x + right) / stepX) * stepX : x;
    const startY = tileY ? y - Math.ceil((y + top) / stepY) * stepY : y;
    const countX = tileX ? Math.ceil((viewW - left - startX) / stepX) + 1 : 1;
    const countY = tileY ? Math.ceil((viewTop - bottom - startY) / stepY) + 1 : 1;
    for (let row = 0; row < countY; row++) {
      const drawY = startY + row * stepY;
      if (drawY + top <= 0 || drawY + bottom >= viewTop) continue;
      for (let column = 0; column < countX; column++) {
        const drawX = startX + column * stepX;
        if (drawX + right <= 0 || drawX + left >= viewW) continue;
        if (particles) {
          const turn = this._particlePlanet > 0
            ? PlaxPlanet.turn(this._particlePlanet, viewW, drawX + width / 2, drawY + height / 2) : null;
          particles.draw(l.quads, drawX, drawY, this._particleTint, this._ppu, this.height, turn);
          continue;
        }
        if (hook) {
          const ppu = this._ppu;
          hook(l.quads, { x: drawX * ppu, y: this.height - (drawY + height) * ppu, width: width * ppu, height: height * ppu },
            this._hookColor, l);
          continue;
        }
        let fx = l.model.flipX, fy = l.model.flipY;
        // Mirrored: flipped vertically when tiling on X, horizontally when tiling on Y.
        if (mirror) { if (onX) fy = !fy; else fx = !fx; }
        if (sequence) PlaxSequence.draw(this, l, drawX, drawY, 0, viewW, fx, fy);
        else {
          if (this._part !== 2) this._drawRegion(l, drawX, drawY, width, height, fx, fy);
          if (this._part !== 1) PlaxAnim.draw(this, l, drawX, drawY, fx, fy);
        }
      }
    }
  }

  // The packed image of `l` (a layer, or a sequence's segment) in a box at (x, y); a flip mirrors where it sits in the
  // box too. Into the quads of the layer being drawn.
  _drawRegion(l, x, y, width, height, fx, fy) {
    const drawW = width * l.packedW, drawH = height * l.packedH;
    const left = x + width * (fx ? 1 - l.trimLeft - l.packedW : l.trimLeft);
    const bottom = y + height * (fy ? 1 - l.trimBottom - l.packedH : l.trimBottom);
    const texture = l.region.page.texture;
    if (this._panorama) {
      // Behind a near tower: cut in strips, each placed round the panorama and scaled about the eye.
      PlaxPanorama.draw(this, texture, l.src, left, bottom, drawW, drawH, fx, fy, this._panorama, this._eyeY, this._viewW);
      return;
    }
    if (this._planet > 0) {
      // Round the page's planet: cut in strips, each wrapped round it (ParallaxPageReader.drawImageLayer).
      PlaxPlanet.draw(this, texture, l.src, left, bottom, drawW, drawH, fx, fy, this._planet, this._viewW);
      return;
    }
    const ppu = this._ppu, top = this.height - (bottom + drawH) * ppu;
    this._quads.add(texture, left * ppu, top, drawW * ppu, drawH * ppu, l.src, fx, fy);
  }
}

// --- SEQUENCE layers (PlaxSequence: ParallaxLayer's sequence half) ----------------------------------------------------

// A SEQUENCE layer's cycle: its segments measured when the layer is built, its slots laid out when its seed changes,
// never in a frame, and drawn through the background's _drawRegion.
export const PlaxSequence = {
  // WholePage_Model.buildLayer, ParallaxLayer.measureSegments: its first segment sizes it as an image layer's region
  // does, every segment is as wide as the layer's height and its own image make it, the first as wide as the layer.
  // Null when a segment's region is missing. Its cycle is not laid out yet (layOut).
  build(bg, model, fromPage, fromAtlas) {
    if (!model.sequenceSegments.length) {
      console.error(`plax: the sequence layer '${model.name}' has no segments`);
      return null;
    }
    const segments = [], weights = [];
    for (const named of model.sequenceSegments) {
      const region = fromAtlas?.findRegion(named.regionName, named.regionPosition);
      if (!region) {
        console.error(`plax: region '${named.regionName}' #${named.regionPosition} of the sequence layer `
          + `'${model.name}' not found in ${fromPage.atlasName}`);
        return null;
      }
      if (region.rotate) console.warn(`plax: region '${named.regionName}' is packed rotated, which is not supported: drawn as is`);
      segments.push(measureRegion(region, fromPage.useOriginalSize));
      weights.push(named.weight);
    }
    // The first segment sizes the layer, as an image layer's region does.
    const l = bg._buildLayer(model, segments[0].region, fromPage.useOriginalSize);
    l.segments = segments;
    l.weights = weights;
    l.narrowest = Infinity;
    for (const segment of segments) l.narrowest = Math.min(l.narrowest, segment.aspect);
    return l;
  },

  // The layer's cycle from `seed`, and where each slot starts, without the pads, in layer heights: slot i at
  // height * edges[i] + i * padX. One edge more than the slots.
  layOut(l, seed) {
    l.cycle = drawCycle(seed, l.weights, l.model.sequenceLength);
    const edges = new Float64Array(l.cycle.length + 1);
    let edge = 0;
    for (let slot = 0; slot < l.cycle.length; slot++) {
      edges[slot] = edge;
      edge += l.segments[l.cycle[slot]].aspect;
    }
    edges[l.cycle.length] = edge;
    l.edges = edges;
  },

  // ParallaxLayer.getCycleLeft: where a SEQUENCE layer's leftmost slot starts, from the tile's corner; below 0 only
  // when a negative padX wider than a segment takes slots back past it.
  cycleLeft(l) {
    if (PlaxSequence.ordered(l)) return 0;
    let left = 0;
    for (let slot = 0; slot < l.cycle.length; slot++) left = Math.min(left, l.height * l.edges[slot] + slot * l.model.padX);
    return left;
  },

  // ParallaxLayer.getCycleRight: where its rightmost slot ends; the layer's width unless slots go back.
  cycleRight(l) {
    if (PlaxSequence.ordered(l)) return l.width;
    let right = -Infinity;
    for (let slot = 0; slot < l.cycle.length; slot++) {
      right = Math.max(right, l.height * (l.edges[slot] + l.segments[l.cycle[slot]].aspect) + slot * l.model.padX);
    }
    return right;
  },

  // Every slot starts right of the one before: no negative padX outweighs the narrowest segment.
  ordered(l) {
    return l.height * l.narrowest + l.model.padX > 0;
  },

  // ParallaxLayer.drawCycle: the slots of a SEQUENCE layer's cycle, starting at (x, y), that reach between `fromX` and
  // `toX`, each drawn by `bg`'s _drawRegion. A binary search finds the first, then the walk stops past `toX`, so a
  // long cycle costs only what shows. A flip mirrors each segment in its own slot; the slots keep their order.
  draw(bg, l, x, y, fromX, toX, fx, fy) {
    const { height, edges, cycle } = l, pad = l.model.padX, slots = cycle.length;
    // A negative padX wider than a segment makes the edges go back: then every slot is looked at.
    const inOrder = PlaxSequence.ordered(l);
    let first = 0;
    if (inOrder) {
      let last = slots;
      while (first < last) {
        const middle = (first + last) >> 1;
        if (x + height * edges[middle + 1] + middle * pad > fromX) last = middle;
        else first = middle + 1;
      }
    }
    for (let slot = first; slot < slots; slot++) {
      const left = x + height * edges[slot] + slot * pad;
      if (left >= toX) {
        if (inOrder) break;
        continue;
      }
      const segment = l.segments[cycle[slot]], width = height * segment.aspect;
      if (left + width <= fromX) continue;
      bg._drawRegion(segment, left, y, width, height, fx, fy);
    }
  },
};

// --- FLOOR layers (PlaxFloor: ParallaxLayer's floor half, ParallaxPageReader.plant) ----------------------------------

// A FLOOR layer's rows, measured when the layer is built or resized, never in a frame, scrolled, drawn, and the layers
// in front planted on them.
export const PlaxFloor = {
  // The most rows a FLOOR layer is cut in (ParallaxLayer.MAX_FLOOR_ROWS), and mode 7's tilt (ParallaxLayer.FLOOR_TILT).
  MAX_FLOOR_ROWS: 512,
  FLOOR_TILT: 0.6,

  // ParallaxLayer.measureFloor: a FLOOR layer's rows, bottom to top, from its settings and its image: each row's speed
  // (the bottom row's to the top row's, straight, at the row's middle), its stretch across (MODE7: its speed over the
  // bottom row's; LINE_SCROLL: 1) and the strip of the image it draws, in the atlas' pixels (MODE7: where the ground is
  // at the row's depth, the image's height one tile of depth; LINE_SCROLL: the image's rows i/n to (i+1)/n).
  measure(l) {
    const m = l.model;
    const n = clamp(m.floorRows | 0, 2, PlaxFloor.MAX_FLOOR_ROWS);
    const bottom = f32p(m.parallaxScalingSpeedX);
    const top = f32p(m.floorTopSpeed);
    const mode7 = m.floorMapping === 'MODE7';
    const recedes = mode7 && Math.abs(bottom) > Math.abs(top) && bottom * top >= 0 && l.height > 0;
    const nearInHeights = recedes ? l.floorH / l.height * bottom / (PlaxFloor.FLOOR_TILT * (bottom - top)) : 0;
    const drawn = l.src;
    l.rowSpeed = new Float64Array(n);
    l.rowStretch = new Float64Array(n);
    l.rowStrips = [];
    for (let i = 0; i < n; i++) {
      const t0 = i / n, t1 = (i + 1) / n, tc = (i + 0.5) / n;
      const speed = bottom + (top - bottom) * tc;
      l.rowSpeed[i] = speed;
      l.rowStretch[i] = mode7 && bottom !== 0 ? Math.max(0.02, speed / bottom) : 1;
      let from = t0, to = t1;
      if (recedes) {
        from = PlaxFloor._along(bottom, top, t0, nearInHeights);
        to = PlaxFloor._along(bottom, top, t1, nearInHeights);
        const start = from - Math.floor(from);
        to = Math.min(1, start + (to - from));
        from = start;
      }
      // The image's rows count down from its top, t up from its bottom.
      l.rowStrips.push({ x: drawn.x, y: drawn.y + drawn.h * (1 - to), w: drawn.w, h: drawn.h * (to - from) });
    }
    // Allocated once: a resize measures the rows again and keeps where they have scrolled to.
    if (!l.rowDistance || l.rowDistance.length !== n) l.rowDistance = new Float64Array(n).fill(l.distanceX);
  },

  // Each row of a FLOOR layer scrolled by `travel` (the page's speed times the step) times its own speed, kept within
  // one of its tiles: a floor is tiled across whatever the page repeats (ParallaxLayer.act).
  scroll(l, travel) {
    for (let i = 0; i < l.rowDistance.length; i++) {
      const step = l.width * l.rowStretch[i];
      const distance = l.rowDistance[i] - travel * l.rowSpeed[i];
      l.rowDistance[i] = step > 0 ? distance % step : distance;
    }
  },

  // ParallaxPageReader.plant: every layer in front of the page's last FLOOR layer scrolls at the speed of the floor's
  // row under its foot (footY); one whose foot is above the floor's top row, and every other layer, at its own.
  plant(pageLayers, worldHeight) {
    let floor = -1;
    pageLayers.forEach((l, i) => { if (l.model.kind === 'FLOOR') floor = i; });
    pageLayers.forEach((l, i) => {
      l.planted = floor >= 0 && i > floor ? PlaxFloor.plantedSpeedOn(pageLayers[floor], l, worldHeight) : NaN;
    });
  },

  // ParallaxPageReader.plantedSpeedOn: the speed of `floor`'s row under `l`'s foot, NaN above the floor's top row.
  plantedSpeedOn(floor, l, worldHeight) {
    const bottom = floor.model.decal_Y_Ratio * (worldHeight / 100);
    const height = floor.floorH;
    let t = height > 0 ? (PlaxFloor.footY(l, worldHeight) - bottom) / height : 0;
    if (t > 1) return NaN;
    t = Math.max(0, t);
    return floor.model.parallaxScalingSpeedX + (floor.model.floorTopSpeed - floor.model.parallaxScalingSpeedX) * t;
  },

  // ParallaxLayer.getFootY: where a layer stands, its decal, and over it the rows an atlas trimmed off an IMAGE or
  // SHADER layer's image's bottom (a LURKER's too: drawn as an IMAGE layer).
  footY(l, worldHeight) {
    let foot = l.model.decal_Y_Ratio * (worldHeight / 100);
    if (['IMAGE', 'SHADER', 'LURKER'].includes(l.model.kind)) {
      foot += l.height * (l.model.flipY ? 1 - l.trimBottom - l.packedH : l.trimBottom);
    }
    return foot;
  },

  // ParallaxLayer.drawFloor: a FLOOR layer's rows in its box, its bottom at `y`, each tiled across the view (x 0 to
  // `viewW`) from where it has scrolled, its tiles `width * stretch` wide, stretched about the view's middle; into the
  // quads of the layer being drawn, at `bg`'s scale.
  draw(bg, l, y, viewW, viewH) {
    const n = l.rowStrips.length;
    const width = l.width;
    const rowH = l.floorH / n;
    const middle = viewW / 2;
    const texture = l.region.page.texture;
    const ppu = bg._ppu;
    const screenH = bg.height;
    for (let i = 0; i < n; i++) {
      const rowY = y + i * rowH;
      if (rowY + rowH <= 0 || rowY >= viewH) continue;
      const stretch = l.rowStretch[i];
      const across = width * stretch;
      if (!(across > 0)) continue;
      const origin = middle + (0 - middle) * stretch + l.rowDistance[i];
      const first = origin + across * Math.floor((0 - origin) / across);
      const count = Math.ceil((viewW - first) / across);
      for (let k = 0; k < count; k++) {
        const x = first + k * across;
        bg._quads.add(texture, x * ppu, screenH - (rowY + rowH) * ppu, across * ppu, rowH * ppu, l.rowStrips[i], false, false);
      }
    }
  },

  // ParallaxLayer.along: how far along the ground the row at `t` is from the bottom row, in image heights.
  _along(bottom, top, t, nearInHeights) {
    const speed = bottom + (top - bottom) * t;
    return (bottom / speed - 1) * nearInHeights;
  },
};

// --- LURKER layers (PlaxLurker: ParallaxPageReader.sightLurkers, r371; docs/lurker-layers.md) ------------------------

// The figure appears or vanishes only while nothing of it is on screen. Gone at the sighting after one where it was
// seen, else there by its seed's draws (sequenceNext: the JVM's picks), or when the game says (reveal). Its state is on
// the layer: lurkerState, lurkerStarted, lurkerThere, lurkerSeen, lurkerInSight, lurkerForced. Run every act() step
// against the view of the last draw, and at each draw against its own.
export const PlaxLurker = {
  // Whether layer `l`'s figure is drawn now (ParallaxLayer.isLurkerThere): true for one no sighting has run yet.
  isThere(l) {
    return !l.lurkerStarted || l.lurkerThere;
  },

  // Runs the sightings of every LURKER layer on screen, of both pages during a cross-fade, at the camera zoom `zoom`
  // of the last draw (NaN before the first: nothing is in the view).
  sight(bg, zoom) {
    PlaxLurker._sightPage(bg, bg.layers, false, zoom);
    PlaxLurker._sightPage(bg, bg.transferLayers, true, zoom);
  },

  _sightPage(bg, page, incoming, zoom) {
    page.forEach((figure, i) => {
      if (figure.model.kind !== 'LURKER') return;
      if (!figure.lurkerStarted) PlaxLurker._start(figure);
      const inSight = PlaxLurker._inSight(bg, page, i, incoming, zoom);
      if (inSight && figure.lurkerThere) figure.lurkerSeen = true;
      else if (!inSight && figure.lurkerInSight) PlaxLurker._hide(figure);
      figure.lurkerInSight = inSight;
    });
  },

  // The figure's first draw, from its seed (0: ZERO_SEED), as each later one: rule 2.
  _start(figure) {
    const seed = figure.model.lurkerSeed >>> 0;
    figure.lurkerState = sequenceNext(seed !== 0 ? seed : ZERO_SEED);
    figure.lurkerThere = PlaxLurker._drawsThere(figure);
    figure.lurkerSeen = false;
    figure.lurkerInSight = false;
    figure.lurkerForced = false;
    figure.lurkerStarted = true;
  },

  // A sighting has ended: whether the figure is there at the next one. Forced by reveal; else gone after it was seen;
  // else the state steps once and it is there by 1 chance in its odds. Integers only.
  _hide(figure) {
    if (figure.lurkerForced) figure.lurkerThere = true;
    else if (figure.lurkerSeen) figure.lurkerThere = false;
    else {
      figure.lurkerState = sequenceNext(figure.lurkerState);
      figure.lurkerThere = PlaxLurker._drawsThere(figure);
    }
    figure.lurkerForced = false;
    figure.lurkerSeen = false;
  },

  // (state >>> 1) % lurkerOdds == 0, the state an unsigned 32-bit int.
  _drawsThere(figure) {
    return (figure.lurkerState >>> 1) % Math.max(1, figure.model.lurkerOdds | 0) === 0;
  },

  // ParallaxPageReader.lurkerInSight: whether a copy of the figure `index` of `page` is in the view (its tile's box;
  // the view's bottom left corner at 0, 0) and not wholly covered by one layer in front of it. The cover counts only
  // where it was worked out for: a page repeating on X, the figure's own row, no planet, no panorama, the dolly at
  // zoom 1, no cross-fade, the tint at full opacity, and the layers' Y apart as saved.
  _inSight(bg, page, index, incoming, zoom) {
    const figure = page[index];
    const width = figure.width, height = figure.height;
    if (!(width > 0) || !(height > 0) || Number.isNaN(zoom)) return false;
    const z = bg.dolly ? PlaxEffects.dollyZoom(zoom, PlaxEffects.frontSpeedOf(page), figure.model) : zoom;
    const vw = bg.worldWidth * z, vh = bg.worldHeight * z;
    const anchor = bg._anchorOf(z);
    const originX = anchor[0] + figure.distanceX, originY = anchor[1] + figure.distanceY;
    const fromPage = incoming ? bg.transferPage : bg.page;
    const covers = bg._repeatX && figure.model.lurkerCover.length > 0 && !(fromPage.planetRadius > 0)
      && !PlaxPanorama.nearTowerOf(page) && (!bg.dolly || zoom === 1)
      && bg.transferLayers.length === 0 && bg.tint[3] >= 1;
    const stepX = width + figure.model.padX, stepY = height + figure.model.padY;
    const tileX = bg._repeatX && stepX > 0, tileY = bg._repeatY && stepY > 0;
    const firstColumn = tileX ? -Math.ceil((originX + width) / stepX) : 0;
    const firstRow = tileY ? -Math.ceil((originY + height) / stepY) : 0;
    for (let row = firstRow; ; row++) {
      const y = originY + row * stepY;
      if (row > firstRow && (!tileY || y >= vh)) break;
      if (!(y + height > 0 && y < vh)) continue;
      for (let column = firstColumn; ; column++) {
        const x = originX + column * stepX;
        if (column > firstColumn && (!tileX || x >= vw)) break;
        if (x + width > 0 && x < vw && !(covers && row === 0 && PlaxLurker._covered(bg, page, index, x - anchor[0]))) {
          return true;
        }
      }
    }
    return false;
  },

  // ParallaxPageReader.lurkerCovered: whether a layer in front covers the copy of figure `index` whose tile corner is
  // `x` from the anchor: its offset against that layer's tiles, modulo their period, in one of the cover's intervals.
  _covered(bg, page, index, x) {
    const figure = page[index];
    const cover = figure.model.lurkerCover;
    for (let k = 0; k + 2 < cover.length; k += 3) {
      const nearAt = index + (cover[k] | 0), from = cover[k + 1], to = cover[k + 2];
      if (nearAt <= index || nearAt >= page.length) continue;
      const near = page[nearAt];
      const period = near.width + near.model.padX;
      if (!(period > 0)) continue;
      // Their Y apart as saved, which the sweep took them at.
      const restApart = (figure.model.decal_Y_Ratio - near.model.decal_Y_Ratio) * bg.worldHeight / 100;
      if (Math.abs(figure.distanceY - near.distanceY - restApart) > 1e-3 * figure.height) continue;
      const d = ((x - near.distanceX) % period + period) % period;
      if (((d - from) % period + period) % period <= to - from) return true;
    }
    return false;
  },

  // ParallaxPageReader.reveal: the next sighting of every LURKER layer named `name` in `page` shows its figure, at
  // once when it is not in sight now, else from the end of this sighting.
  reveal(page, name) {
    for (const figure of page) {
      if (figure.model.kind !== 'LURKER' || figure.model.name !== name) continue;
      if (!figure.lurkerStarted) PlaxLurker._start(figure);
      if (figure.lurkerInSight) figure.lurkerForced = true;
      else figure.lurkerThere = true;
    }
  },
};

// --- the animated patch (PlaxAnim: format 16, docs/animated-layers.md) -----------------------------------------------

// A layer's animated patch: WholePage_Model.findFrames, ParallaxLayer.setAnimTime and drawAnim, line for line. Drawn
// after each tile through _drawRegion, so the layer's effect and the fog take it as they take the tile.
export const PlaxAnim = {
  // The patch's frames for layer `l` built from `model`: the regions named animRegion in the atlas's order, each
  // measured as the layer's region is (measureRegion). Only an IMAGE or SHADER layer has one. False, said in the log,
  // when the atlas has none of that name: libGDX fails the load.
  build(l, model, atlas, useOriginalSize) {
    l.anim = [];
    l.animFrame = 0;
    if (!['IMAGE', 'SHADER'].includes(model.kind) || !model.animRegion) return true;
    const frames = [];
    for (;;) {
      const region = atlas?.findRegion(model.animRegion, frames.length);
      if (!region) break;
      frames.push(measureRegion(region, useOriginalSize));
    }
    if (!frames.length) {
      console.error(`plax: region '${model.animRegion}' #0 not found in the atlas`);
      return false;
    }
    l.anim = frames;
    return true;
  },

  // ParallaxLayer.setAnimTime: frame floor(`time` x fps) modulo the count, a negative one wrapped, NaN or infinite 0.
  setTime(l, time) {
    const count = l.anim?.length ?? 0;
    if (count === 0) return;
    const f = Math.floor(time * l.model.animFps) % count;
    if (Number.isNaN(f)) {
      l.animFrame = 0;
      return;
    }
    l.animFrame = f < 0 ? f + count : f;
  },

  // ParallaxLayer.drawAnim: the current frame over the tile at (x, y), its place in the layer's image scaled to the
  // drawn size, mirrored with the tile by a flip.
  draw(bg, l, x, y, fx, fy) {
    if (!l.anim?.length) return;
    const width = l.width, height = l.height;
    const scaleX = width / l.imageW, scaleY = height / l.imageH;
    const frame = l.anim[l.animFrame];
    const patchW = frame.imageW * scaleX, patchH = frame.imageH * scaleY;
    const ax = l.model.animX * scaleX, ay = l.model.animY * scaleY;
    const left = fx ? x + width - (ax + patchW) : x + ax;
    const bottom = fy ? y + height - (ay + patchH) : y + ay;
    bg._drawRegion(frame, left, bottom, patchW, patchH, fx, fy);
  },

  // GdxLayerEffects.patchUniforms: a SHADER layer's patch quads' uniforms `u` given the current frame's region and
  // drawn size in the place of the layer's (r397): the effect keeps its samples inside the region it is given.
  apply(u, l) {
    const frame = l.anim[l.animFrame];
    const image = frame.region.page.texture.image, src = frame.src;
    u.region.value.set(src.x / image.width, src.y / image.height, (src.x + src.w) / image.width,
      (src.y + src.h) / image.height);
    u.size.value.set(frame.imageW * l.width / l.imageW * frame.packedW, frame.imageH * l.height / l.imageH * frame.packedH);
  },
};

// --- planets (PlaxPlanet: core's PlanetBatch, r277, r347; docs/depth-tricks.md §3) -----------------------------------

// A page round its planet: core's PlanetBatch and ParallaxPageReader.planetInView, planetReachOf and planetPlaceAt, as
// plax_planet.gd has them. The layer's view is the side of a world turning round one centre `radius` under the middle
// of its bottom: a point (x, y) of the view goes to the angle (x - middle) / radius round it, at radius plus its height
// from it, so what scrolls across turns and what stands up leans out along the radius. Each IMAGE, SHADER and SEQUENCE
// layer is cut in vertical strips fixed in the view, each piece drawn as the quad its four corners go to;
// PlaxBackground._drawRegion hands its regions here.
export const PlaxPlanet = {
  // PlanetBatch.STRIPS: how many strips the view is cut in, across.
  STRIPS: 256,

  // ParallaxPageReader.planetInView: the radius, in page units, of the planet `radius` percent of `worldWidth` in a
  // layer's view at zoom `z`, the camera at `zoom` (a dollied layer's view is scaled back into the camera's, so every
  // layer is wrapped round the same circle on screen); 0 for none.
  radiusOf(radius, worldWidth, z, zoom) {
    const r = radius * (worldWidth / 100);
    return r > 0 ? r * z / zoom : 0;
  },

  // ParallaxPageReader.planetReachOf: how far above a view `viewW` by `viewH` its top corners show, round a planet of
  // `radius` under its bottom; 0 for none.
  reachOf(radius, viewW, viewH) {
    if (!(radius > 0)) return 0;
    const far = radius + viewH;
    return Math.sqrt(far * far + viewW * viewW / 4) - far;
  },

  // PlanetBatch.placeX/placeY: where (x, y) of a view from (0, 0), `viewW` wide, goes round a centre `radius` under the
  // middle of its bottom, in page units, y up.
  place(radius, viewW, x, y) {
    const a = (x - viewW / 2) / radius;
    return [viewW / 2 + (radius + y) * Math.sin(a), -radius + (radius + y) * Math.cos(a)];
  },

  // How far what stands at `x` leans round the planet, in radians clockwise on screen: right of the middle, to the
  // right. ParallaxPageReader.planetAngleAt, which is counterclockwise in degrees.
  angleAt(radius, viewW, x) {
    return radius > 0 ? (x - viewW / 2) / radius : 0;
  },

  // ParallaxPageReader.particleDrop: how far the ground under `x` is lowered round the planet.
  dropAt(radius, viewW, x) {
    return -PlaxPlanet.place(radius, viewW, x, 0)[1];
  },

  // PlanetBatch.turn: a particle effect turned as one block, moved and turned as the point (x, y) is, its middle; in
  // floats, as libGDX turns its vertices (PlaxParticles.draw).
  turn(radius, viewW, x, y) {
    const middle = viewW / 2;
    const a = f32p(f32p(x - middle) / radius);
    const angle = -a;
    return {
      pivotX: x, pivotY: y,
      toX: f32p(middle + f32p(f32p(radius + y) * f32p(Math.sin(a)))),
      toY: f32p(f32p(-radius) + f32p(f32p(radius + y) * f32p(Math.cos(a)))),
      cos: f32p(Math.cos(angle)), sin: f32p(Math.sin(angle)),
    };
  },

  // PlanetBatch.draw: `src` of `texture` in the box from (left, bottom), drawW by drawH page units (x 0..viewW of the
  // layer's view), flipped as `fx`, `fy` say, cut at the strips, each piece the quad its corners go to round a centre
  // `radius` under the view. What lies outside the view is not drawn.
  draw(bg, texture, src, left, bottom, drawW, drawH, fx, fy, radius, viewW) {
    const step = viewW / PlaxPlanet.STRIPS;
    const right = left + drawW;
    if (!(drawW > 0) || !(step > 0) || right <= 0 || left >= viewW) return;
    const first = Math.max(0, Math.floor(left / step));
    const last = Math.min(PlaxPlanet.STRIPS - 1, Math.floor(right / step));
    const W = texture.image.width, H = texture.image.height;
    // The image's top at the box's top and its bottom at the box's bottom; flipped on Y, the other way round.
    const vTop = (fy ? src.y + src.h : src.y) / H;
    const vBottom = (fy ? src.y : src.y + src.h) / H;
    const top = bottom + drawH;
    const pixels = at => [at[0] * bg._ppu, bg.height - at[1] * bg._ppu];
    for (let strip = first; strip <= last; strip++) {
      const p0 = Math.max(left, strip * step);
      const p1 = Math.min(right, (strip + 1) * step);
      if (!(p1 > p0)) continue;
      // The piece's share of the image, left to right; flipped on X the image runs right to left.
      const t0 = (p0 - left) / drawW, t1 = (p1 - left) / drawW;
      const u0 = (src.x + src.w * (fx ? 1 - t0 : t0)) / W;
      const u1 = (src.x + src.w * (fx ? 1 - t1 : t1)) / W;
      // SpriteBatch's order: bottom left, top left, top right, bottom right.
      bg._quads.addCorners(texture, [
        ...pixels(PlaxPlanet.place(radius, viewW, p0, bottom)), ...pixels(PlaxPlanet.place(radius, viewW, p0, top)),
        ...pixels(PlaxPlanet.place(radius, viewW, p1, top)), ...pixels(PlaxPlanet.place(radius, viewW, p1, bottom))],
      [u0, vBottom, u0, vTop, u1, vTop, u1, vBottom]);
    }
  },
};

// --- towers (PlaxTower: ParallaxLayer.drawTower, r276; docs/depth-tricks.md §2) --------------------------------------

// An IMAGE layer wrapped round a tower: ParallaxPageReader's towerRows and ParallaxLayer.drawTower, as plax_tower.gd.
export const PlaxTower = {
  // How many strips a tower's front half is cut in, by angle (ParallaxLayer.TOWER_STRIPS).
  TOWER_STRIPS: 128,

  // ParallaxLayer.isTower: an IMAGE layer with a radius.
  isTower(l) {
    return l.model.kind === 'IMAGE' && l.model.towerRadius > 0;
  },

  // ParallaxPageReader.towerRows: the tower round its axis in the view's middle, its tile at (x, y); once, or on a page
  // repeating on Y every repetition up that intersects the view (y 0 to `viewH`).
  drawRows(bg, l, x, y, viewW, viewH) {
    const height = l.height;
    const stepY = height + l.model.padY;
    if (!(height > 0)) return;
    const tileY = bg._repeatY && stepY > 0;
    const startY = tileY ? y - Math.ceil((y + height) / stepY) * stepY : y;
    const countY = tileY ? Math.ceil((viewH - startY) / stepY) + 1 : 1;
    for (let row = 0; row < countY; row++) {
      const drawY = startY + row * stepY;
      if (drawY + height <= 0 || drawY >= viewH) continue;
      PlaxTower.draw(bg, l, viewW / 2, x, drawY, 0, viewW, viewH / 2);
    }
  },

  // ParallaxLayer.towerScaleAt: how much a tower seen from `camera` radii from its axis scales its wall at angle `a`
  // from its front, about the view's middle: (camera - 1) / (camera - cos a); 1 for a camera up to 1 (far away).
  scaleAt(camera, a) {
    return camera > 1 ? (camera - 1) / (camera - Math.cos(a)) : 1;
  },

  // ParallaxLayer.drawTower: the layer round a cylinder of its radius (percent of the world width) whose axis is at
  // `axisX`, its box's bottom at `y`. The point an arc s round the wall from its front shows what the flat layer, its
  // tile at `x`, shows at axisX + s, drawn at axisX + radius sin(s / radius); the front half cut in TOWER_STRIPS strips
  // of equal angle and at each image's edges, each piece at 1 - towerShade (1 - cos a) of the tint, at its middle. Seen
  // from close by (towerCamera above 1, r350), each piece is also scaled about `eyeY` by scaleAt at its middle.
  draw(bg, l, axisX, x, y, viewLeft, viewRight, eyeY) {
    const m = l.model;
    const radius = m.towerRadius * (bg.worldWidth / 100);
    const width = l.width, height = l.height;
    if (!(radius > 0) || !(width > 0) || !(height > 0)) return;
    const stripAngle = Math.PI / PlaxTower.TOWER_STRIPS;
    // What shows of the front half: its arc from the view's left edge to its right one.
    const from = radius * Math.asin(clamp((viewLeft - axisX) / radius, -1, 1));
    const to = radius * Math.asin(clamp((viewRight - axisX) / radius, -1, 1));
    if (!(to > from)) return;
    const fx = m.flipX, fy = m.flipY;
    // The image in its tile, along the arc, and up the box, as _drawRegion places it.
    const imageLeft = width * (fx ? 1 - l.trimLeft - l.packedW : l.trimLeft);
    const imageW = width * l.packedW;
    const drawH = height * l.packedH;
    const bottom = y + height * (fy ? 1 - l.trimBottom - l.packedH : l.trimBottom);
    // Tile k's image starts at an arc of corner + k * step; a step <= 0 can't tile: the image goes round once.
    const corner = x - axisX + imageLeft;
    const step = width + m.padX;
    let first = 0, last = 0;
    if (step > 0) {
      first = Math.floor((from - corner - imageW) / step);
      last = Math.ceil((to - corner) / step);
    }
    const src = l.src;
    const texture = l.region.page.texture;
    const ppu = bg._ppu;
    const camera = m.towerCamera;
    for (let k = first; k <= last; k++) {
      const left = corner + k * step;
      const lo = Math.max(from, left);
      const hi = Math.min(to, left + imageW);
      if (!(hi > lo)) continue;
      let strip = Math.max(0, Math.floor((lo / radius + Math.PI / 2) / stripAngle));
      const lastStrip = Math.min(PlaxTower.TOWER_STRIPS - 1, Math.floor((hi / radius + Math.PI / 2) / stripAngle));
      for (; strip <= lastStrip; strip++) {
        const p0 = Math.max(lo, radius * (strip * stripAngle - Math.PI / 2));
        const p1 = Math.min(hi, radius * ((strip + 1) * stripAngle - Math.PI / 2));
        if (!(p1 > p0)) continue;
        const x0 = axisX + radius * Math.sin(p0 / radius);
        const x1 = axisX + radius * Math.sin(p1 / radius);
        const mid = (p0 + p1) / (2 * radius);
        const bright = 1 - m.towerShade * (1 - Math.cos(mid));
        const scale = PlaxTower.scaleAt(camera, mid);
        const pieceY = eyeY + (bottom - eyeY) * scale;
        // The piece's share of its image, left to right; flipped on X the image runs right to left.
        const f0 = (p0 - left) / imageW, f1 = (p1 - left) / imageW;
        const g0 = fx ? 1 - f1 : f0, g1 = fx ? 1 - f0 : f1;
        const piece = { x: src.x + src.w * g0, y: src.y, w: src.w * (g1 - g0), h: src.h };
        const pxW = (x1 - x0) * ppu, pxH = drawH * scale * ppu;
        bg._quads.shade = bg._towerShade(bright);
        bg._quads.add(texture, x0 * ppu, bg.height - pieceY * ppu - pxH, pxW, pxH, piece, fx, fy);
      }
    }
  },
};

// --- panoramas (PlaxPanorama: core's PanoramaBatch, r350; docs/depth-tricks.md §2) -----------------------------------

// The layers behind a tower seen from close by: ParallaxPageReader.nearTowerOf/panoramaRadiusOf and core's
// PanoramaBatch, as plax_panorama.gd. Each IMAGE, SHADER and SEQUENCE layer slower than the tower, and not still, is the
// far side of a cylinder round the tower's axis; its point at angle t from that side's middle is drawn at
// axis + rho sin t (q + 1) / (q + cos t) and scaled about the eye by (q + 1) / (q + cos t). Drawn as vertical strips
// fixed in the view, each piece placed by its edges and scaled at its strip's middle; PlaxBackground._drawRegion hands
// its regions here when the layer is one.
export const PlaxPanorama = {
  // PanoramaBatch.STRIPS: how many strips the view is cut in, across.
  STRIPS: 128,

  // ParallaxLayer.isNearTower: a tower whose camera stands more than one radius from its axis.
  isNearTower(l) {
    return PlaxTower.isTower(l) && l.model.towerCamera > 1;
  },

  // ParallaxPageReader.nearTowerOf: the page's tower seen from close by, the fastest when there are more; null for none.
  nearTowerOf(pageLayers) {
    let tower = null;
    for (const l of pageLayers) {
      if (PlaxPanorama.isNearTower(l) && (!tower
        || Math.abs(l.model.parallaxScalingSpeedX) > Math.abs(tower.model.parallaxScalingSpeedX))) tower = l;
    }
    return tower;
  },

  // ParallaxPageReader.towerCameraOf: how far the camera looking at `tower` stands from its axis, in world units.
  cameraOf(tower, worldWidth) {
    return tower.model.towerRadius * (worldWidth / 100) * tower.model.towerCamera;
  },

  // ParallaxPageReader.panoramaRadiusOf: the radius of the panorama `l` is wrapped in round `tower`, world units; 0
  // flat. The tower's front f = D - r away, the layer f s_tower / s, R = that - D, at least D.
  radiusOf(tower, l, worldWidth) {
    if (!tower || tower === l || PlaxTower.isTower(l)) return 0;
    const kind = l.model.kind;
    if (kind !== 'IMAGE' && kind !== 'SHADER' && kind !== 'SEQUENCE') return 0;
    const speed = Math.abs(l.model.parallaxScalingSpeedX);
    const towerSpeed = Math.abs(tower.model.parallaxScalingSpeedX);
    if (!(speed > 0) || !(speed < towerSpeed)) return 0;
    const camera = PlaxPanorama.cameraOf(tower, worldWidth);
    const radius = tower.model.towerRadius * (worldWidth / 100);
    return Math.max(camera, (camera - radius) * towerSpeed / speed - camera);
  },

  // PanoramaBatch.screenOf: where the panorama's point at angle t is drawn, from the axis.
  screenOf(rho, q, t) {
    return rho * Math.sin(t) * (q + 1) / (q + Math.cos(t));
  },

  // PanoramaBatch.scaleAt: how much the panorama is scaled about the eye at angle t: 1 at its middle.
  scaleAt(q, t) {
    return (q + 1) / (q + Math.cos(t));
  },

  // PanoramaBatch.angleAt: the angle of the point drawn x from the axis.
  angleAt(rho, q, x) {
    const c = rho * (q + 1);
    const a = Math.sqrt(c * c + x * x);
    return Math.atan2(x, c) + Math.asin(clamp(x * q / a, -1, 1));
  },

  // PanoramaBatch.set: the strips' edges as arcs from the axis (`edges`, STRIPS + 1) and their scales (`scales`,
  // STRIPS), for a panorama of `rho` at its own scale, the camera `q` of its radius from the axis in the view's middle.
  layOut(rho, q, viewW, edges, scales) {
    const step = viewW / PlaxPanorama.STRIPS;
    for (let i = 0; i <= PlaxPanorama.STRIPS; i++) edges[i] = rho * PlaxPanorama.angleAt(rho, q, i * step - viewW / 2);
    for (let i = 0; i < PlaxPanorama.STRIPS; i++) {
      scales[i] = PlaxPanorama.scaleAt(q, PlaxPanorama.angleAt(rho, q, (i + 0.5) * step - viewW / 2));
    }
  },

  // PanoramaBatch.draw: `src` of `texture` in the box from (left, bottom), drawW by drawH page units (x 0..viewW of the
  // layer's view), flipped as `fx`, `fy` say, cut at the strips, each piece placed round the panorama and scaled about
  // the eye at `eyeY`. What lies outside the view is not drawn.
  draw(bg, texture, src, left, bottom, drawW, drawH, fx, fy, layout, eyeY, viewW) {
    const [rho, q, edges, scales] = layout;
    const STRIPS = PlaxPanorama.STRIPS;
    const step = viewW / STRIPS;
    const axis = viewW / 2;
    const a = left - axis;
    const b = left + drawW - axis;
    if (!(b > a) || !(step > 0) || b <= edges[0] || a >= edges[STRIPS]) return;
    let lo = 0, hi = STRIPS - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (edges[mid + 1] <= a) lo = mid + 1;
      else hi = mid;
    }
    const ppu = bg._ppu;
    for (let s = lo; s < STRIPS && edges[s] < b; s++) {
      const p0 = Math.max(a, edges[s]);
      const p1 = Math.min(b, edges[s + 1]);
      if (!(p1 > p0)) continue;
      // The piece's share of the image, left to right; flipped on X the image runs right to left.
      const t0 = (p0 + axis - left) / drawW, t1 = (p1 + axis - left) / drawW;
      const g0 = fx ? 1 - t1 : t0, g1 = fx ? 1 - t0 : t1;
      const piece = { x: src.x + src.w * g0, y: src.y, w: src.w * (g1 - g0), h: src.h };
      const x0 = p0 === edges[s] ? s * step : axis + PlaxPanorama.screenOf(rho, q, p0 / rho);
      const x1 = p1 === edges[s + 1] ? (s + 1) * step : axis + PlaxPanorama.screenOf(rho, q, p1 / rho);
      const scale = scales[s];
      const pieceY = eyeY + (bottom - eyeY) * scale;
      const pxW = (x1 - x0) * ppu, pxH = drawH * scale * ppu;
      bg._quads.add(texture, x0 * ppu, bg.height - pieceY * ppu - pxH, pxW, pxH, piece, fx, fy);
    }
  },
};

// --- transfer styles (PlaxTransfertStyle: core's TransfertStyle) -----------------------------------------------------

// How a cross-fade into another page is drawn (PlaxBackground.transfertInto's `style`): the copy of
// plax_transfert_style.gd, slotRamp line for line. The game's call, never stored in a page.
//
//   bg.transfertInto(page, atlas, 3, PlaxTransfertStyle.depthStagger(0.5))
//
// FADE (or no style): every layer slot fades at once. DEPTH_STAGGER: each slot over its own window, the back ones
// first. DISSOLVE: in each slot the incoming layer eats the outgoing one in patches (PlaxEffects' dissolved()), over
// the same windows. THROUGH_COLOR: in each slot the outgoing layer goes to a colour, then the incoming one comes out of
// it (PlaxEffects' grade), the gradients too, over the same windows. FOG_CREEP: the whole page sinks into a mist, far
// layers first, the incoming page swapped in at the middle and coming out of it, near layers first (the same grade).
export class PlaxTransfertStyle {
  // TransfertStyle.MAX_STAGGER, MIN_PATCHES, MAX_PATCHES, MIN_SOFTNESS, MAX_SOFTNESS, DISSOLVE_DRIFT.
  static MAX_STAGGER = 2;
  static MIN_PATCHES = 0.5;
  static MAX_PATCHES = 64;
  static MIN_SOFTNESS = 0.01;
  static MAX_SOFTNESS = 0.5;
  static DISSOLVE_DRIFT = 0.15;

  constructor() {
    // 'FADE', 'DEPTH_STAGGER', 'DISSOLVE', 'THROUGH_COLOR' or 'FOG_CREEP', as TransfertStyle.Kind.
    this.kind = 'FADE';
    // How far apart the slots' windows are, 0 to MAX_STAGGER; 0 for FADE.
    this.stagger = 0;
    // A dissolve's patches across the view, and how soft their edges are; 0 for every other style.
    this.patches = 0;
    this.softness = 0;
    // THROUGH_COLOR's colour or FOG_CREEP's mist, [r, g, b, 1]; black for every other style.
    this.color = [0, 0, 0, 1];
  }

  // Every layer slot at once: what a cross-fade with no style does.
  static fade() {
    return new PlaxTransfertStyle();
  }

  // TransfertStyle.depthStagger: the back slot over the first 1 / (1 + stagger) of the fade, the front one over the
  // last; `stagger` clamped to 0..MAX_STAGGER, 0 is fade().
  static depthStagger(value) {
    const style = new PlaxTransfertStyle();
    const s = clamp(value, 0, PlaxTransfertStyle.MAX_STAGGER);
    if (s > 0) {
      style.kind = 'DEPTH_STAGGER';
      style.stagger = s;
    }
    return style;
  }

  // TransfertStyle.dissolve: about `patchCount` patches across the view, their edges `soft` soft (0.01 hard, 0.5 a
  // blur), the slots over depthStagger's windows. Clamped as there.
  static dissolve(patchCount, soft, value) {
    const style = new PlaxTransfertStyle();
    style.kind = 'DISSOLVE';
    style.stagger = clamp(value, 0, PlaxTransfertStyle.MAX_STAGGER);
    style.patches = clamp(patchCount, PlaxTransfertStyle.MIN_PATCHES, PlaxTransfertStyle.MAX_PATCHES);
    style.softness = clamp(soft, PlaxTransfertStyle.MIN_SOFTNESS, PlaxTransfertStyle.MAX_SOFTNESS);
    return style;
  }

  // TransfertStyle.throughColor: each slot's outgoing layer mixed toward `through` ([r, g, b], 0 to 1, an alpha
  // ignored) until it is all that colour halfway through the slot's window, then the incoming one out of it; the slots
  // over depthStagger's windows.
  static throughColor(through, value) {
    const style = new PlaxTransfertStyle();
    style.kind = 'THROUGH_COLOR';
    style.stagger = clamp(value, 0, PlaxTransfertStyle.MAX_STAGGER);
    style.color = [...through.slice(0, 3).map(c => clamp(c, 0, 1)), 1];
    return style;
  }

  // TransfertStyle.fogCreep: the whole page mixed toward `mist` ([r, g, b], 0 to 1, an alpha ignored), the far layers
  // ahead by `value` (clamped to 0..MAX_STAGGER), all mist at the middle, where the incoming page comes in and out of
  // it, the near layers first.
  static fogCreep(mist, value) {
    const style = new PlaxTransfertStyle();
    style.kind = 'FOG_CREEP';
    style.stagger = clamp(value, 0, PlaxTransfertStyle.MAX_STAGGER);
    style.color = [...mist.slice(0, 3).map(c => clamp(c, 0, 1)), 1];
    return style;
  }

  // TransfertStyle.gradeOf: how much of the colour a layer of a slot at `ramp` is mixed toward, 0 to 1, peaking at 1
  // halfway.
  static gradeOf(ramp) {
    return ramp < 0.5 ? 2 * ramp : 2 - 2 * ramp;
  }

  // TransfertStyle.incomingShows: whether a slot at `ramp` draws its incoming layer rather than its outgoing one.
  static incomingShows(ramp) {
    return ramp >= 0.5;
  }

  // TransfertStyle.grades: whether the style mixes its layers toward its colour, THROUGH_COLOR and FOG_CREEP.
  grades() {
    return this.kind === 'THROUGH_COLOR' || this.kind === 'FOG_CREEP';
  }

  // TransfertStyle.showsIncoming: whether slot `slot` of `total` draws its incoming layer at `progress`; THROUGH_COLOR
  // halfway through the slot's window, FOG_CREEP every slot halfway through the fade.
  showsIncoming(progress, slot, total) {
    return this.kind === 'FOG_CREEP' ? progress >= 0.5
      : PlaxTransfertStyle.incomingShows(this.slotRamp(progress, slot, total));
  }

  // TransfertStyle.gradeAt: how much of the colour slot `slot` of `total` is mixed toward at `progress`. FOG_CREEP: the
  // slot's ramp over 2 progress going in, 2 - 2 progress coming out, smoothstepped.
  gradeAt(progress, slot, total) {
    if (this.kind !== 'FOG_CREEP') return PlaxTransfertStyle.gradeOf(this.slotRamp(progress, slot, total));
    const r = this.slotRamp(progress < 0.5 ? 2 * progress : 2 - 2 * progress, slot, total);
    return r * r * (3 - 2 * r);
  }

  // TransfertStyle.gradient: a gradient's colour `progress` into a fade from `from` to `to`; THROUGH_COLOR's and
  // FOG_CREEP's go through their colour at the back slot's pace.
  gradient(from, to, progress) {
    if (!this.grades()) return lerp(from, to, progress);
    return lerp(this.showsIncoming(progress, 0, 1) ? to : from, this.color, this.gradeAt(progress, 0, 1));
  }

  // TransfertStyle.cellsUp: the dissolve's noise cells up a view `viewW` by `viewH`, stretched 2.5 times.
  cellsUp(viewW, viewH) {
    return viewW > 0 ? this.patches * viewH / viewW * 2.5 : 0;
  }

  // TransfertStyle.drift: how far the dissolve's noise has drifted on x at `seconds` of the clock, in cells, wrapped at
  // 8.
  static drift(seconds) {
    const d = (seconds * PlaxTransfertStyle.DISSOLVE_DRIFT) % 8;
    return d < 0 ? d + 8 : d;
  }

  // TransfertStyle.slotRamp: how far along slot `slot` of `total` (0 at the back) is, 0 to 1, when the fade is at
  // `progress`: the incoming layer's opacity, the outgoing one's 1 minus it.
  slotRamp(progress, slot, total) {
    const k = total > 1 ? slot / (total - 1) : 0;
    return clamp(progress * (1 + this.stagger) - this.stagger * k, 0, 1);
  }
}

// --- detail levels (PlaxDetail: ParallaxPageReader.setDetail, r464) ---------------------------------------------------

// Detail levels, for a PlaxBackground (ParallaxPageReader.setDetail, Enum_Detail, LayerDetail; plax_detail.gd): how much
// of a page a game draws, a player's choice of quality, never stored in a page. At each level a layer is drawn, drawn
// plain or not drawn as its rule says: from which level it is shown, its SHADER effect drawn (below: its image), its
// glint drawn, a MIRROR layer's reflection drawn (below: through SWELL, in SWELL's sun). Its state is on the layer:
// shown, drawnEffect ('' for none) and drawnGlint, set when the page is built and when the game changes a level:
//
//   bg.detail = PlaxDetail.Level.MEDIUM
//   bg.setLayerDetail('birds', { shown: PlaxDetail.Level.HIGH })   // a flock at HIGH only
//   bg.setLayerDetail('sea', { mirror: PlaxDetail.Level.MEDIUM })  // a sea that keeps its reflection at MEDIUM
export const PlaxDetail = {
  // Enum_Detail, lowest first.
  Level: { OFF: 0, MEDIUM: 1, HIGH: 2 },
  // LayerDetail.DEFAULT: shown at every level, its effect and glint from MEDIUM, a MIRROR's reflection at HIGH only.
  DEFAULT: { shown: 0, effect: 1, glint: 1, mirror: 2 },

  // A layer's rule: DEFAULT with `over`'s keys (shown, effect, glint, mirror) in their place.
  rule(over) {
    return { ...PlaxDetail.DEFAULT, ...over };
  },

  // The rule `bg` draws layer `l` by: its name's (PlaxBackground.setLayerDetail), else every layer's.
  ruleOf(bg, l) {
    return bg._details.get(l.model.name) ?? bg._defaultDetail;
  },

  // Sets what `bg.detail` draws of each of its layers, both pages of a cross-fade (ParallaxPageReader.setDetail).
  applyAll(bg) {
    for (const l of [...bg.layers, ...bg.transferLayers]) PlaxDetail.apply(l, bg.detail, PlaxDetail.ruleOf(bg, l));
  },

  // Sets what `level` draws of layer `l` under rule `r` (ParallaxPageReader.drawLayer, ParallaxLayer.getDrawnEffect,
  // drawsGlint): shown or not (it scrolls on), and a SHADER layer's quads drawn through the effect it is drawn through,
  // or for none PLAIN when it is fogged.
  apply(l, level, r) {
    const m = l.model;
    l.shown = level >= r.shown;
    let effect = '';
    if (m.kind === 'SHADER' && level >= r.effect) {
      effect = m.shaderEffect === 'MIRROR' && level < r.mirror ? 'SWELL' : m.shaderEffect;
    }
    l.drawnGlint = effect !== '' && level >= r.glint;
    const before = l.drawnEffect ?? (m.kind === 'SHADER' ? m.shaderEffect : '');
    l.drawnEffect = effect;
    if (m.kind !== 'SHADER' || before === effect) return;
    l.dissolvePlain = false;
    l.quads.setEffect(effect || (l.haze > 0 ? 'PLAIN' : null));
    // Drawn plain, its patches go with its tiles, as an IMAGE layer's (ParallaxPageReader.drawImageLayer; update()).
    if (l.animQuads && effect) l.animQuads.setEffect(effect);
  },
};

// --- SHADER layers and the page fog (PlaxEffects: core's GdxLayerEffects) ----------------------------------------------

// The SHADER layers' effects (Enum_ShaderEffect), as GLSL: plax_effects.gd's shaders line for line, which copy core's
// GdxLayerEffects, the same numbers (uniforms), the same picture. Change one, change core/src/.../GdxLayerEffects.java,
// plax_effects.gd and engines/jme's ParallaxEffect.frag, and run engines/threejs/tools/frame-check.sh
// engines/rounds/shaders.
//
// Each effect works on the image's own coordinates, taken from UV: x and y in page units from the image's bottom-left,
// as it is drawn. region: the region's u, v, u2, v2 (v at the image's top); size: the drawn image's width and height in
// page units; effect: amplitude, wavelength, phase; haze: the depth fog at the layer, 0 to 1, which hazed() mixes the
// colour toward fog_color by, then toward grade's rgb by its a (a transfert through a colour), its alpha times
// dissolved() (a transfert's dissolve: side 0 none, 1 outgoing, 2 incoming; ramp, softness, drift; cells across and up
// the view). across: the view's width in page units, where the layer's noise starts from its left (effectStartX), 1 or
// -1 (flipped on X); along() reads it at SCREEN_UV: where the fragment is along the layer, local() carried on across
// its tiles, so FOG has no seam at a tile edge (r229); up: the same up the view, from its bottom (effectStartY), -1
// flipped on Y (r262), which WAVE's ripple reads too (r273). SCREEN_UV is Godot's: 0 to 1 across and down the view,
// from the quad's own screen pixels (vScreen), so a game's pixel ratio or viewport does not move it. "PLAIN" is no
// effect: an IMAGE or SEQUENCE layer the page's depth fog reaches. No colour management: the shaders write what they
// compute, blended as every layer is (LAYER_BLENDING).
const EFFECT_VERTEX = `varying vec2 vUv;
varying vec2 vScreen;
varying vec3 vShade;
void main() {
	vUv = uv;
	vScreen = position.xy;
	vShade = color;
	gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const EFFECT_HEAD = `#define TAU 6.283185307179586
uniform sampler2D tex;
uniform vec4 tint;
uniform vec2 view;
uniform vec4 region;
uniform vec2 size;
uniform vec3 effect;
uniform float haze;
uniform vec3 fog_color;
uniform vec4 dissolve;
uniform vec2 cells;
uniform vec4 grade;
uniform vec3 across;
uniform vec3 up;
uniform vec2 wrap;
uniform vec4 glint;
uniform vec2 sun;
uniform vec4 glint_a;
uniform vec4 glint_b;
varying vec2 vUv;
varying vec2 vScreen;
// Quads.shade: what a tower's piece darkens the tint by (PlaxTower.draw); 1 for every other quad.
varying vec3 vShade;
vec2 local(vec2 uv) {
	return vec2((uv.x - region.x) / (region.z - region.x) * size.x, (region.w - uv.y) / (region.w - region.y) * size.y);
}
vec2 along(vec2 screen_uv) {
	return vec2(across.z * (screen_uv.x * across.x - across.y), up.z * ((1.0 - screen_uv.y) * up.x - up.y));
}
float dissolved(vec2 screen_uv) {
	if (dissolve.x < 0.5)
		return 1.0;
	vec2 p = vec2(screen_uv.x, 1.0 - screen_uv.y) * cells + vec2(dissolve.w, 0.0);
	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
	float e = dissolve.z;
	float m = smoothstep(n - e, n + e, dissolve.y * (1.0 + 2.0 * e) - e);
	return dissolve.x < 1.5 ? 1.0 - m : m;
}
vec4 hazed(vec4 color, vec2 screen_uv) {
	return vec4(mix(mix(color.rgb, fog_color, haze), grade.rgb, grade.a), color.a * dissolved(screen_uv));
}
vec4 glinted(vec4 color, vec4 image, vec2 uv, vec2 screen_uv) {
	if (glint.x <= 0.0)
		return color;
	const vec3 LUMA = vec3(0.299, 0.587, 0.114);
	float dv = glint.w / size.y * (region.w - region.y);
	float lo = min(region.y, region.w), hi = max(region.y, region.w);
	vec4 above = texture2D(tex, vec2(uv.x, clamp(uv.y - dv, lo, hi)));
	vec4 below = texture2D(tex, vec2(uv.x, clamp(uv.y + dv, lo, hi)));
	float ridge = dot(image.rgb, LUMA) - 0.5 * (dot(above.rgb, LUMA) + dot(below.rgb, LUMA));
	float crest = smoothstep(0.005, 0.035, ridge) * min(above.a, below.a) * image.a;
	float sun_at = (screen_uv.x - sun.x) / sun.y;
	vec2 q = along(screen_uv) / glint_b.zw;
	float n = (cos(TAU * (glint_a.x * q.x + glint_a.y * q.y))
		+ 0.6 * cos(TAU * (glint_a.z * q.x + glint_a.w * q.y + 0.3))) / 1.6;
	float pulse = 0.5 + 0.5 * cos(TAU * (glint.z + 1.5 * sin(TAU * (glint_b.x * q.x + glint_b.y * q.y))));
	float glow = smoothstep(1.0 - glint.y, 1.0, n) * pulse * pulse * crest * exp(-sun_at * sun_at);
	return vec4(min(color.rgb + glint.x * glow * vec3(1.0, 0.96, 0.85), 1.0), color.a);
}
`;

const EFFECT_FRAGMENTS = {
  // WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region; y is
  // along().y, running on up across the tiles (r273).
  WAVE: `void main() {
	vec2 SCREEN_UV = vScreen / view;
	float shift = effect.x * sin(TAU * (along(SCREEN_UV).y - effect.z) / effect.y);
	float u = clamp(vUv.x + shift / size.x * (region.z - region.x), min(region.x, region.z), max(region.x, region.z));
	vec2 uv = vec2(u, vUv.y);
	vec4 image = texture2D(tex, uv);
	gl_FragColor = hazed(glinted(vec4(tint.rgb * vShade, tint.a) * image, image, uv, SCREEN_UV), SCREEN_UV);
}
`,
  // FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines whose x frequencies are 7, 17 and 23 per
  // 8 wavelengths (FOG_PERIOD).
  FOG: `void main() {
	vec2 SCREEN_UV = vScreen / view;
	vec2 p = (along(SCREEN_UV) + vec2(effect.z, 0.0)) / effect.y;
	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
	vec4 image = texture2D(tex, vUv);
	vec4 color = vec4(tint.rgb * vShade, tint.a) * image;
	color.a *= 1.0 - effect.x * n;
	gl_FragColor = hazed(glinted(color, image, vUv, SCREEN_UV), SCREEN_UV);
}
`,
  // SWELL (r431): each pixel read from where two crossing travelling waves move it, up to amplitude sideways and half
  // that up and down, the image wrapped on the axes its page repeats on (wrap: 1 wraps, 0 holds the edge rows, r434)
  // and held on the others; whole turns over FOG_PERIOD wavelengths across and FOG_PERIOD_Y up.
  SWELL: `void main() {
	vec2 SCREEN_UV = vScreen / view;
	vec2 a = along(SCREEN_UV);
	float L = effect.y;
	float t = effect.z / L;
	float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);
	float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);
	vec2 l = local(vUv) + effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));
	vec2 f = mix(clamp(l / size, 0.0, 1.0), fract(l / size), wrap);
	vec2 uv = vec2(mix(region.x, region.z, f.x), mix(region.w, region.y, f.y));
	vec4 image = texture2D(tex, uv);
	gl_FragColor = hazed(glinted(vec4(tint.rgb * vShade, tint.a) * image, image, uv, SCREEN_UV), SCREEN_UV);
}
`,
  // MIRROR (r455): SWELL, then the water a mirror of the page's sky (GdxLayerEffects.MIRROR): a facet, tilted by the
  // swell's slope, three finer ripples and the painting's relief, bends the painting's own colour by how much more
  // or less of the sky it mirrors than flat water there does. mirror: strength, relief, shine, tint; mirror_at: the
  // horizon's height in local() and the depth; sky, zenith: the page's top gradient, its bottom and top.
  MIRROR: `uniform vec4 mirror;
uniform vec2 mirror_at;
uniform vec3 sky;
uniform vec3 zenith;
vec2 wrapped(vec2 l) {
	vec2 f = mix(clamp(l / size, 0.0, 1.0), fract(l / size), wrap);
	return vec2(mix(region.x, region.z, f.x), mix(region.w, region.y, f.y));
}
vec3 mirror_of(float s) {
	float hard = 0.5 - 0.4 * mirror.z;
	vec3 mirrored = mix(sky, zenith, smoothstep(0.5 - hard, 0.5 + hard, s));
	mirrored = mix(mirrored, vec3(1.0), mirror.z * (1.0 - smoothstep(-0.3, 0.1, s)));
	return mirrored * (1.0 - 0.5 * mirror.z * smoothstep(0.9, 1.4, s));
}
void main() {
	vec2 SCREEN_UV = vScreen / view;
	vec2 a = along(SCREEN_UV);
	float L = effect.y;
	float t = effect.z / L;
	float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);
	float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);
	vec2 l = local(vUv) + effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));
	vec2 uv = wrapped(l);
	vec4 image = texture2D(tex, uv);
	vec4 color = vec4(tint.rgb * vShade, tint.a) * image;
	vec2 mq = vec2(a.x / (8.0 * L), a.y / (10.0 * L));
	float tilt = TAU / L * effect.x * (0.18 * cos(p1) - 0.18 * cos(p2))
		+ effect.x * (0.9 * sin(TAU * (13.0 * mq.x + 41.0 * mq.y - 3.0 * t))
		+ 0.7 * sin(TAU * (-7.0 * mq.x + 57.0 * mq.y - 4.0 * t))
		+ 0.5 * sin(TAU * (21.0 * mq.x + 83.0 * mq.y - 5.0 * t + 0.5 * sin(TAU * (5.0 * mq.x - 9.0 * mq.y + t)))));
	const vec3 GREY = vec3(0.299, 0.587, 0.114);
	const float D = 0.05;
	float relief = dot(texture2D(tex, wrapped(l + vec2(0.0, D))).rgb
		- texture2D(tex, wrapped(l - vec2(0.0, D))).rgb, GREY) / (2.0 * D);
	float facet = tilt + 0.5 * mirror.y * relief;
	float level = (mirror_at.x - local(vUv).y) / mirror_at.y;
	vec3 mirrored = mirror_of(level + 1.2 * facet);
	vec3 still = mirror_of(level);
	vec3 bend = mirrored / max(still, 0.05);
	bend = mix(bend, vec3(dot(mirrored, GREY) / max(dot(still, GREY), 0.05)), mirror.w);
	color.rgb = mix(color.rgb, min(color.rgb * bend, 1.0), mirror.x);
	gl_FragColor = hazed(glinted(color, image, uv, SCREEN_UV), SCREEN_UV);
}
`,
  // PLAIN: no effect, the depth fog only.
  PLAIN: `void main() {
	vec2 SCREEN_UV = vScreen / view;
	gl_FragColor = hazed(vec4(tint.rgb * vShade, tint.a) * texture2D(tex, vUv), SCREEN_UV);
}
`,
};

export const PlaxEffects = {
  // Enum_ShaderEffect.FOG_PERIOD: how many wavelengths FOG's patches run before they repeat, SWELL's waves too.
  FOG_PERIOD: 8,
  // Enum_ShaderEffect.FOG_PERIOD_Y: how many wavelengths they run up before they repeat (r262).
  FOG_PERIOD_Y: 10,
  // GdxLayerEffects' GLINT_*: what a page does not store of the glint, as the animation lab drew it (r408).
  GLINT_SIZE: 0.8,
  GLINT_CREST: 0.2,
  GLINT_DENSITY: 0.6,
  GLINT_TWINKLE: 0.3,
  GLINT_SUN_X: 0.5,
  GLINT_SUN_WIDTH: 0.3,
  // GdxLayerEffects.MIRROR_SUN_*: a MIRROR layer's glint's sun (r455).
  MIRROR_SUN_X: 0.25,
  MIRROR_SUN_WIDTH: 0.45,
  // GdxLayerEffects.MIRROR_*: MIRROR's strength, relief, shine and tint, which a page does not store (r455).
  MIRROR_NUMBERS: [0.15, 3.0, 1.0, 1.0],
  GLINT_ACROSS: [1.0, 1.37, 0.53],
  GLINT_UP: [1.0, -0.71, 1.3],

  // A layer's uniforms, at what the Godot shaders default them to; every run of the layer shares them.
  uniforms() {
    return {
      tint: { value: new THREE.Vector4(1, 1, 1, 1) }, view: { value: new THREE.Vector2(1280, 720) },
      region: { value: new THREE.Vector4(0, 0, 1, 1) }, size: { value: new THREE.Vector2(1, 1) },
      effect: { value: new THREE.Vector3(0, 1, 0) }, haze: { value: 0 },
      fog_color: { value: new THREE.Vector3(0.93, 0.95, 0.97) }, dissolve: { value: new THREE.Vector4(0, 0, 0, 0) },
      cells: { value: new THREE.Vector2() }, grade: { value: new THREE.Vector4(0, 0, 0, 0) },
      across: { value: new THREE.Vector3(0, 0, 1) }, up: { value: new THREE.Vector3(0, 0, 1) },
      wrap: { value: new THREE.Vector2(1, 0) },
      glint: { value: new THREE.Vector4(0, 0, 0, 0) }, sun: { value: new THREE.Vector2(0.5, 0.3) },
      glint_a: { value: new THREE.Vector4(0, 0, 0, 0) }, glint_b: { value: new THREE.Vector4(0, 0, 1, 1) },
      mirror: { value: new THREE.Vector4(0, 0, 0, 0) }, mirror_at: { value: new THREE.Vector2(1, 1) },
      sky: { value: new THREE.Vector3(1, 1, 1) }, zenith: { value: new THREE.Vector3(1, 1, 1) },
    };
  },

  // A material for one run of a layer drawn through `effect` ('PLAIN' for a fogged one), its texture its own, its
  // other numbers `uniforms`, the layer's.
  material(effect, uniforms) {
    return new THREE.ShaderMaterial({
      ...LAYER_BLENDING, vertexColors: true, uniforms: { ...uniforms, tex: { value: null } },
      vertexShader: EFFECT_VERTEX, fragmentShader: EFFECT_HEAD + (EFFECT_FRAGMENTS[effect] ?? EFFECT_FRAGMENTS.WAVE),
    });
  },

  // ParallaxLayer.getShaderPhase: speed * seconds wrapped to where the effect repeats (one wavelength, FOG_PERIOD for
  // FOG and SWELL), never negative; 0 without a wavelength.
  phase(model, seconds) {
    const wavelength = model.shaderWavelength;
    if (!(wavelength > 0)) return 0;
    const period = wavelength * PlaxEffects._across(model);
    const p = (seconds * model.shaderSpeed) % period;
    return p < 0 ? p + period : p;
  },

  // ParallaxLayer.getShaderPeriod: where the effect repeats, one wavelength, FOG_PERIOD for FOG and SWELL, at least
  // FOG's for a glinting layer; 0 without a wavelength.
  period(model) {
    const wavelength = model.shaderWavelength ?? 0;
    if (!(wavelength > 0)) return 0;
    return wavelength * PlaxEffects._across(model);
  },

  // ParallaxLayer.glints: a SHADER layer with a positive glint strength and wavelength.
  glints(model) {
    return model.kind === 'SHADER' && (model.glintStrength ?? 0) > 0 && (model.shaderWavelength ?? 0) > 0;
  },

  // ParallaxLayer.periodAcross: the wavelengths the effect runs across before it repeats, FOG's with it when glinting.
  _across(model) {
    const own = ['FOG', 'SWELL', 'MIRROR'].includes(model.shaderEffect) ? PlaxEffects.FOG_PERIOD : 1;
    return PlaxEffects.glints(model) ? PlaxEffects._leastMultiple(own, PlaxEffects.FOG_PERIOD) : own;
  },

  // ParallaxLayer.periodUp: the same up.
  _up(model) {
    const own = ['FOG', 'SWELL', 'MIRROR'].includes(model.shaderEffect) ? PlaxEffects.FOG_PERIOD_Y : 1;
    return PlaxEffects.glints(model) ? PlaxEffects._leastMultiple(own, PlaxEffects.FOG_PERIOD_Y) : own;
  },

  _leastMultiple(a, b) {
    let x = a, y = b;
    while (y !== 0) [x, y] = [y, x % y];
    return a / x * b;
  },

  // ParallaxLayer.getEffectStartX: where FOG's noise starts on X, in page units from the view's left, wrapped to the
  // period: the image's left edge in the tile at distanceX (its right edge flipped on X), carried back by what
  // _actLayer wrapped.
  effectStartX(l) {
    const trim = l.model.flipX ? 1 - l.trimLeft : l.trimLeft;
    const start = l.distanceX + l.wrappedX + l.width * trim;
    const p = PlaxEffects.period(l.model);
    return p > 0 ? start % p : start;
  },

  // ParallaxLayer.getShaderPeriodY: where the effect repeats up, one wavelength, FOG_PERIOD_Y for FOG and SWELL, at
  // least FOG's for a glinting layer; 0 without one.
  periodY(model) {
    const wavelength = model.shaderWavelength ?? 0;
    if (!(wavelength > 0)) return 0;
    return wavelength * PlaxEffects._up(model);
  },

  // ParallaxLayer.getEffectStartY: where FOG's noise starts on Y, in page units from the view's bottom, wrapped to
  // periodY: the image's bottom edge in the tile at distanceY (its top edge flipped on Y), carried back by what
  // _actLayer wrapped (r262).
  effectStartY(l) {
    const trim = l.model.flipY ? 1 - l.trimBottom : l.trimBottom;
    const start = l.distanceY + l.wrappedY + l.height * trim;
    const p = PlaxEffects.periodY(l.model);
    return p > 0 ? start % p : start;
  },

  // GdxLayerEffects.uniforms: the numbers of a SHADER layer `l` of PlaxBackground, set on its `uniforms`; `viewW` and
  // `viewH` the view's size in page units; `wrap` 1 on an axis the page repeats on, 0 on one it does not (SWELL's,
  // GdxLayerEffects.setRepeat, r434).
  apply(u, l, seconds, viewW, viewH, left = 0, floorY = 0, wrap = [1, 0]) {
    const image = l.region.page.texture.image, model = l.model, src = l.src;
    // The effect its detail level draws (PlaxDetail): SWELL for a MIRROR without its reflection.
    const effect = l.drawnEffect ?? model.shaderEffect;
    u.region.value.set(src.x / image.width, src.y / image.height, (src.x + src.w) / image.width,
      (src.y + src.h) / image.height);
    u.size.value.set(l.width * l.packedW, l.height * l.packedH);
    const on = model.shaderWavelength > 0;
    let amplitude = model.shaderAmplitude;
    if (effect === 'FOG') amplitude = Math.min(1, Math.max(0, amplitude));
    u.effect.value.set(on ? amplitude : 0, on ? model.shaderWavelength : 1, on ? PlaxEffects.phase(model, seconds) : 0);
    u.across.value.set(viewW, left + PlaxEffects.effectStartX(l), model.flipX ? -1 : 1);
    u.up.value.set(viewH, floorY + PlaxEffects.effectStartY(l), model.flipY ? -1 : 1);
    u.wrap.value.set(wrap[0], wrap[1]);
    PlaxEffects._applyGlint(u, l, seconds);
    if (effect === 'MIRROR') PlaxEffects._applyMirror(u, l);
  },

  // GdxLayerEffects.mirrorUniforms: MIRROR's numbers, the horizon's height in local() and the depth, the page's sky.
  _applyMirror(u, l) {
    u.mirror.value.set(...PlaxEffects.MIRROR_NUMBERS);
    u.mirror_at.value.set((l.model.mirrorHorizon - l.trimBottom) * l.height, Math.max(0.01, l.model.mirrorDepth));
    u.sky.value.set(...l.sky);
    u.zenith.value.set(...l.zenith);
  },

  // GdxLayerEffects.glintUniforms: the glint's numbers, its strength 0 for a layer that does not glint, or whose detail
  // level draws none (PlaxDetail: ParallaxLayer.drawsGlint); each wave a whole number of turns over the effect's period.
  _applyGlint(u, l, seconds) {
    const model = l.model;
    const on = PlaxEffects.glints(model) && l.drawnGlint !== false;
    const periodX = on ? PlaxEffects.period(model) : 1;
    const periodUp = on ? PlaxEffects.periodY(model) : 1;
    u.glint.value.set(on ? model.glintStrength : 0, PlaxEffects.GLINT_DENSITY,
      (seconds * PlaxEffects.GLINT_TWINKLE) % 1000, PlaxEffects.GLINT_CREST);
    // A MIRROR drawn through SWELL takes SWELL's sun (Simon on r464).
    const mirrored = (l.drawnEffect ?? model.shaderEffect) === 'MIRROR';
    u.sun.value.set(mirrored ? PlaxEffects.MIRROR_SUN_X : PlaxEffects.GLINT_SUN_X,
      mirrored ? PlaxEffects.MIRROR_SUN_WIDTH : PlaxEffects.GLINT_SUN_WIDTH);
    const across = periodX / (2.5 * PlaxEffects.GLINT_SIZE);
    const upTurns = periodUp / 0.9;
    const t = [];
    for (let k = 0; k < 3; k++) {
      t.push(Math.max(1, Math.floor(PlaxEffects.GLINT_ACROSS[k] * across + 0.5)));
      t.push(Math.floor(PlaxEffects.GLINT_UP[k] * upTurns + 0.5));
    }
    u.glint_a.value.set(t[0], t[1], t[2], t[3]);
    u.glint_b.value.set(t[4], t[5], periodX, periodUp);
  },

  // ParallaxPageReader.fogOf: how much of the fog's colour layer `model` is mixed toward, 0 to 1:
  // 1 - exp(-strength * (1 / speed - 1 / front)), speed its speed ratio X and front the page's fastest (frontSpeedOf),
  // as magnitudes. 0 for the front layer, for no fog and for a page that does not scroll; 1 for a still layer.
  fogOf(strength, front, model) {
    if (!(strength > 0) || !(front > 0)) return 0;
    const speed = Math.abs(model.parallaxScalingSpeedX);
    if (!(speed > 0)) return 1;
    const depth = 1 / speed - 1 / front;
    return depth > 0 ? 1 - Math.exp(-strength * depth) : 0;
  },

  // ParallaxPageReader.dollyZoom: the zoom a dollied layer is drawn at, the camera's being `zoom` and its page's front
  // `front`: 1 - k (1 - zoom), k its speed ratio X over the front's, as magnitudes; the camera's for a still page.
  // In floats, as libGDX reckons it.
  dollyZoom(zoom, front, model) {
    if (!(front > 0)) return zoom;
    const k = Math.min(1, f32p(f32p(Math.abs(model.parallaxScalingSpeedX)) / f32p(front)));
    return f32p(1 - f32p(k * f32p(1 - zoom)));
  },

  // ParallaxPageReader.frontSpeedOf: the fastest speed ratio X of `page` (PlaxBackground's layers), as a magnitude.
  frontSpeedOf(page) {
    let front = 0;
    for (const l of page) front = Math.max(front, Math.abs(l.model.parallaxScalingSpeedX));
    return front;
  },
};

// --- depth (PlaxDepth: core's ParallaxDepth) --------------------------------------------------------------------------

export const PlaxDepth = {
  // ParallaxDepth.distanceOf: how far layer `model` is, in front distances, its page's fastest speed ratio X being
  // `front` (PlaxEffects.frontSpeedOf): front / speed, as magnitudes. The front layer is at 1, a still one infinitely far;
  // a page that does not scroll is one picture, every layer at 1.
  distanceOf(front, model) {
    if (!(front > 0)) return 1;
    const speed = Math.abs(model.parallaxScalingSpeedX);
    return speed > 0 ? front / speed : Infinity;
  },
};

// --- rings (PlaxRings: core's ParallaxRings, and their drawing) -------------------------------------------------------

// A page as the sky of a 3D world (parallax-readers r48, docs/page-as-3d-sky.md): each layer that draws an image is an
// open cylinder round the world's centre, its radius from its speed, so a camera flying through the world sees the
// layers slide by perspective alone; the page's gradients are a sphere round the camera. Built from a PlaxBackground's
// state (its layers, scroll, cross-fades and tint), never a second loader:
//
//   const rings = new PlaxRings(bg, { front: 330, foot: 50 });   // or far: 760, lift: -2, scale: 0.7, as the saga
//   scene.add(rings.group);
//   renderer.setAnimationLoop(() => { rings.update(1 / 60); renderer.render(scene, camera); });
//
// The statics are core's ParallaxRings line for line, the numbers only (engines/rounds/rings checks them against
// core's: tools/rings.html, frame-check.sh); the instance draws them. Its layers' colours are libGDX's as the flat
// reader's are: raw ShaderMaterials (no colour management, no tone mapping), LAYER_BLENDING, the tint packed.
export class PlaxRings {
  // ParallaxRings.MAX_ELEVATION: no band reaches the zenith, a cylinder there would be infinitely high.
  static MAX_ELEVATION = Math.fround(89 * Math.PI / 180);
  // ParallaxLayer.drawsImage: the kinds a ring is drawn for; EMPTY and PARTICLES have none.
  static DRAWS_IMAGE = ['IMAGE', 'SHADER', 'SEQUENCE', 'FLOOR', 'LURKER'];

  // ParallaxRings.Options, what the 3D scene chooses (the page itself stores none of it), over `o`: front, the front
  // band's radius in world units; far, when more than 0 the slowest moving band's radius, the depths compressed into
  // front..far (radiusOf), 0 the library's depths; foot, the height of the eye the page is seen from, its horizon; lift,
  // the page raised (lowered when negative) by this, in page units, before scale; scale, the page squeezed (or
  // stretched) about its horizon; pageDegrees, how many degrees of sky the page's height spans at scale 1;
  // fogStrength, the page fog's strength (PlaxEffects.fogOf).
  static options(o = {}) {
    return { front: 100, far: 0, foot: 0, lift: 0, scale: 1, pageDegrees: 45, fogStrength: 0, ...o };
  }

  // ParallaxRings.pageHeightOf: the page's height in its units, as a ring sees it: the world's at 16:9.
  static pageHeightOf(worldWidth) {
    return worldWidth * 9 / 16;
  }

  // ParallaxRings.elevationOf: the elevation of `pageY` (page units up from its bottom edge), in radians, kept under
  // the zenith.
  static elevationOf(pageY, worldWidth, options) {
    const perUnit = options.pageDegrees * Math.PI / 180 / PlaxRings.pageHeightOf(worldWidth);
    return Math.min(PlaxRings.MAX_ELEVATION, (pageY + options.lift) * perUnit * options.scale);
  }

  // ParallaxRings.radiusOf: the radius of layer `l`'s ring (PlaxBackground's layer state), the page's speeds running
  // from `slowest` (the slowest that moves) to `front`, as magnitudes. The library's depth: options.front * front /
  // speed. With options.far: options.front * (front / speed)^p, p = ln(far / options.front) / ln(front / slowest), which
  // puts the slowest at far (deux-generaux lib/saga/sky.js's law). Still: infinite.
  static radiusOf(front, slowest, l, options) {
    const speed = Math.abs(l.model.parallaxScalingSpeedX);
    if (!(speed > 0)) return Infinity;
    const depth = PlaxDepth.distanceOf(front, l.model);
    if (options.far > 0 && slowest > 0 && slowest < front) {
      const p = Math.log(options.far / options.front) / Math.log(front / slowest);
      return options.front * Math.pow(depth, p);
    }
    return options.front * depth;
  }

  // ParallaxRings.bands: the rings of `page`'s layers (PlaxBackground's layers) that draw an image, in page order. Each:
  // layer (its index in `page`), radius (Infinity for a still layer, drawn on the sky round the camera), bottom and top
  // (the elevations of its edges, radians, seen from the centre at the foot), y and height (its bottom edge's height in
  // the world and its own, a finite ring's only), repeat (how often its tile goes round, at least once), imageShare (the
  // share of a tile the image fills, the rest padX), order (0 first: far first, as the rings share a centre and no
  // depth sort can order them) and fog (PlaxEffects.fogOf).
  static bands(page, worldWidth, options) {
    const front = PlaxEffects.frontSpeedOf(page);
    let slowest = front;
    for (const l of page) {
      const speed = Math.abs(l.model.parallaxScalingSpeedX);
      if (speed > 0 && speed < slowest) slowest = speed;
    }
    const pageHeight = PlaxRings.pageHeightOf(worldWidth);
    const bands = [];
    page.forEach((l, i) => {
      if (!PlaxRings.DRAWS_IMAGE.includes(l.model.kind)) return;
      const band = { layer: i, radius: PlaxRings.radiusOf(front, slowest, l, options), bottom: 0, top: 0, y: 0, height: 0,
        repeat: 1, imageShare: 1, order: 0, fog: 0 };
      // ParallaxLayer.getDecalPercentY, getRegionHeight, getTotalWidth and getWidth: a SEQUENCE layer's width is its
      // cycle's without the padX after the last slot (_sizeLayer), its tile with it.
      const foot = l.model.decal_Y_Ratio * pageHeight / 100, high = l.height;
      band.bottom = PlaxRings.elevationOf(foot, worldWidth, options);
      band.top = PlaxRings.elevationOf(foot + high, worldWidth, options);
      const tile = l.width + l.model.padX;
      band.imageShare = tile > 0 ? l.width / tile : 1;
      if (band.radius !== Infinity) {
        band.y = options.foot + band.radius * Math.tan(band.bottom);
        band.height = band.radius * (Math.tan(band.top) - Math.tan(band.bottom));
        // a tile as wide, for the band's height, as the image is for the layer's: the painting keeps its aspect
        if (band.height > 0 && high > 0) {
          band.repeat = Math.max(1, Math.round(2 * Math.PI * band.radius / (tile * band.height / high)));
        }
      } else if (band.top > band.bottom && high > 0) {
        band.repeat = Math.max(1, Math.round(2 * Math.PI / (tile * (band.top - band.bottom) / high)));
      }
      band.fog = PlaxEffects.fogOf(options.fogStrength, front, l.model);
      bands.push(band);
    });
    // far first; among equals, page order (back to front)
    const sorted = [...bands];
    for (let i = 1; i < sorted.length; i++) {
      for (let j = i; j > 0 && sorted[j].radius > sorted[j - 1].radius; j--) {
        [sorted[j], sorted[j - 1]] = [sorted[j - 1], sorted[j]];
      }
    }
    sorted.forEach((band, i) => { band.order = i; });
    return bands;
  }

  // ParallaxRings.gradient: the page's gradients by elevation, in radians, each over the part of the page's height the
  // 2D heart gives it (topHalfSize and bottomHalfSize, the part each leaves uncovered): the top one from topFrom (its
  // bottom colour) to topTo (its top colour), the bottom one from bottomFrom (its bottom colour) to bottomTo (its top
  // colour), drawn over the top one where they meet.
  static gradient(topHalfSize, bottomHalfSize, worldWidth, options) {
    const h = PlaxRings.pageHeightOf(worldWidth);
    return {
      topFrom: PlaxRings.elevationOf(h * topHalfSize, worldWidth, options),
      topTo: PlaxRings.elevationOf(h, worldWidth, options),
      bottomFrom: PlaxRings.elevationOf(0, worldWidth, options),
      bottomTo: PlaxRings.elevationOf(h * (1 - bottomHalfSize), worldWidth, options),
    };
  }

  // The rings of `background`'s page, drawn: add `group` to the scene, call update() every frame. `options` are
  // options()'s, and: fogStrength and fogColor ([r, g, b]) left out, the page's own; centre ({x, z}) where the world's
  // centre is (the foot is its height); melt, the share of each band's height, from its bottom, that melts into the
  // fog's colour (deux-generaux's 0.22; 0 none); eyeMelt, how high, in radians above the camera's own horizon, the
  // bands melt into the fog's colour as the camera sees them (0 none): it holds where melt's part of a band is hidden
  // behind the world's own hills; horizon, how high, in radians, the fog climbs the sky from the horizon; tintSky, the
  // background's tint (tintTo) laid on the sky's gradients and the fog's colour too (off: as the flat reader, which
  // tints the layers only); renderOrder, the first of the render orders the rings take (one per band, the sky one under it), below
  // what the game draws transparent. The scene's camera needs a far plane past the farthest band: the sky and a still
  // band are drawn just inside it.
  constructor(background, options = {}) {
    this.background = background;
    this.group = new THREE.Group();
    this.group.name = 'plax-rings';
    this.options = {};
    // Per layer state (PlaxBackground's layer objects): its band's meshes, and what they were built from.
    this._built = new Map();
    this._bandsCache = new WeakMap();
    this._version = 0;
    this._sky = skyMesh();
    this._sky.onBeforeRender = (renderer, scene, camera) => this._follow(this._sky, camera, 0.9);
    this.group.add(this._sky);
    this.set(options);
  }

  // Changes some options (as the constructor takes them); the bands are rebuilt at the next update().
  set(options) {
    this.options = { melt: 0.22, eyeMelt: 0, horizon: 0.12, tintSky: false, renderOrder: -1000, centre: { x: 0, z: 0 }, ...this.options, ...options };
    this.group.position.set(this.options.centre.x ?? 0, 0, this.options.centre.z ?? 0);
    this._version++;
  }

  // The options core's ParallaxRings takes for `page`: these, the fog the page's when not given.
  ringOptions(page) {
    const o = this.options;
    return PlaxRings.options({ front: o.front, far: o.far, foot: o.foot, lift: o.lift, scale: o.scale,
      pageDegrees: o.pageDegrees, fogStrength: o.fogStrength ?? page?.fogStrength ?? 0 });
  }

  // Moves the background by `seconds` (its act: speedXAtRest's drift, the fades, the tint; 0 for none, when the game
  // acts it itself), then lays the rings out as it is now.
  update(seconds = 0) {
    const bg = this.background;
    if (seconds) bg.act(seconds);
    // Each layer's slot, for the cross-fade's alphas (PlaxBackground._alphaOf).
    bg._drawOrder();
    const sides = [[bg.layers, bg.page], [bg.transferLayers, bg.transferPage]];
    const live = new Set(), all = [];
    for (const [layers, page] of sides) {
      if (!layers.length || !page) continue;
      const options = this.ringOptions(page);
      for (const band of this._bandsOf(layers, options)) {
        const l = layers[band.layer];
        live.add(l);
        all.push(this._sync(l, band, page));
      }
    }
    for (const [l, built] of this._built) {
      if (!live.has(l)) this._dropBand(l, built);
    }
    // Far first, the outgoing page before the incoming one among equals; the sky under them all.
    // Compared, not subtracted: Infinity - Infinity is NaN, and two still bands keep their order.
    const order = all.map((b, i) => [b, i]).sort((a, c) => {
      const ra = a[0].band.radius, rc = c[0].band.radius;
      return ra === rc ? a[1] - c[1] : rc > ra ? 1 : -1;
    });
    order.forEach(([b], i) => { for (const mesh of b.meshes) mesh.renderOrder = this.options.renderOrder + 1 + i; });
    this._sky.renderOrder = this.options.renderOrder;
    this._drawSky();
  }

  dispose() {
    for (const [l, built] of this._built) this._dropBand(l, built);
    this._sky.geometry.dispose();
    this._sky.material.dispose();
  }

  // The bands of `layers`, computed again when the options, the layers or a sequence's cycle changed.
  _bandsOf(layers, options) {
    const key = `${this._version}|${JSON.stringify(options)}|${layers.map(l => `${l.drawnSeed ?? ''}:${l.width}`).join(',')}`;
    const cached = this._bandsCache.get(layers);
    if (cached?.key === key) return cached.bands;
    const bands = PlaxRings.bands(layers, this.background.worldWidth, options);
    this._bandsCache.set(layers, { key, bands });
    return bands;
  }

  // The meshes of layer `l`'s band, built when it is new or its numbers changed; then this frame's scroll, colour,
  // fog and fade set on them.
  _sync(l, band, page) {
    const bg = this.background;
    let built = this._built.get(l);
    if (!built || built.band !== band) {
      if (built) this._dropBand(l, built);
      built = this._buildBand(l, band);
      this._built.set(l, built);
    }
    // The 2D reader's scroll: the tile at distanceX, one tile 1 / repeat of the ring.
    const tile = l.width + l.model.padX;
    built.holder.rotation.y = tile > 0 ? -l.distanceX / tile * 2 * Math.PI / band.repeat : 0;
    const u = built.uniforms;
    // During a cross-fade, the layer's opacity as the flat reader's; a dissolve fades it by its share, it has no mask.
    const fade = bg._masked(l) ? bg._shareOf(l) : bg._alphaOf(l);
    const alpha = bg.tint[3] * fade;
    const tint = bg.tint.map(packed);
    u.tint.value.set(tint[0], tint[1], tint[2], packedAlpha(alpha));
    for (const mesh of built.meshes) mesh.visible = alpha > 0;
    const fog = this._fogColor(page);
    u.fog_color.value.set(fog[0], fog[1], fog[2]);
    u.haze.value = band.fog;
    u.melt.value = this.options.melt;
    u.eye_melt.value = this.options.eyeMelt;
    if (bg._graded(l)) {
      const c = bg._style.color, total = Math.max(bg.layers.length, bg.transferLayers.length);
      u.grade.value.set(c[0], c[1], c[2], clamp(bg._style.gradeAt(bg._newAlpha, l.slot, total), 0, 1));
    } else u.grade.value.set(0, 0, 0, 0);
    return built;
  }

  // Layer `l`'s band as meshes, one per texture its images sit on, in a holder turned by the scroll: each image (an
  // IMAGE or SHADER layer's, a SEQUENCE layer's slots) laid round the ring once per repeat, curved, its texture linear in
  // angle and in elevation. A still band is on a cylinder of radius 1 round the camera, scaled to the sky's.
  _buildBand(l, band) {
    const still = band.radius === Infinity, R = still ? 1 : band.radius, footY = still ? 0 : this.options.foot;
    const holder = new THREE.Group(), uniforms = ringUniforms(), m = l.model;
    const tile = l.width + m.padX, turn = 2 * Math.PI / band.repeat;
    const images = [];
    if (m.kind === 'SEQUENCE' && l.edges) {
      for (let slot = 0; slot < l.cycle.length; slot++) {
        const segment = l.segments[l.cycle[slot]];
        images.push([segment, l.height * l.edges[slot] + slot * m.padX, l.height * segment.aspect]);
      }
    } else images.push([l, 0, l.width]);
    const runs = new Map();
    const ROWS = 8, STEP = 2 * Math.PI / 192;
    for (let k = 0; k < band.repeat; k++) {
      for (const [image, left, width] of images) {
        if (!(width > 0) || !(tile > 0)) continue;
        const fx = m.flipX, fy = m.flipY;
        // As _drawRegion: the packed image in its box, a flip mirroring where it sits in it.
        const x0 = left + width * (fx ? 1 - image.trimLeft - image.packedW : image.trimLeft), w = width * image.packedW;
        const b0 = fy ? 1 - image.trimBottom - image.packedH : image.trimBottom, bh = image.packedH;
        const texture = image.region.page.texture, src = image.src, W = texture.image.width, H = texture.image.height;
        let u0 = src.x / W, u1 = (src.x + src.w) / W, vTop = src.y / H, vBottom = (src.y + src.h) / H;
        if (fx) [u0, u1] = [u1, u0];
        if (fy) [vTop, vBottom] = [vBottom, vTop];
        const run = runs.get(texture) ?? { position: [], uv: [], band: [], index: [] };
        runs.set(texture, run);
        const cols = Math.max(1, Math.ceil(w / tile * turn / STEP));
        const first = run.position.length / 3;
        for (let j = 0; j <= ROWS; j++) {
          const along = b0 + bh * j / ROWS;
          const y = footY + R * Math.tan(band.bottom + along * (band.top - band.bottom));
          for (let i = 0; i <= cols; i++) {
            const a = k * turn + (x0 + w * i / cols) / tile * turn;
            run.position.push(R * Math.sin(a), y, -R * Math.cos(a));
            run.uv.push(u0 + (u1 - u0) * i / cols, vBottom + (vTop - vBottom) * j / ROWS);
            run.band.push(along);
          }
        }
        for (let j = 0; j < ROWS; j++) {
          for (let i = 0; i < cols; i++) {
            const a = first + j * (cols + 1) + i, b = a + 1, c = a + cols + 1, d = c + 1;
            run.index.push(a, b, d, a, d, c);
          }
        }
      }
    }
    const meshes = [];
    for (const [texture, run] of runs) {
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(run.position, 3));
      g.setAttribute('uv', new THREE.Float32BufferAttribute(run.uv, 2));
      g.setAttribute('band', new THREE.Float32BufferAttribute(run.band, 1));
      g.setIndex(run.index);
      const mesh = new THREE.Mesh(g, ringMaterial(texture, uniforms));
      mesh.frustumCulled = false;
      if (still) mesh.onBeforeRender = (renderer, scene, camera) => this._follow(holder, camera, 0.85);
      holder.add(mesh);
      meshes.push(mesh);
    }
    this.group.add(holder);
    return { band, holder, meshes, uniforms };
  }

  _dropBand(l, built) {
    this.group.remove(built.holder);
    for (const mesh of built.meshes) {
      mesh.geometry.dispose();
      mesh.material.dispose();
    }
    this._built.delete(l);
  }

  // Puts `object` (the sky, a still band) round `camera`, its radius `share` of the camera's far plane.
  _follow(object, camera, share) {
    this.group.updateWorldMatrix(true, false);
    object.position.copy(camera.getWorldPosition(_cameraAt)).sub(_groupAt.setFromMatrixPosition(this.group.matrixWorld));
    object.scale.setScalar((camera.far ?? 1000) * share);
    object.updateMatrixWorld(true);
  }

  // The page's gradients on the sky, by elevation, the fog under the horizon.
  _drawSky() {
    const bg = this.background, u = this._sky.material.uniforms, page = bg.page;
    this._sky.visible = !!page;
    if (!page) return;
    const g = PlaxRings.gradient(bg._topHalfSize, bg._bottomHalfSize, bg.worldWidth, this.ringOptions(page));
    const [tt, tb, bt, bb] = bg._gradient.map(c => this._skyTinted(c).map(packed));
    u.top_to.value.set(...tt);
    u.top_from.value.set(...tb);
    u.bottom_to.value.set(...bt);
    u.bottom_from.value.set(...bb);
    u.top.value.set(g.topFrom, g.topTo);
    u.bottom.value.set(g.bottomFrom, g.bottomTo);
    const fog = this._fogColor(page);
    u.fog_color.value.set(fog[0], fog[1], fog[2]);
    u.horizon.value = this.options.horizon;
  }

  // The fog's colour (options.fogColor, else the page's), tinted with tintSky.
  _fogColor(page) {
    return this._skyTinted(this.options.fogColor ?? page.fogColor);
  }

  // Colour `c`'s red, green and blue, times the background's tint with tintSky.
  _skyTinted(c) {
    const t = this.background.tint;
    return this.options.tintSky ? [c[0] * t[0], c[1] * t[1], c[2] * t[2]] : c.slice(0, 3);
  }
}

const _cameraAt = new THREE.Vector3(), _groupAt = new THREE.Vector3();

// A band's numbers, shared by its meshes: the tint (packed as libGDX packs a batch colour, the cross-fade's alpha in
// it), the fog's colour and the band's haze (PlaxEffects.fogOf), the melt and a transfert's grade (as hazed()).
function ringUniforms() {
  return {
    tint: { value: new THREE.Vector4(1, 1, 1, 1) }, fog_color: { value: new THREE.Vector3(0.93, 0.95, 0.97) },
    haze: { value: 0 }, melt: { value: 0 }, eye_melt: { value: 0 }, grade: { value: new THREE.Vector4(0, 0, 0, 0) },
  };
}

// A band's material on `texture`: raw colours, blended as every layer is, behind the 3D world (it tests its depth and
// writes none).
function ringMaterial(texture, uniforms) {
  return new THREE.ShaderMaterial({
    ...LAYER_BLENDING, depthTest: true, uniforms: { ...uniforms, tex: { value: texture } },
    vertexShader: `attribute float band;
varying vec2 vUv;
varying float vBand;
varying vec3 vFromEye;
void main() {
	vUv = uv;
	vBand = band;
	vFromEye = (modelMatrix * vec4(position, 1.0)).xyz - cameraPosition;
	gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`,
    fragmentShader: `uniform sampler2D tex;
uniform vec4 tint;
uniform vec3 fog_color;
uniform float haze;
uniform float melt;
uniform float eye_melt;
uniform vec4 grade;
varying vec2 vUv;
varying float vBand;
varying vec3 vFromEye;
void main() {
	vec4 color = tint * texture2D(tex, vUv);
	float melted = melt > 0.0 ? 1.0 - smoothstep(0.0, melt, vBand) : 0.0;
	if (eye_melt > 0.0)
		melted = max(melted, 1.0 - smoothstep(0.0, eye_melt, asin(clamp(normalize(vFromEye).y, -1.0, 1.0))));
	float fog = haze + (1.0 - haze) * melted;
	gl_FragColor = vec4(mix(mix(color.rgb, fog_color, fog), grade.rgb, grade.a), color.a);
}
`,
  });
}

// The sky: a sphere of radius 1 round the camera (PlaxRings._follow scales it), coloured by elevation as
// ParallaxRings.gradient lays the page's gradients out, under the bands; under the horizon, the fog.
function skyMesh() {
  const material = new THREE.ShaderMaterial({
    uniforms: {
      top_from: { value: new THREE.Vector3(1, 1, 1) }, top_to: { value: new THREE.Vector3(1, 1, 1) },
      bottom_from: { value: new THREE.Vector3(1, 1, 1) }, bottom_to: { value: new THREE.Vector3(1, 1, 1) },
      top: { value: new THREE.Vector2(0, 1) }, bottom: { value: new THREE.Vector2(0, 1) },
      fog_color: { value: new THREE.Vector3(0.93, 0.95, 0.97) }, horizon: { value: 0.12 },
    },
    side: THREE.BackSide, depthWrite: false, depthTest: false, blending: THREE.NoBlending,
    vertexShader: `varying vec3 vDirection;
void main() {
	vDirection = position;
	gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`,
    fragmentShader: `uniform vec3 top_from, top_to, bottom_from, bottom_to, fog_color;
uniform vec2 top, bottom;
uniform float horizon;
varying vec3 vDirection;
float along(float e, vec2 span) {
	return span.y > span.x ? clamp((e - span.x) / (span.y - span.x), 0.0, 1.0) : step(span.x, e);
}
void main() {
	float e = asin(clamp(normalize(vDirection).y, -1.0, 1.0));
	vec3 color = mix(top_from, top_to, along(e, top));
	if (e <= bottom.y)
		color = mix(bottom_from, bottom_to, along(e, bottom));
	gl_FragColor = vec4(mix(fog_color, color, smoothstep(-0.02, horizon, e)), 1.0);
}
`,
  });
  const mesh = new THREE.Mesh(new THREE.SphereGeometry(1, 48, 24), material);
  mesh.name = 'plax-rings-sky';
  mesh.frustumCulled = false;
  return mesh;
}

// A region's box: where its packed image sits in it, the image's size, its width in heights, and the texels drawn of it. useOriginalSize: a
// region packed with its whitespace stripped keeps its original size, and its packed image is drawn at its offset
// inside it. Off: the packed image is stretched over the whole box.
function measureRegion(region, useOriginalSize) {
  const m = { region, trimLeft: 0, trimBottom: 0, packedW: 1, packedH: 1, imageW: region.width, imageH: region.height };
  if (useOriginalSize && region.originalWidth > 0 && region.originalHeight > 0) {
    m.trimLeft = region.offsetX / region.originalWidth;
    m.trimBottom = region.offsetY / region.originalHeight;
    m.packedW = region.width / region.originalWidth;
    m.packedH = region.height / region.originalHeight;
    m.imageW = region.originalWidth;
    m.imageH = region.originalHeight;
  }
  m.aspect = m.imageW / m.imageH;
  // Half a texel in from each edge, as ParallaxPageReader draws a region: its neighbours in the atlas never bleed in.
  const inset = region.width >= 2 && region.height >= 2 ? 0.5 : 0;
  m.src = { x: region.x + inset, y: region.y + inset, w: region.width - 2 * inset, h: region.height - 2 * inset };
  return m;
}

// --- PARTICLES layers (PlaxParticles: core's ParallaxParticles, on libGDX's ParticleEffect) --------------------------

// A PARTICLES layer draws the libGDX effect (.p) its page names (particlesLibgdx), found in the atlas's folder and
// fetched by loadParticles: libGDX's ParticleEffect, ParticleEmitter and the Sprite each particle is, ported here line
// for line (libGDX 1.14, Apache 2.0) in Java's float arithmetic (Math.fround), with MathUtils' random (RandomXS128) and
// sine table, so that one seed (PlaxParticles.random.setSeed, as engines/rounds' Round.seedRandom) drops the snow flake
// for flake as libGDX does. Only what a .p file can ask is ported: no ParticleEffectPool, no scaling, no flips.
const f32p = Math.fround;
const MASK64 = (1n << 64n) - 1n;

// libGDX's RandomXS128 (xorshift128+), its 64-bit longs as unsigned BigInts.
class RandomXS128 {
  constructor(seed = BigInt(Math.floor(Math.random() * 2 ** 52))) {
    this.setSeed(seed);
  }

  // A Java long (a Number or a BigInt, as Java's int widens to it).
  setSeed(seed) {
    const s = BigInt.asUintN(64, BigInt(seed));
    const seed0 = murmurHash3(s === 0n ? 1n << 63n : s);
    this.seed0 = seed0;
    this.seed1 = murmurHash3(seed0);
  }

  nextLong() {
    let s1 = this.seed0;
    const s0 = this.seed1;
    this.seed0 = s0;
    s1 = (s1 ^ (s1 << 23n)) & MASK64;
    this.seed1 = s1 ^ s0 ^ (s1 >> 17n) ^ (s0 >> 26n);
    return (this.seed1 + s0) & MASK64;
  }

  nextFloat() {
    return Number(this.nextLong() >> 40n) / 16777216;
  }

  // nextInt(n): nextLong(n), Java's signed longs.
  nextInt(n) {
    const bound = BigInt(n);
    for (;;) {
      const bits = this.nextLong() >> 1n, value = bits % bound;
      if (BigInt.asIntN(64, bits - value + (bound - 1n)) >= 0n) return Number(value);
    }
  }
}

function murmurHash3(x) {
  x ^= x >> 33n;
  x = (x * 0xff51afd7ed558ccdn) & MASK64;
  x ^= x >> 33n;
  x = (x * 0xc4ceb9fe1a85ec53n) & MASK64;
  x ^= x >> 33n;
  return x;
}

// MathUtils.sinDeg and cosDeg: a table of 2^14 sines, read at the index the angle falls in.
const SIN_BITS = 14, SIN_MASK = ~(-1 << SIN_BITS), SIN_COUNT = SIN_MASK + 1;
const DEG_TO_INDEX = f32p(SIN_COUNT / 360);
const SIN_TABLE = (() => {
  const table = new Float32Array(SIN_COUNT), radFull = f32p(f32p(Math.PI) * 2);
  for (let i = 0; i < SIN_COUNT; i++) table[i] = Math.sin(f32p(f32p(f32p(i + 0.5) / SIN_COUNT) * radFull));
  table[0] = 0;
  table[(f32p(90 * DEG_TO_INDEX) | 0) & SIN_MASK] = 1;
  table[(f32p(180 * DEG_TO_INDEX) | 0) & SIN_MASK] = 0;
  table[(f32p(270 * DEG_TO_INDEX) | 0) & SIN_MASK] = -1;
  return table;
})();
const sinDeg = degrees => SIN_TABLE[(f32p(degrees * DEG_TO_INDEX) | 0) & SIN_MASK];
const cosDeg = degrees => SIN_TABLE[(f32p(f32p(degrees + 90) * DEG_TO_INDEX) | 0) & SIN_MASK];

// What a particle's colour and blending are drawn with: libGDX's emitter sets the batch's blend function as its .p
// asks (ParticleEmitter.draw). The alpha keeps ONE, ONE_MINUS_SRC_ALPHA, as every layer's: the drawing buffer stays
// opaque.
const PARTICLE_BLENDS = {
  normal: [THREE.SrcAlphaFactor, THREE.OneMinusSrcAlphaFactor],
  additive: [THREE.SrcAlphaFactor, THREE.OneFactor],
  premultiplied: [THREE.OneFactor, THREE.OneMinusSrcAlphaFactor],
};

// The text of a .p file, one line at a time, as ParticleEmitter.load reads its BufferedReader.
class EffectReader {
  constructor(text) {
    // As Java's readLine: a last line break ends the last line, it does not start an empty one.
    this.lines = text.split(/\r?\n/);
    if (this.lines[this.lines.length - 1] === '') this.lines.pop();
    this.at = 0;
    this.marked = 0;
  }

  readLine() {
    return this.at < this.lines.length ? this.lines[this.at++] : null;
  }

  mark() { this.marked = this.at; }

  reset() { this.at = this.marked; }

  // ParticleEmitter.readString: what follows the line's first colon, or the whole line without one.
  string(name) {
    const line = this.readLine();
    if (line === null) throw new Error(`plax: a .p file misses its ${name}`);
    return line.slice(line.indexOf(':') + 1).trim();
  }

  bool(name) { return this.string(name) === 'true'; }

  int(name) { return parseInt(this.string(name), 10); }

  float(name) { return f32p(parseFloat(this.string(name))); }
}

// ParticleEmitter.RangedNumericValue, ScaledNumericValue and IndependentScaledNumericValue in one: what the file
// holds of each is read by the matching load.
class ParticleValue {
  constructor(alwaysActive = false) {
    this.alwaysActive = alwaysActive;
    this.active = alwaysActive;
    this.lowMin = this.lowMax = this.highMin = this.highMax = 0;
    this.relative = false;
    this.independent = false;
    this.scaling = [1];
    this.timeline = [0];
  }

  isActive() { return this.alwaysActive || this.active; }

  _loadActive(r) {
    this.active = this.alwaysActive ? true : r.bool('active');
  }

  loadRanged(r) {
    this._loadActive(r);
    if (!this.active) return this;
    this.lowMin = r.float('lowMin');
    this.lowMax = r.float('lowMax');
    return this;
  }

  loadScaled(r) {
    this.loadRanged(r);
    if (!this.active) return this;
    this.highMin = r.float('highMin');
    this.highMax = r.float('highMax');
    this.relative = r.bool('relative');
    this.scaling = Array.from({ length: r.int('scalingCount') }, (_, i) => r.float('scaling' + i));
    this.timeline = Array.from({ length: r.int('timelineCount') }, (_, i) => r.float('timeline' + i));
    return this;
  }

  // An older file has no "independent" line: the reader goes back.
  loadIndependent(r) {
    this.loadScaled(r);
    r.mark();
    const line = r.readLine();
    if (line === null) throw new Error('plax: a .p file misses its independent');
    if (line.includes('independent')) this.independent = line.slice(line.indexOf(':') + 1).trim() === 'true';
    else r.reset();
    return this;
  }

  newLowValue(random) {
    return f32p(this.lowMin + f32p(f32p(this.lowMax - this.lowMin) * random.nextFloat()));
  }

  newHighValue(random) {
    return f32p(this.highMin + f32p(f32p(this.highMax - this.highMin) * random.nextFloat()));
  }

  getScale(percent) {
    const timeline = this.timeline, n = timeline.length;
    let endIndex = -1;
    for (let i = 1; i < n; i++) {
      if (timeline[i] > percent) {
        endIndex = i;
        break;
      }
    }
    if (endIndex === -1) return this.scaling[n - 1];
    const startIndex = endIndex - 1, startValue = this.scaling[startIndex], startTime = timeline[startIndex];
    return f32p(startValue + f32p(f32p(this.scaling[endIndex] - startValue)
      * f32p(f32p(percent - startTime) / f32p(timeline[endIndex] - startTime))));
  }
}

// ParticleEmitter.GradientColorValue: the tint over a particle's life.
class GradientColorValue {
  constructor() {
    this.colors = [1, 1, 1];
    this.timeline = [0];
    this.temp = [0, 0, 0];
  }

  load(r) {
    this.colors = Array.from({ length: r.int('colorsCount') }, (_, i) => r.float('colors' + i));
    this.timeline = Array.from({ length: r.int('timelineCount') }, (_, i) => r.float('timeline' + i));
    return this;
  }

  getColor(percent) {
    const timeline = this.timeline, n = timeline.length, colors = this.colors, temp = this.temp;
    let startIndex = 0, endIndex = -1;
    for (let i = 1; i < n; i++) {
      if (timeline[i] > percent) {
        endIndex = i;
        break;
      }
      startIndex = i;
    }
    const startTime = timeline[startIndex];
    startIndex *= 3;
    const r1 = colors[startIndex], g1 = colors[startIndex + 1], b1 = colors[startIndex + 2];
    if (endIndex === -1) {
      temp[0] = r1;
      temp[1] = g1;
      temp[2] = b1;
      return temp;
    }
    const factor = f32p(f32p(percent - startTime) / f32p(timeline[endIndex] - startTime));
    endIndex *= 3;
    temp[0] = f32p(r1 + f32p(f32p(colors[endIndex] - r1) * factor));
    temp[1] = f32p(g1 + f32p(f32p(colors[endIndex + 1] - g1) * factor));
    temp[2] = f32p(b1 + f32p(f32p(colors[endIndex + 2] - b1) * factor));
    return temp;
  }
}

// What an emitter reads from its .p, as ParticleEmitter.load: shared by every emitter built from it.
function readEmitter(r) {
  const e = { name: r.string('name') };
  r.readLine();
  e.delay = new ParticleValue().loadRanged(r);
  r.readLine();
  e.duration = new ParticleValue(true).loadRanged(r);
  r.readLine();
  e.minParticleCount = r.int('minParticleCount');
  e.maxParticleCount = r.int('maxParticleCount');
  r.readLine();
  e.emission = new ParticleValue(true).loadScaled(r);
  r.readLine();
  e.life = new ParticleValue(true).loadIndependent(r);
  r.readLine();
  e.lifeOffset = new ParticleValue().loadIndependent(r);
  r.readLine();
  e.xOffset = new ParticleValue().loadScaled(r);
  r.readLine();
  e.yOffset = new ParticleValue().loadScaled(r);
  r.readLine();
  e.spawnShape = { shape: 'point', edges: false, side: 'both' };
  {
    const shape = r.string('shape');
    e.spawnShape.shape = shape;
    if (shape === 'ellipse') {
      e.spawnShape.edges = r.bool('edges');
      e.spawnShape.side = r.string('side');
    }
  }
  r.readLine();
  e.spawnWidth = new ParticleValue(true).loadScaled(r);
  r.readLine();
  e.spawnHeight = new ParticleValue(true).loadScaled(r);
  e.xScale = new ParticleValue(true);
  e.yScale = new ParticleValue();
  if (r.readLine().trim() === '- Scale -') {
    e.xScale.loadScaled(r);
    e.yScale.active = false;
  } else {
    e.xScale.loadScaled(r);
    r.readLine();
    e.yScale.loadScaled(r);
  }
  r.readLine();
  e.velocity = new ParticleValue().loadScaled(r);
  r.readLine();
  e.angle = new ParticleValue().loadScaled(r);
  r.readLine();
  e.rotation = new ParticleValue().loadScaled(r);
  r.readLine();
  e.wind = new ParticleValue().loadScaled(r);
  r.readLine();
  e.gravity = new ParticleValue().loadScaled(r);
  r.readLine();
  e.tint = new GradientColorValue().load(r);
  r.readLine();
  e.transparency = new ParticleValue(true).loadScaled(r);
  r.readLine();
  e.attached = r.bool('attached');
  e.continuous = r.bool('continuous');
  e.aligned = r.bool('aligned');
  e.additive = r.bool('additive');
  e.behind = r.bool('behind');
  e.premultipliedAlpha = false;
  e.spriteMode = 'single';
  let line = r.readLine();
  if (line?.startsWith('premultipliedAlpha')) {
    e.premultipliedAlpha = line.slice(line.indexOf(':') + 1).trim() === 'true';
    line = r.readLine();
  }
  if (line?.startsWith('spriteMode')) {
    e.spriteMode = line.slice(line.indexOf(':') + 1).trim();
    line = r.readLine();
  }
  e.imagePaths = [];
  while ((line = r.readLine()) !== null && line !== '') e.imagePaths.push(line);
  return e;
}

// ParticleEffect.loadEmitters: the emitters of a .p file, each ending at a blank line.
function readEffect(text) {
  const r = new EffectReader(text), emitters = [];
  for (;;) {
    emitters.push(readEmitter(r));
    if (r.readLine() === null) break;
  }
  return emitters;
}

// A libGDX Sprite on an atlas region (atlas.createSprite): its size the region's, its origin its middle.
function spriteOf(region) {
  const image = region.page.texture.image;
  return {
    texture: region.page.texture,
    u: region.x / image.width, v: region.y / image.height,
    u2: (region.x + region.width) / image.width, v2: (region.y + region.height) / image.height,
    width: region.width, height: region.height, originX: region.width / 2, originY: region.height / 2,
  };
}

const UPDATE_SCALE = 1 << 0, UPDATE_ANGLE = 1 << 1, UPDATE_ROTATION = 1 << 2, UPDATE_VELOCITY = 1 << 3;
const UPDATE_WIND = 1 << 4, UPDATE_GRAVITY = 1 << 5, UPDATE_TINT = 1 << 6, UPDATE_SPRITE = 1 << 7;

// A particle: libGDX's ParticleEmitter.Particle, a Sprite with its life's numbers.
class Particle {
  constructor(sprite) {
    this.set(sprite);
    this.life = this.currentLife = 0;
    this.xScale = this.xScaleDiff = this.yScale = this.yScaleDiff = 0;
    this.rotationStart = this.rotationDiff = 0;
    this.velocity = this.velocityDiff = this.angle = this.angleDiff = this.angleCos = this.angleSin = 0;
    this.transparency = this.transparencyDiff = this.wind = this.windDiff = this.gravity = this.gravityDiff = 0;
    this.tint = [0, 0, 0];
    this.frame = 0;
    this.vertices = new Float32Array(8);
  }

  // Sprite.set: the sprite's region, size, origin, white, no rotation nor scale.
  set(sprite) {
    this.texture = sprite.texture;
    this.u = sprite.u;
    this.v = sprite.v;
    this.u2 = sprite.u2;
    this.v2 = sprite.v2;
    this.x = this.y = 0;
    this.width = sprite.width;
    this.height = sprite.height;
    this.originX = sprite.originX;
    this.originY = sprite.originY;
    this.rotation = 0;
    this.scaleX = this.scaleY = 1;
    this.color = [1, 1, 1, 1];
  }

  setRegion(sprite) {
    this.texture = sprite.texture;
    this.u = sprite.u;
    this.v = sprite.v;
    this.u2 = sprite.u2;
    this.v2 = sprite.v2;
  }

  setBounds(x, y, width, height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  translate(x, y) {
    this.x = f32p(this.x + x);
    this.y = f32p(this.y + y);
  }

  // Sprite.getVertices' corners, x then y: bottom left, top left, top right, bottom right.
  getVertices() {
    const v = this.vertices;
    let localX = -this.originX, localY = -this.originY;
    let localX2 = f32p(localX + this.width), localY2 = f32p(localY + this.height);
    const worldOriginX = f32p(this.x - localX), worldOriginY = f32p(this.y - localY);
    if (this.scaleX !== 1 || this.scaleY !== 1) {
      localX = f32p(localX * this.scaleX);
      localY = f32p(localY * this.scaleY);
      localX2 = f32p(localX2 * this.scaleX);
      localY2 = f32p(localY2 * this.scaleY);
    }
    if (this.rotation !== 0) {
      const cos = cosDeg(this.rotation), sin = sinDeg(this.rotation);
      const xCos = f32p(localX * cos), xSin = f32p(localX * sin), yCos = f32p(localY * cos), ySin = f32p(localY * sin);
      const x2Cos = f32p(localX2 * cos), x2Sin = f32p(localX2 * sin);
      const y2Cos = f32p(localY2 * cos), y2Sin = f32p(localY2 * sin);
      const x1 = f32p(f32p(xCos - ySin) + worldOriginX), y1 = f32p(f32p(yCos + xSin) + worldOriginY);
      const x2 = f32p(f32p(xCos - y2Sin) + worldOriginX), y2 = f32p(f32p(y2Cos + xSin) + worldOriginY);
      const x3 = f32p(f32p(x2Cos - y2Sin) + worldOriginX), y3 = f32p(f32p(y2Cos + x2Sin) + worldOriginY);
      v[0] = x1; v[1] = y1; v[2] = x2; v[3] = y2; v[4] = x3; v[5] = y3;
      v[6] = x1 + (x3 - x2); v[7] = y3 - (y2 - y1);
    } else {
      const x1 = f32p(localX + worldOriginX), y1 = f32p(localY + worldOriginY);
      const x2 = f32p(localX2 + worldOriginX), y2 = f32p(localY2 + worldOriginY);
      v[0] = x1; v[1] = y1; v[2] = x1; v[3] = y2; v[4] = x2; v[5] = y2; v[6] = x2; v[7] = y1;
    }
    return v;
  }
}

// One emitter of a PARTICLES layer's effect: libGDX's ParticleEmitter, its particles built when the page loads
// (ParallaxParticles.allocate).
class PlaxEmitter {
  constructor(spec, sprites, random) {
    Object.assign(this, spec);
    this.random = random;
    this.sprites = sprites;
    this.particles = sprites.length ? Array.from({ length: spec.maxParticleCount }, () => new Particle(sprites[0])) : [];
    this.active = new Array(spec.maxParticleCount).fill(false);
    this.activeCount = 0;
    this.accumulator = 0;
    this.x = this.y = 0;
    this.firstUpdate = false;
    this.allowCompletion = false;
    this.updateFlags = 0;
    this.emissionNow = this.emissionDiff = this.emissionDelta = 0;
    this.lifeOffsetNow = this.lifeOffsetDiff = this.lifeNow = this.lifeDiff = 0;
    this.spawnWidthNow = this.spawnWidthDiff = this.spawnHeightNow = this.spawnHeightDiff = 0;
    this.durationNow = 1;
    this.durationTimer = 0;
    this.delayNow = this.delayTimer = 0;
  }

  addParticle() {
    if (this.activeCount === this.maxParticleCount) return;
    for (let i = 0; i < this.active.length; i++) {
      if (!this.active[i]) {
        this._activateParticle(i);
        this.active[i] = true;
        this.activeCount++;
        break;
      }
    }
  }

  addParticles(count) {
    count = Math.min(count, this.maxParticleCount - this.activeCount);
    if (count === 0) return;
    const active = this.active, n = active.length;
    let index = 0;
    outer:
    for (let i = 0; i < count; i++) {
      for (; index < n; index++) {
        if (!active[index]) {
          this._activateParticle(index);
          active[index++] = true;
          continue outer;
        }
      }
      break;
    }
    this.activeCount += count;
  }

  update(delta) {
    this.accumulator = f32p(this.accumulator + f32p(delta * 1000));
    if (this.accumulator < 1) return;
    const deltaMillis = this.accumulator | 0;
    this.accumulator = f32p(this.accumulator - deltaMillis);
    if (this.delayTimer < this.delayNow) {
      this.delayTimer = f32p(this.delayTimer + deltaMillis);
    } else {
      let done = false;
      if (this.firstUpdate) {
        this.firstUpdate = false;
        this.addParticle();
      }
      if (this.durationTimer < this.durationNow) this.durationTimer = f32p(this.durationTimer + deltaMillis);
      else if (!this.continuous || this.allowCompletion) done = true;
      else this._restart();
      if (!done) {
        this.emissionDelta += deltaMillis;
        let emissionTime = f32p(this.emissionNow
          + f32p(this.emissionDiff * this.emission.getScale(f32p(this.durationTimer / this.durationNow))));
        if (emissionTime > 0) {
          emissionTime = f32p(1000 / emissionTime);
          if (this.emissionDelta >= emissionTime) {
            let emitCount = f32p(this.emissionDelta / emissionTime) | 0;
            emitCount = Math.min(emitCount, this.maxParticleCount - this.activeCount);
            this.emissionDelta = f32p(this.emissionDelta - f32p(emitCount * emissionTime)) | 0;
            this.emissionDelta = f32p(this.emissionDelta % emissionTime) | 0;
            this.addParticles(emitCount);
          }
        }
        if (this.activeCount < this.minParticleCount) this.addParticles(this.minParticleCount - this.activeCount);
      }
    }
    const active = this.active, particles = this.particles;
    let activeCount = this.activeCount;
    for (let i = 0; i < active.length; i++) {
      if (active[i] && !this._updateParticle(particles[i], delta, deltaMillis)) {
        active[i] = false;
        activeCount--;
      }
    }
    this.activeCount = activeCount;
  }

  start() {
    this.firstUpdate = true;
    this.allowCompletion = false;
    this._restart();
  }

  reset(start) {
    this.emissionDelta = 0;
    this.durationTimer = this.durationNow;
    this.active.fill(false);
    this.activeCount = 0;
    if (start) this.start();
  }

  isComplete() {
    if (this.continuous && !this.allowCompletion) return false;
    if (this.delayTimer < this.delayNow) return false;
    return this.durationTimer >= this.durationNow && this.activeCount === 0;
  }

  _restart() {
    const random = this.random;
    this.delayNow = this.delay.active ? this.delay.newLowValue(random) : 0;
    this.delayTimer = 0;
    this.durationTimer = f32p(this.durationTimer - this.durationNow);
    this.durationNow = this.duration.newLowValue(random);
    this.emissionNow = this.emission.newLowValue(random) | 0;
    this.emissionDiff = this.emission.newHighValue(random) | 0;
    if (!this.emission.relative) this.emissionDiff -= this.emissionNow;
    if (!this.life.independent) this._generateLifeValues();
    if (!this.lifeOffset.independent) this._generateLifeOffsetValues();
    this.spawnWidthNow = this.spawnWidth.newLowValue(random);
    this.spawnWidthDiff = this.spawnWidth.newHighValue(random);
    if (!this.spawnWidth.relative) this.spawnWidthDiff = f32p(this.spawnWidthDiff - this.spawnWidthNow);
    this.spawnHeightNow = this.spawnHeight.newLowValue(random);
    this.spawnHeightDiff = this.spawnHeight.newHighValue(random);
    if (!this.spawnHeight.relative) this.spawnHeightDiff = f32p(this.spawnHeightDiff - this.spawnHeightNow);
    let flags = 0;
    if (this.angle.active && this.angle.timeline.length > 1) flags |= UPDATE_ANGLE;
    if (this.velocity.active) flags |= UPDATE_VELOCITY;
    if (this.xScale.timeline.length > 1) flags |= UPDATE_SCALE;
    if (this.yScale.active && this.yScale.timeline.length > 1) flags |= UPDATE_SCALE;
    if (this.rotation.active && this.rotation.timeline.length > 1) flags |= UPDATE_ROTATION;
    if (this.wind.active) flags |= UPDATE_WIND;
    if (this.gravity.active) flags |= UPDATE_GRAVITY;
    if (this.tint.timeline.length > 1) flags |= UPDATE_TINT;
    if (this.spriteMode === 'animated') flags |= UPDATE_SPRITE;
    this.updateFlags = flags;
  }

  _activateParticle(index) {
    const random = this.random;
    // Array.random: MathUtils.random(0, size - 1).
    const sprite = this.spriteMode === 'random' ? this.sprites[random.nextInt(this.sprites.length)] : this.sprites[0];
    const particle = this.particles[index];
    particle.set(sprite);
    const percent = f32p(this.durationTimer / this.durationNow), flags = this.updateFlags;
    if (this.life.independent) this._generateLifeValues();
    if (this.lifeOffset.independent) this._generateLifeOffsetValues();
    particle.currentLife = particle.life = this.lifeNow + (f32p(this.lifeDiff * this.life.getScale(percent)) | 0);
    if (this.velocity.active) {
      particle.velocity = this.velocity.newLowValue(random);
      particle.velocityDiff = this.velocity.newHighValue(random);
      if (!this.velocity.relative) particle.velocityDiff = f32p(particle.velocityDiff - particle.velocity);
    }
    particle.angle = this.angle.newLowValue(random);
    particle.angleDiff = this.angle.newHighValue(random);
    if (!this.angle.relative) particle.angleDiff = f32p(particle.angleDiff - particle.angle);
    let angle = 0;
    if ((flags & UPDATE_ANGLE) === 0) {
      angle = f32p(particle.angle + f32p(particle.angleDiff * this.angle.getScale(0)));
      particle.angle = angle;
      particle.angleCos = cosDeg(angle);
      particle.angleSin = sinDeg(angle);
    }
    const spriteWidth = sprite.width, spriteHeight = sprite.height;
    particle.xScale = f32p(this.xScale.newLowValue(random) / spriteWidth);
    particle.xScaleDiff = f32p(this.xScale.newHighValue(random) / spriteWidth);
    if (!this.xScale.relative) particle.xScaleDiff = f32p(particle.xScaleDiff - particle.xScale);
    if (this.yScale.active) {
      particle.yScale = f32p(this.yScale.newLowValue(random) / spriteHeight);
      particle.yScaleDiff = f32p(this.yScale.newHighValue(random) / spriteHeight);
      if (!this.yScale.relative) particle.yScaleDiff = f32p(particle.yScaleDiff - particle.yScale);
      particle.scaleX = f32p(particle.xScale + f32p(particle.xScaleDiff * this.xScale.getScale(0)));
      particle.scaleY = f32p(particle.yScale + f32p(particle.yScaleDiff * this.yScale.getScale(0)));
    } else {
      particle.scaleX = particle.scaleY = f32p(particle.xScale + f32p(particle.xScaleDiff * this.xScale.getScale(0)));
    }
    if (this.rotation.active) {
      particle.rotationStart = this.rotation.newLowValue(random);
      particle.rotationDiff = this.rotation.newHighValue(random);
      if (!this.rotation.relative) particle.rotationDiff = f32p(particle.rotationDiff - particle.rotationStart);
      let rotation = f32p(particle.rotationStart + f32p(particle.rotationDiff * this.rotation.getScale(0)));
      if (this.aligned) rotation = f32p(rotation + angle);
      particle.rotation = rotation;
    }
    if (this.wind.active) {
      particle.wind = this.wind.newLowValue(random);
      particle.windDiff = this.wind.newHighValue(random);
      if (!this.wind.relative) particle.windDiff = f32p(particle.windDiff - particle.wind);
    }
    if (this.gravity.active) {
      particle.gravity = this.gravity.newLowValue(random);
      particle.gravityDiff = this.gravity.newHighValue(random);
      if (!this.gravity.relative) particle.gravityDiff = f32p(particle.gravityDiff - particle.gravity);
    }
    const temp = this.tint.getColor(0);
    particle.tint[0] = temp[0];
    particle.tint[1] = temp[1];
    particle.tint[2] = temp[2];
    particle.transparency = this.transparency.newLowValue(random);
    particle.transparencyDiff = f32p(this.transparency.newHighValue(random) - particle.transparency);
    // Spawn.
    let x = this.x;
    if (this.xOffset.active) x = f32p(x + this.xOffset.newLowValue(random));
    let y = this.y;
    if (this.yOffset.active) y = f32p(y + this.yOffset.newLowValue(random));
    const shape = this.spawnShape;
    const width = () => f32p(this.spawnWidthNow + f32p(this.spawnWidthDiff * this.spawnWidth.getScale(percent)));
    const height = () => f32p(this.spawnHeightNow + f32p(this.spawnHeightDiff * this.spawnHeight.getScale(percent)));
    if (shape.shape === 'square') {
      const w = width(), h = height();
      x = f32p(x + f32p(f32p(random.nextFloat() * w) - f32p(w * 0.5)));
      y = f32p(y + f32p(f32p(random.nextFloat() * h) - f32p(h * 0.5)));
    } else if (shape.shape === 'ellipse') {
      const w = width(), h = height();
      const radiusX = f32p(w * 0.5), radiusY = f32p(h * 0.5);
      if (radiusX !== 0 && radiusY !== 0) {
        const scaleY = f32p(radiusX / radiusY);
        if (shape.edges) {
          const spawnAngle = shape.side === 'top' ? -f32p(random.nextFloat() * 179)
            : shape.side === 'bottom' ? f32p(random.nextFloat() * 179) : f32p(random.nextFloat() * 360);
          const cos = cosDeg(spawnAngle), sin = sinDeg(spawnAngle);
          x = f32p(x + f32p(cos * radiusX));
          y = f32p(y + f32p(f32p(sin * radiusX) / scaleY));
          if ((flags & UPDATE_ANGLE) === 0) {
            particle.angle = spawnAngle;
            particle.angleCos = cos;
            particle.angleSin = sin;
          }
        } else {
          const radius2 = f32p(radiusX * radiusX);
          for (;;) {
            const px = f32p(f32p(random.nextFloat() * w) - radiusX), py = f32p(f32p(random.nextFloat() * w) - radiusX);
            if (f32p(f32p(px * px) + f32p(py * py)) <= radius2) {
              x = f32p(x + px);
              y = f32p(y + f32p(py / scaleY));
              break;
            }
          }
        }
      }
    } else if (shape.shape === 'line') {
      const w = width(), h = height();
      if (w !== 0) {
        const lineX = f32p(w * random.nextFloat());
        x = f32p(x + lineX);
        y = f32p(y + f32p(lineX * f32p(h / w)));
      } else y = f32p(y + f32p(h * random.nextFloat()));
    }
    particle.setBounds(f32p(x - f32p(spriteWidth * 0.5)), f32p(y - f32p(spriteHeight * 0.5)), spriteWidth, spriteHeight);
    let offsetTime = f32p(this.lifeOffsetNow + f32p(this.lifeOffsetDiff * this.lifeOffset.getScale(percent))) | 0;
    if (offsetTime > 0) {
      if (offsetTime >= particle.currentLife) offsetTime = particle.currentLife - 1;
      this._updateParticle(particle, f32p(offsetTime / 1000), offsetTime);
    }
  }

  _updateParticle(particle, delta, deltaMillis) {
    const life = particle.currentLife - deltaMillis;
    if (life <= 0) return false;
    particle.currentLife = life;
    const percent = f32p(1 - f32p(particle.currentLife / particle.life)), flags = this.updateFlags;
    if ((flags & UPDATE_SCALE) !== 0) {
      particle.scaleX = f32p(particle.xScale + f32p(particle.xScaleDiff * this.xScale.getScale(percent)));
      particle.scaleY = this.yScale.active
        ? f32p(particle.yScale + f32p(particle.yScaleDiff * this.yScale.getScale(percent))) : particle.scaleX;
    }
    if ((flags & UPDATE_VELOCITY) !== 0) {
      const velocity = f32p(f32p(particle.velocity + f32p(particle.velocityDiff * this.velocity.getScale(percent))) * delta);
      let velocityX, velocityY;
      if ((flags & UPDATE_ANGLE) !== 0) {
        const angle = f32p(particle.angle + f32p(particle.angleDiff * this.angle.getScale(percent)));
        velocityX = f32p(velocity * cosDeg(angle));
        velocityY = f32p(velocity * sinDeg(angle));
        if ((flags & UPDATE_ROTATION) !== 0) {
          let rotation = f32p(particle.rotationStart + f32p(particle.rotationDiff * this.rotation.getScale(percent)));
          if (this.aligned) rotation = f32p(rotation + angle);
          particle.rotation = rotation;
        }
      } else {
        velocityX = f32p(velocity * particle.angleCos);
        velocityY = f32p(velocity * particle.angleSin);
        if (this.aligned || (flags & UPDATE_ROTATION) !== 0) {
          let rotation = f32p(particle.rotationStart + f32p(particle.rotationDiff * this.rotation.getScale(percent)));
          if (this.aligned) rotation = f32p(rotation + particle.angle);
          particle.rotation = rotation;
        }
      }
      if ((flags & UPDATE_WIND) !== 0) {
        velocityX = f32p(velocityX + f32p(f32p(particle.wind + f32p(particle.windDiff * this.wind.getScale(percent))) * delta));
      }
      if ((flags & UPDATE_GRAVITY) !== 0) {
        velocityY = f32p(velocityY
          + f32p(f32p(particle.gravity + f32p(particle.gravityDiff * this.gravity.getScale(percent))) * delta));
      }
      particle.translate(velocityX, velocityY);
    } else if ((flags & UPDATE_ROTATION) !== 0) {
      particle.rotation = f32p(particle.rotationStart + f32p(particle.rotationDiff * this.rotation.getScale(percent)));
    }
    const color = (flags & UPDATE_TINT) !== 0 ? this.tint.getColor(percent) : particle.tint;
    const a = f32p(particle.transparency + f32p(particle.transparencyDiff * this.transparency.getScale(percent)));
    const c = particle.color;
    if (this.premultipliedAlpha) {
      const alphaMultiplier = this.additive ? 0 : 1;
      c[0] = f32p(color[0] * a);
      c[1] = f32p(color[1] * a);
      c[2] = f32p(color[2] * a);
      c[3] = f32p(a * alphaMultiplier);
    } else {
      c[0] = color[0];
      c[1] = color[1];
      c[2] = color[2];
      c[3] = a;
    }
    if ((flags & UPDATE_SPRITE) !== 0) {
      const frame = Math.min(f32p(percent * this.sprites.length) | 0, this.sprites.length - 1);
      if (particle.frame !== frame) {
        const sprite = this.sprites[frame];
        const prevWidth = particle.width, prevHeight = particle.height;
        particle.setRegion(sprite);
        particle.width = sprite.width;
        particle.height = sprite.height;
        particle.originX = sprite.originX;
        particle.originY = sprite.originY;
        particle.translate(f32p(f32p(prevWidth - sprite.width) * 0.5), f32p(f32p(prevHeight - sprite.height) * 0.5));
        particle.frame = frame;
      }
    }
    return true;
  }

  _generateLifeValues() {
    this.lifeNow = this.life.newLowValue(this.random) | 0;
    this.lifeDiff = this.life.newHighValue(this.random) | 0;
    if (!this.life.relative) this.lifeDiff -= this.lifeNow;
  }

  _generateLifeOffsetValues() {
    this.lifeOffsetNow = this.lifeOffset.active ? this.lifeOffset.newLowValue(this.random) | 0 : 0;
    this.lifeOffsetDiff = this.lifeOffset.newHighValue(this.random) | 0;
    if (!this.lifeOffset.relative) this.lifeOffsetDiff -= this.lifeOffsetNow;
  }

  // setPosition: an attached emitter carries its live particles.
  setPosition(x, y) {
    if (this.attached) {
      const xAmount = f32p(x - this.x), yAmount = f32p(y - this.y);
      for (let i = 0; i < this.active.length; i++) if (this.active[i]) this.particles[i].translate(xAmount, yAmount);
    }
    this.x = x;
    this.y = y;
  }

  // The blend its .p asks for (ParticleEmitter.draw).
  blend() {
    return this.premultipliedAlpha ? 'premultiplied' : this.additive ? 'additive' : 'normal';
  }
}

// A PARTICLES layer's effect: core's ParallaxParticles. Its emitters stay at (0, 0) and its particles live in the
// layer's own space, in page units: draw moves them to where the layer is drawn, once per tile, and multiplies the
// page's tint and fade into each one, as the batch colour does an image. A finished effect starts again.
export class PlaxParticles {
  // `emitters`: readEffect's, for this layer; its images, regions of `atlas`. Starts it and warms it up.
  constructor(emitters, atlas) {
    this.emitters = emitters.map(spec => {
      const sprites = spec.imagePaths.map(path => {
        const file = path.replace(/\\/g, '/').replace(/^.*\//, ''), name = file.replace(/\.[^.]*$/, '');
        const region = atlas?.findRegion(name);
        if (!region) throw new Error(`plax: the atlas has no region '${name}' for the particle emitter '${spec.name}'`);
        if (region.rotate || region.width !== region.originalWidth || region.height !== region.originalHeight) {
          console.warn(`plax: the particle image '${name}' is packed rotated or stripped, which is not supported: drawn as is`);
        }
        return spriteOf(region);
      });
      return new PlaxEmitter(spec, sprites, PlaxParticles.random);
    });
    this.minX = this.minY = this.maxX = this.maxY = 0;
    this.empty = true;
    for (const e of this.emitters) e.start();
    this.warmUp();
  }

  // Runs it on in steps of WARM_UP_STEP for as long as its slowest emitter takes to start and its longest-lived
  // particle lives: a page shows its snow already falling, not falling in.
  warmUp() {
    for (let left = this.warmUpSeconds(); left > 0; left = f32p(left - PlaxParticles.WARM_UP_STEP)) {
      this.update(Math.min(PlaxParticles.WARM_UP_STEP, left));
    }
  }

  warmUpSeconds() {
    let longest = 0;
    for (const e of this.emitters) {
      const delay = e.delay.isActive() ? e.delay.lowMax : 0, life = e.life;
      const lowest = Math.max(life.lowMin, life.lowMax);
      const highest = f32p(Math.max(life.highMin, life.highMax) + (life.relative ? lowest : 0));
      longest = Math.max(longest, f32p(f32p(delay + Math.max(lowest, highest)) / 1000));
    }
    return Math.min(longest, PlaxParticles.MAX_WARM_UP);
  }

  // `delta` seconds on, starting it again once finished, and where its particles are now.
  update(delta) {
    for (const e of this.emitters) e.update(delta);
    if (this.emitters.every(e => e.isComplete())) for (const e of this.emitters) e.reset(true);
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const e of this.emitters) {
      for (let i = 0; i < e.active.length; i++) {
        if (!e.active[i]) continue;
        const v = e.particles[i].getVertices();
        for (let k = 0; k < 8; k += 2) {
          minX = Math.min(minX, v[k]);
          maxX = Math.max(maxX, v[k]);
          minY = Math.min(minY, v[k + 1]);
          maxY = Math.max(maxY, v[k + 1]);
        }
      }
    }
    this.empty = !(minX <= maxX && minY <= maxY);
    if (!this.empty) Object.assign(this, { minX, minY, maxX, maxY });
  }

  // Moves every live particle, not the emitters: a VIEW-anchored effect's particles drift as its layer scrolls.
  translateParticles(x, y) {
    if (x === 0 && y === 0) return;
    x = f32p(x);
    y = f32p(y);
    for (const e of this.emitters) {
      const attached = e.attached;
      e.attached = true;
      e.setPosition(f32p(e.x + x), f32p(e.y + y));
      e.attached = false;
      e.setPosition(f32p(e.x - x), f32p(e.y - y));
      e.attached = attached;
    }
  }

  // Into `sprites` (the layer's ParticleQuads), moved by (x, y) page units, their colours multiplied by `tint`; `ppu`
  // and `screenH` turn page units, y up, into screen pixels, y down. Each emitter blends as its .p asks.
  // `turn`: round a planet, every vertex turned as one block about its pivot (PlaxPlanet.turn, PlanetBatch.draw).
  draw(sprites, x, y, tint, ppu, screenH, turn = null) {
    const xy = new Float32Array(8), uv = new Float32Array(8), rgba = new Float32Array(4);
    for (const e of this.emitters) {
      const blend = e.blend(), fade = e.premultipliedAlpha ? tint[3] : 1;
      for (let i = 0; i < e.active.length; i++) {
        if (!e.active[i]) continue;
        const p = e.particles[i], v = p.getVertices(), c = p.color;
        for (let k = 0; k < 8; k += 2) {
          let vx = f32p(v[k] + x), vy = f32p(v[k + 1] + y);
          if (turn) {
            const dx = f32p(vx - turn.pivotX), dy = f32p(vy - turn.pivotY);
            vx = f32p(f32p(turn.toX + f32p(dx * turn.cos)) - f32p(dy * turn.sin));
            vy = f32p(f32p(turn.toY + f32p(dx * turn.sin)) + f32p(dy * turn.cos));
          }
          xy[k] = vx * ppu;
          xy[k + 1] = screenH - vy * ppu;
        }
        // Bottom left, top left, top right, bottom right, as Sprite's vertices.
        uv.set([p.u, p.v2, p.u, p.v, p.u2, p.v, p.u2, p.v2]);
        rgba[0] = packed(c[0] * tint[0] * fade);
        rgba[1] = packed(c[1] * tint[1] * fade);
        rgba[2] = packed(c[2] * tint[2] * fade);
        rgba[3] = packedAlpha(c[3] * tint[3]);
        sprites.add(p.texture, blend, xy, uv, rgba);
      }
    }
  }

  // A .p file's emitters, to build each PARTICLES layer's PlaxParticles from (loadParticles reads them).
  static read(text) {
    return readEffect(text);
  }
}
PlaxParticles.WARM_UP_STEP = f32p(1 / 30);
PlaxParticles.MAX_WARM_UP = 30;
// MathUtils.random: what every effect draws its particles from. setSeed(javaHashCode(id)) repeats a run.
PlaxParticles.random = new RandomXS128();
// Java's String.hashCode, a seed as engines/rounds' Round.seedRandom makes one from a scene's id.
PlaxParticles.hashCode = text => {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
  return h;
};

// Fetches the .p file each PARTICLES layer of `page` names (particlesLibgdx), from `atlas`'s folder, as libGDX finds
// them (WholePage_Model.loadParticles): before setPage or transfertInto with that page. A layer naming none, or one
// not found, draws nothing.
export async function loadParticles(page, atlas) {
  page.effects ??= {};
  for (const m of page.layers) {
    const file = m.kind === 'PARTICLES' ? m.particlesLibgdx : '';
    if (!file || file in page.effects) continue;
    page.effects[file] = PlaxParticles.read(await (await fetchOk(join(atlas.dir, file))).text());
  }
}

// A PARTICLES layer's effect, built from the .p its page names (loadParticles fetched it), started and warmed up; null,
// said in the log as libGDX says it, when it names none.
function particlesOf(model, page, atlas) {
  const file = model.particlesLibgdx, name = model.name ? `'${model.name}' ` : '';
  if (!file) {
    console.log(`plax: the particle layer ${name}of ${page.atlasName} draws nothing: the page names no libGDX effect (.p)`);
    return null;
  }
  const emitters = page.effects?.[file];
  if (!emitters) {
    console.warn(`plax: the particle layer ${name}of ${page.atlasName} draws nothing: ${file} was not loaded (loadParticles)`);
    return null;
  }
  return new PlaxParticles(emitters, atlas);
}

// The quads of a PARTICLES layer: one Mesh per run of one texture and one blend, each particle its own corners and
// colour (libGDX's SpriteBatch.draw(texture, vertices)), in screen pixels.
class ParticleQuads {
  constructor() {
    this.group = new THREE.Group();
    this.runs = [];
    this.used = 0;
    this.effect = null;
    this.uniforms = null;
  }

  setEffect() {}

  begin(order) {
    for (const run of this.runs) run.count = 0;
    this.used = 0;
    this.group.renderOrder = order;
  }

  setColor() {}

  add(texture, blend, xy, uv, rgba) {
    let run = this.used ? this.runs[this.used - 1] : null;
    if (!run || run.texture !== texture || run.blend !== blend) {
      run = this.runs[this.used] ??= new ParticleRun(this.runs.length);
      run.set(texture, blend);
      if (run.mesh.parent !== this.group) this.group.add(run.mesh);
      this.used++;
    }
    run.add(xy, uv, rgba);
  }

  end() {
    this.runs.forEach((run, i) => run.end(i < this.used));
  }

  dispose() {
    for (const run of this.runs) run.dispose();
    this.runs = [];
  }
}

class ParticleRun {
  constructor(order) {
    this.texture = null;
    this.blend = null;
    this.count = 0;
    this.capacity = 0;
    this.geometry = new THREE.BufferGeometry();
    this.material = new THREE.MeshBasicMaterial({ ...LAYER_BLENDING, vertexColors: true, toneMapped: false });
    this.mesh = new THREE.Mesh(this.geometry, this.material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = order;
    this._grow(64);
  }

  set(texture, blend) {
    if (this.texture !== texture) {
      this.texture = texture;
      this.material.map = texture;
      this.material.needsUpdate = true;
    }
    if (this.blend !== blend) {
      this.blend = blend;
      [this.material.blendSrc, this.material.blendDst] = PARTICLE_BLENDS[blend];
    }
  }

  _grow(capacity) {
    const old = this.capacity ? { position: this.position, uv: this.uv, color: this.color } : null;
    this.capacity = capacity;
    this.position = new Float32Array(capacity * 12);
    this.uv = new Float32Array(capacity * 8);
    this.color = new Float32Array(capacity * 16);
    if (old) {
      this.position.set(old.position);
      this.uv.set(old.uv);
      this.color.set(old.color);
    }
    const index = new Uint32Array(capacity * 6);
    for (let i = 0; i < capacity; i++) index.set([0, 1, 2, 0, 2, 3].map(v => v + i * 4), i * 6);
    this.geometry.dispose();
    this.geometry = new THREE.BufferGeometry();
    this.geometry.setAttribute('position', new THREE.BufferAttribute(this.position, 3));
    this.geometry.setAttribute('uv', new THREE.BufferAttribute(this.uv, 2));
    this.geometry.setAttribute('color', new THREE.BufferAttribute(this.color, 4));
    this.geometry.setIndex(new THREE.BufferAttribute(index, 1));
    this.mesh.geometry = this.geometry;
  }

  add(xy, uv, rgba) {
    if (this.count === this.capacity) this._grow(this.capacity * 2);
    const i = this.count++;
    for (let k = 0; k < 4; k++) {
      this.position.set([xy[2 * k], xy[2 * k + 1], 0], i * 12 + k * 3);
      this.color.set(rgba, i * 16 + k * 4);
    }
    this.uv.set(uv, i * 8);
  }

  end(used) {
    this.geometry.setDrawRange(0, this.count * 6);
    const a = this.geometry.attributes;
    a.position.needsUpdate = a.uv.needsUpdate = a.color.needsUpdate = true;
    this.mesh.visible = used && this.count > 0;
  }

  dispose() {
    this.geometry.dispose();
    this.material.dispose();
  }
}

// The quads of one layer, refilled every update: libGDX's SpriteBatch, one colour. A run of quads on one texture is
// one Mesh; a quad on another texture starts the next run, as SpriteBatch flushes on a texture switch, so a sequence
// whose segments sit on several atlas pages keeps its draw order. The runs are drawn in order inside `group`.
class Quads {
  constructor() {
    this.group = new THREE.Group();
    this.runs = [];
    this.used = 0;
    this.color = new THREE.Color(1, 1, 1);
    this.opacity = 1;
    // Drawn through a PlaxEffects shader (setEffect): its name, and the uniforms every run shares; null for none.
    this.effect = null;
    this.uniforms = null;
    // What the next quads' colour is multiplied by, [r, g, b] (a tower's darkness, PlaxTower.draw); null for 1.
    this.shade = null;
  }

  // Draws every run through PlaxEffects' `effect` ('WAVE', 'FOG', 'SWELL', 'MIRROR' or 'PLAIN'), its numbers in `uniforms`,
  // set once a frame for the whole layer, as one Godot canvas's material; null, back to no shader.
  setEffect(effect) {
    this.effect = effect;
    this.uniforms = effect ? PlaxEffects.uniforms() : null;
    for (const run of this.runs) {
      this.group.remove(run.mesh);
      run.dispose();
    }
    this.runs = [];
    this.used = 0;
  }

  begin(order) {
    for (const run of this.runs) run.count = 0;
    this.used = 0;
    this.group.renderOrder = order;
  }

  setColor(rgb, alpha) {
    this.color.setRGB(rgb[0], rgb[1], rgb[2]);
    this.opacity = alpha;
    if (this.uniforms) this.uniforms.tint.value.set(rgb[0], rgb[1], rgb[2], alpha);
  }

  // A quad at (x, y) in screen pixels, y down, `src` the texels drawn of `texture`, mirrored by fx, fy.
  add(texture, x, y, w, h, src, fx, fy) {
    this._runOf(texture).add(x, y, w, h, src, fx, fy, this.shade);
  }

  // A quad of any shape: its four corners `xy` ([x0, y0, .. x3, y3], screen pixels, y down) and their `uv`, as
  // SpriteBatch.draw(texture, vertices) takes them (PlaxPlanet.draw).
  addCorners(texture, xy, uv) {
    this._runOf(texture).addCorners(xy, uv, this.shade);
  }

  _runOf(texture) {
    let run = this.used ? this.runs[this.used - 1] : null;
    if (!run || run.texture !== texture) {
      run = this.runs[this.used] ??= new QuadRun(this.runs.length, this.effect, this.uniforms);
      if (run.texture !== texture) run.setTexture(texture);
      if (!this.effect) {
        run.material.color.copy(this.color);
        run.material.opacity = this.opacity;
      }
      if (run.mesh.parent !== this.group) this.group.add(run.mesh);
      this.used++;
    }
    return run;
  }

  // An atlas region (atlas.findRegion) stretched over a box at (x, y) in screen pixels, y down, as Batch.draw(region,
  // x, y, width, height): what a layer hook draws with, in the layer's colour. With `place` ([x, y]) and `angle`
  // (radians, clockwise on screen), the box is in a frame moved to `place` and turned by `angle`: round a planet, a hook
  // stands what it draws where bg.planetPlaceAt puts its foot, turned by bg.planetAngleAt (r347).
  drawRegion(region, x, y, w, h, place = null, angle = 0) {
    const src = { x: region.x, y: region.y, w: region.width, h: region.height };
    if (!place) {
      this.add(region.page.texture, x, y, w, h, src, false, false);
      return;
    }
    const image = region.page.texture.image, cos = Math.cos(angle), sin = Math.sin(angle);
    const at = (px, py) => [place[0] + px * cos - py * sin, place[1] + px * sin + py * cos];
    const u0 = src.x / image.width, u1 = (src.x + src.w) / image.width;
    const v0 = src.y / image.height, v1 = (src.y + src.h) / image.height;
    this.addCorners(region.page.texture, [...at(x, y), ...at(x + w, y), ...at(x + w, y + h), ...at(x, y + h)],
      [u0, v0, u1, v0, u1, v1, u0, v1]);
  }

  end() {
    this.runs.forEach((run, i) => run.end(i < this.used));
  }

  dispose() {
    for (const run of this.runs) run.dispose();
    this.runs = [];
  }
}

// SpriteBatch's SRC_ALPHA, ONE_MINUS_SRC_ALPHA on the colour; the alpha stays 1, the background opaque: an alpha under
// 1 is un-premultiplied when the canvas is shown or saved, and brightens what is half transparent. Every layer
// material, shader or not, draws with it.
const LAYER_BLENDING = {
  transparent: true, depthTest: false, depthWrite: false, side: THREE.DoubleSide,
  blending: THREE.CustomBlending, blendEquation: THREE.AddEquation,
  blendSrc: THREE.SrcAlphaFactor, blendDst: THREE.OneMinusSrcAlphaFactor,
  blendSrcAlpha: THREE.OneFactor, blendDstAlpha: THREE.OneMinusSrcAlphaFactor,
};

// One run of Quads: one texture, one draw; through `effect`'s shader, its `uniforms` shared with the layer's other
// runs, when it has one.
class QuadRun {
  constructor(order, effect = null, uniforms = null) {
    this.texture = null;
    this.count = 0;
    this.capacity = 0;
    this.geometry = new THREE.BufferGeometry();
    this.material = effect ? PlaxEffects.material(effect, uniforms)
      : new THREE.MeshBasicMaterial({ ...LAYER_BLENDING, vertexColors: true, toneMapped: false });
    this.mesh = new THREE.Mesh(this.geometry, this.material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = order;
    this._grow(4);
  }

  setTexture(texture) {
    this.texture = texture;
    if (this.material.isShaderMaterial) {
      this.material.uniforms.tex.value = texture;
      return;
    }
    this.material.map = texture;
    this.material.needsUpdate = true;
  }

  _grow(capacity) {
    this.capacity = capacity;
    this.position = new Float32Array(capacity * 12);
    this.uv = new Float32Array(capacity * 8);
    // Each corner's shade (Quads.shade): 1 but on a tower's pieces.
    this.shade = new Float32Array(capacity * 12).fill(1);
    const index = new Uint32Array(capacity * 6);
    for (let i = 0; i < capacity; i++) index.set([0, 1, 2, 0, 2, 3].map(v => v + i * 4), i * 6);
    this.geometry.dispose();
    this.geometry = new THREE.BufferGeometry();
    this.geometry.setAttribute('position', new THREE.BufferAttribute(this.position, 3));
    this.geometry.setAttribute('uv', new THREE.BufferAttribute(this.uv, 2));
    this.geometry.setAttribute('color', new THREE.BufferAttribute(this.shade, 3));
    this.geometry.setIndex(new THREE.BufferAttribute(index, 1));
    this.mesh.geometry = this.geometry;
  }

  add(x, y, w, h, src, fx, fy, shade = null) {
    const image = this.texture.image;
    let u0 = src.x / image.width, u1 = (src.x + src.w) / image.width;
    let v0 = src.y / image.height, v1 = (src.y + src.h) / image.height;
    if (fx) [u0, u1] = [u1, u0];
    if (fy) [v0, v1] = [v1, v0];
    this.addCorners([x, y, x + w, y, x + w, y + h, x, y + h], [u0, v0, u1, v0, u1, v1, u0, v1], shade);
  }

  addCorners(xy, uv, shade = null) {
    if (this.count === this.capacity) {
      const pos = this.position, uvs = this.uv, shades = this.shade;
      this._grow(this.capacity * 2);
      this.position.set(pos);
      this.uv.set(uvs);
      this.shade.set(shades);
    }
    const i = this.count++;
    for (let k = 0; k < 4; k++) {
      this.position[i * 12 + k * 3] = xy[k * 2];
      this.position[i * 12 + k * 3 + 1] = xy[k * 2 + 1];
      this.position[i * 12 + k * 3 + 2] = 0;
      this.shade[i * 12 + k * 3] = shade ? shade[0] : 1;
      this.shade[i * 12 + k * 3 + 1] = shade ? shade[1] : 1;
      this.shade[i * 12 + k * 3 + 2] = shade ? shade[2] : 1;
    }
    this.uv.set(uv, i * 8);
  }

  end(used) {
    this.geometry.setDrawRange(0, this.count * 6);
    this.geometry.attributes.position.needsUpdate = this.geometry.attributes.uv.needsUpdate = true;
    this.geometry.attributes.color.needsUpdate = true;
    this.mesh.visible = used && this.count > 0;
  }

  dispose() {
    this.geometry.dispose();
    this.material.dispose();
  }
}

function gradientMesh() {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(24), 3));
  g.setAttribute('color', new THREE.BufferAttribute(new Float32Array(24), 3));
  g.setIndex([0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7]);
  const mesh = new THREE.Mesh(g, new THREE.MeshBasicMaterial({
    vertexColors: true, depthTest: false, depthWrite: false, side: THREE.DoubleSide, blending: THREE.NoBlending,
    toneMapped: false,
  }));
  mesh.frustumCulled = false;
  mesh.renderOrder = 0;
  return mesh;
}

// A colour channel as libGDX packs it into a vertex: 8 bits, truncated.
const packed = c => Math.floor(255 * Math.min(1, Math.max(0, c))) / 255;
// An alpha as libGDX packs it: 8 bits, truncated, the lowest dropped (NumberUtils.intToFloatColor), and stretched back
// to 0..1 by the vertex shader's 255 / 254.
const packedAlpha = a => (Math.floor(255 * Math.min(1, Math.max(0, a))) & 0xfe) / 254;
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const join = (dir, file) => !dir ? file : dir.endsWith('/') ? dir + file : dir + '/' + file;
