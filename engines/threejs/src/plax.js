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
// ParallaxPageReader.addColorTransfert:
//
//   bg.transfertInto(await loadPage(url), await loadAtlas(atlasUrl), 2)
//   bg.tintTo([1, 0.6, 0.4, 1], 2)
//
// A SEQUENCE layer chains its segments (atlas regions) in a cycle drawn once, when the page is set (PlaxSequence), by
// drawCycle from the page's seed: the same picks as core's SequenceCycle. The cycle is the layer's tile. A game draws
// a new ground each run with its own seed, XORed with each layer's stored one, as ParallaxPageReader.setSequenceSeed:
//
//   bg.setSequenceSeed(seed)                           // Java's int: 32 bits; clearSequenceSeed() goes back
//
// A SHADER layer is drawn as an image layer, through its effect (PlaxEffects, the shaders of core's GdxLayerEffects),
// which moves on act()'s clock. The page's depth fog (fogStrength, fogColor) mixes each IMAGE, SEQUENCE and SHADER
// layer toward the fog's colour, more the slower it scrolls (PlaxEffects.fogOf), through the same shaders.
//
// Ported: IMAGE layers (decal, sizeRatio, flips, speeds, speedXAtRest, pads, mirror, repeat on X and Y,
// useOriginalSize), SEQUENCE layers, SHADER layers (WAVE, FOG, SWELL), the page's depth fog, the two gradients, plain
// cross-fades, tints, zoom (not the dolly), resizing. EMPTY layers draw nothing. A page with a layer of another kind
// (PARTICLES, FLOOR, LURKER) or a MIRROR effect fails to load, as in libGDX a page from a newer version does: they are
// ported in the order engines/rounds needs them. Not yet: the glint, detail levels.
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
  glintStrength: 0,
};
// The kinds this reader draws; another fails the page's load. A SEQUENCE layer's cycle is drawn by drawCycle.
const KINDS = ['IMAGE', 'EMPTY', 'SEQUENCE', 'SHADER'];
// The effects a SHADER layer is drawn through here (PlaxEffects.EFFECTS less MIRROR); another fails the page's load.
const EFFECTS = ['WAVE', 'FOG', 'SWELL'];
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
    pages, regions,
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
    this._topHalfSize = 0.5;
    this._bottomHalfSize = 0.5;
    // The game's seed for the SEQUENCE layers, when _hasSequenceSeed (setSequenceSeed).
    this._sequenceSeed = 0;
    this._hasSequenceSeed = false;
    // The clock the SHADER layers' effects run on: act()'s seconds.
    this._effectTime = 0;
    this._gradients = gradientMesh();
    this.scene.add(this._gradients);
  }

  // Reads a .jplax or .plaxpj, and its atlas from `atlasDir` (the page's folder when not given).
  async load(pageUrl, atlasDir = pageUrl.replace(/[^/]*$/, '')) {
    const page = await loadPage(pageUrl);
    this.setPage(page, await loadAtlas(join(atlasDir, page.atlasName)));
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
  // started during another drops the page that was fading in.
  transfertInto(page, atlas, seconds) {
    if (!this.page) { this.setPage(page, atlas); return; }
    this._drop(this.transferLayers);
    this._resetTransfert();
    const incoming = this._buildLayers(page, atlas);
    if (incoming.length) {
      this.transferPage = page;
      this.transferAtlas = atlas;
      this.transferLayers = incoming;
      for (const l of incoming) l.incoming = true;
      this._syncTransferPositions();
      if (seconds <= 0) this._finishTransfert();
      else this._fadeSpeed = 1 / seconds;
    }
    this._fadeGradients([page.topHalfTop, page.topHalfBottom, page.bottomHalfTop, page.bottomHalfBottom], seconds);
  }

  // Draws every SEQUENCE layer's cycle, on screen and fading in, from `seed` XOR the seed its page stores rather than
  // from the stored one alone: a new ground each run (ParallaxPageReader.setSequenceSeed). `seed` is Java's int: 32 bits.
  setSequenceSeed(seed) {
    this._sequenceSeed = seed | 0;
    this._hasSequenceSeed = true;
    this._drawCycles();
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
    for (const l of this.layers) this._actLayer(l, delta, speedX, speedY);
    if (this.transferLayers.length) {
      for (const l of this.transferLayers) this._actLayer(l, delta, speedX, speedY);
      this._newAlpha = Math.min(1, this._newAlpha + delta * this._fadeSpeed);
      this._oldAlpha = Math.max(0, this._oldAlpha - delta * this._fadeSpeed);
      if (this._newAlpha >= 1) this._finishTransfert();
    }
    if (this._tintElapsed < this._tintDuration) {
      this._tintElapsed = Math.min(this._tintDuration, this._tintElapsed + delta);
      this.tint = lerp(this._tintFrom, this._tintTo, this._tintElapsed / this._tintDuration);
    }
  }

  // Lays the scene out as the background is now: the gradients, then the layers in draw order.
  update() {
    this._drawGradients();
    let order = 1;
    for (const l of this._drawOrder()) {
      l.quads.begin(order++);
      if (this.page) this._drawLayer(l);
      l.quads.end();
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
      this._resetPosition(l);
      this.scene.add(l.quads.group);
      built.push(l);
    }
    // The depth fog of each layer, by its speed against the page's front: what ParallaxPageReader.drawImageLayer reckons.
    const front = PlaxEffects.frontSpeedOf(built);
    for (const l of built) {
      l.haze = l.model.kind === 'EMPTY' ? 0 : PlaxEffects.fogOf(page.fogStrength, front, l.model);
      l.fogColor = page.fogColor.slice(0, 3);
      if (l.model.kind === 'SHADER') l.quads.setEffect(l.model.shaderEffect);
      else if (l.haze > 0 && (l.model.kind === 'IMAGE' || l.model.kind === 'SEQUENCE')) l.quads.setEffect('PLAIN');
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
    l.height = l.model.kind === 'EMPTY' ? this.worldHeight * l.model.sizeRatio
      : l.imageH * (this.worldWidth / l.imageW) * l.model.sizeRatio;
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
    l.distanceY += -delta * speedY * m.parallaxScalingSpeedY;
    l.distanceX += -delta * (m.speedXAtRest + speedX) * m.parallaxScalingSpeedX;
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
  }

  _updateWorld() {
    this.worldHeight = this.worldWidth * this.height / this.width;
  }

  _drop(layers) {
    for (const l of layers) {
      this.scene.remove(l.quads.group);
      l.quads.dispose();
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
    this.layers = this.transferLayers;
    for (const l of this.layers) l.incoming = false;
    this._resetTransfert();
  }

  _resetTransfert() {
    this.transferPage = null;
    this.transferAtlas = null;
    this.transferLayers = [];
    this._newAlpha = 0;
    this._oldAlpha = 1;
  }

  // SquareBackground.transfertInto: both colours of both gradients, alpha included, straight toward the incoming
  // page's.
  _fadeGradients(target, seconds) {
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
    this._gradient = this._gradientFrom.map((from, i) => lerp(from, this._gradientTo[i], progress));
    if (progress >= 1) this._gradientFading = false;
  }

  // During a cross-fade, slot by slot from the front, the outgoing layer then the incoming one (ParallaxPageReader.draw).
  _drawOrder() {
    const total = Math.max(this.layers.length, this.transferLayers.length);
    const oldOffset = total - this.layers.length, newOffset = total - this.transferLayers.length;
    const order = [];
    for (let slot = 0; slot < total; slot++) {
      if (slot >= oldOffset) order.push(this.layers[slot - oldOffset]);
      if (slot >= newOffset) order.push(this.transferLayers[slot - newOffset]);
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
  // one has a fog (ParallaxPageReader.beginDraw).
  _pageFog() {
    return this.page?.fogStrength > 0 || (this.transferLayers.length > 0 && this.transferPage?.fogStrength > 0);
  }

  // The opacity of the page the layer belongs to: during a cross-fade, the incoming page's or the outgoing one's.
  _alphaOf(l) {
    if (!this.transferLayers.length) return 1;
    return l.incoming ? this._newAlpha : this._oldAlpha;
  }

  _drawLayer(l) {
    const alpha = this.tint[3] * this._alphaOf(l);
    if (!(alpha > 0)) return;
    // As libGDX packs the batch colour (Color.toFloatBits): the alpha to 254 levels, and, but through the page fog's
    // shader (ParallaxPageReader.drawImageLayer: a page with a fog, a layer without an effect), the tint to 8 bits.
    // That shader takes the tint as a uniform and the layer's fog in the batch colour's red: the fog is cut instead.
    const pageFog = this._pageFog() && l.model.kind !== 'SHADER';
    l.quads.setColor(pageFog ? this.tint : this.tint.map(packed), packedAlpha(alpha));
    this._quads = l.quads;
    // Its own view, at its zoom: tiled across, and drawn into the screen at as many pixels a unit.
    const z = this.zoom;
    this._ppu = this.width / (this.worldWidth * z);
    const m = l.model, viewW = this.worldWidth * z, viewH = this.worldHeight * z;
    // EMPTY: drawn by the game, which this reader has no hook for yet.
    if (m.kind === 'EMPTY') return;
    const u = l.quads.uniforms;
    if (u) {
      u.view.value.set(this.width, this.height);
      if (m.kind === 'SHADER') {
        PlaxEffects.apply(u, l, this._effectTime, viewW, viewH, 0, 0, [this._repeatX ? 1 : 0, this._repeatY ? 1 : 0]);
      }
      u.haze.value = pageFog ? packed(l.haze) : l.haze;
      u.fog_color.value.set(...l.fogColor);
    }
    this._tile(l, l.distanceX, l.distanceY, this._repeatX, this._repeatY, false, viewW, viewH);
    if (m.mirror && this._repeatX !== this._repeatY) {
      // A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
      if (this._repeatX) this._tile(l, l.distanceX, l.distanceY + m.padY + l.height, true, false, true, viewW, viewH);
      else this._tile(l, l.distanceX + m.padX + l.width, l.distanceY, false, true, true, viewW, viewH);
    }
  }

  // The layer at (x, y) plus every repetition, on the requested axes, that intersects the view
  // (x 0..viewW, y 0..viewH).
  _tile(l, x, y, onX, onY, mirror, viewW, viewH) {
    const { width, height } = l;
    const sequence = l.model.kind === 'SEQUENCE';
    // A SEQUENCE layer whose pads outweigh its segments is no wider than 0, its slots still drawn: once, below.
    if ((width <= 0 && !sequence) || height <= 0) return;
    // What is drawn of a tile, from its corner: the box, or a SEQUENCE layer's slots (ParallaxLayer.getCycleLeft/Right).
    const left = sequence ? PlaxSequence.cycleLeft(l) : 0;
    const right = sequence ? PlaxSequence.cycleRight(l) : width;
    // A step <= 0 (a negative padding larger than the image) can't tile: the layer is drawn once.
    const stepX = width + l.model.padX, stepY = height + l.model.padY;
    const tileX = onX && stepX > 0, tileY = onY && stepY > 0;
    const startX = tileX ? x - Math.ceil((x + right) / stepX) * stepX : x;
    const startY = tileY ? y - Math.ceil((y + height) / stepY) * stepY : y;
    const countX = tileX ? Math.ceil((viewW - left - startX) / stepX) + 1 : 1;
    const countY = tileY ? Math.ceil((viewH - startY) / stepY) + 1 : 1;
    for (let row = 0; row < countY; row++) {
      const drawY = startY + row * stepY;
      if (drawY + height <= 0 || drawY >= viewH) continue;
      for (let column = 0; column < countX; column++) {
        const drawX = startX + column * stepX;
        if (drawX + right <= 0 || drawX + left >= viewW) continue;
        let fx = l.model.flipX, fy = l.model.flipY;
        // Mirrored: flipped vertically when tiling on X, horizontally when tiling on Y.
        if (mirror) { if (onX) fy = !fy; else fx = !fx; }
        if (sequence) PlaxSequence.draw(this, l, drawX, drawY, 0, viewW, fx, fy);
        else this._drawRegion(l, drawX, drawY, width, height, fx, fy);
      }
    }
  }

  // The packed image of `l` (a layer, or a sequence's segment) in a box at (x, y); a flip mirrors where it sits in the
  // box too. Into the quads of the layer being drawn.
  _drawRegion(l, x, y, width, height, fx, fy) {
    const drawW = width * l.packedW, drawH = height * l.packedH;
    const left = x + width * (fx ? 1 - l.trimLeft - l.packedW : l.trimLeft);
    const bottom = y + height * (fy ? 1 - l.trimBottom - l.packedH : l.trimBottom);
    const ppu = this._ppu, top = this.height - (bottom + drawH) * ppu;
    this._quads.add(l.region.page.texture, left * ppu, top, drawW * ppu, drawH * ppu, l.src, fx, fy);
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
void main() {
	vUv = uv;
	vScreen = position.xy;
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
varying vec2 vUv;
varying vec2 vScreen;
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
	gl_FragColor = hazed(tint * image, SCREEN_UV);
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
	vec4 color = tint * image;
	color.a *= 1.0 - effect.x * n;
	gl_FragColor = hazed(color, SCREEN_UV);
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
	gl_FragColor = hazed(tint * image, SCREEN_UV);
}
`,
  // PLAIN: no effect, the depth fog only.
  PLAIN: `void main() {
	vec2 SCREEN_UV = vScreen / view;
	gl_FragColor = hazed(tint * texture2D(tex, vUv), SCREEN_UV);
}
`,
};

export const PlaxEffects = {
  // Enum_ShaderEffect.FOG_PERIOD: how many wavelengths FOG's patches run before they repeat, SWELL's waves too.
  FOG_PERIOD: 8,
  // Enum_ShaderEffect.FOG_PERIOD_Y: how many wavelengths they run up before they repeat (r262).
  FOG_PERIOD_Y: 10,

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
    };
  },

  // A material for one run of a layer drawn through `effect` ('PLAIN' for a fogged one), its texture its own, its
  // other numbers `uniforms`, the layer's.
  material(effect, uniforms) {
    return new THREE.ShaderMaterial({
      ...LAYER_BLENDING, uniforms: { ...uniforms, tex: { value: null } },
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

  // ParallaxLayer.glints: a SHADER layer with a positive glint strength and wavelength. Its period counts here; the
  // glint itself is not drawn yet (r44).
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
    u.region.value.set(src.x / image.width, src.y / image.height, (src.x + src.w) / image.width,
      (src.y + src.h) / image.height);
    u.size.value.set(l.width * l.packedW, l.height * l.packedH);
    const on = model.shaderWavelength > 0;
    let amplitude = model.shaderAmplitude;
    if (model.shaderEffect === 'FOG') amplitude = Math.min(1, Math.max(0, amplitude));
    u.effect.value.set(on ? amplitude : 0, on ? model.shaderWavelength : 1, on ? PlaxEffects.phase(model, seconds) : 0);
    u.across.value.set(viewW, left + PlaxEffects.effectStartX(l), model.flipX ? -1 : 1);
    u.up.value.set(viewH, floorY + PlaxEffects.effectStartY(l), model.flipY ? -1 : 1);
    u.wrap.value.set(wrap[0], wrap[1]);
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

  // ParallaxPageReader.frontSpeedOf: the fastest speed ratio X of `page` (PlaxBackground's layers), as a magnitude.
  frontSpeedOf(page) {
    let front = 0;
    for (const l of page) front = Math.max(front, Math.abs(l.model.parallaxScalingSpeedX));
    return front;
  },
};

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
  }

  // Draws every run through PlaxEffects' `effect` ('WAVE', 'FOG', 'SWELL' or 'PLAIN'), its numbers in `uniforms`,
  // set once a frame for the whole layer, as one Godot canvas's material.
  setEffect(effect) {
    this.effect = effect;
    this.uniforms = PlaxEffects.uniforms();
    for (const run of this.runs) run.dispose();
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
    run.add(x, y, w, h, src, fx, fy);
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
      : new THREE.MeshBasicMaterial({ ...LAYER_BLENDING, toneMapped: false });
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
    const index = new Uint32Array(capacity * 6);
    for (let i = 0; i < capacity; i++) index.set([0, 1, 2, 0, 2, 3].map(v => v + i * 4), i * 6);
    this.geometry.dispose();
    this.geometry = new THREE.BufferGeometry();
    this.geometry.setAttribute('position', new THREE.BufferAttribute(this.position, 3));
    this.geometry.setAttribute('uv', new THREE.BufferAttribute(this.uv, 2));
    this.geometry.setIndex(new THREE.BufferAttribute(index, 1));
    this.mesh.geometry = this.geometry;
  }

  add(x, y, w, h, src, fx, fy) {
    if (this.count === this.capacity) {
      const pos = this.position, uv = this.uv;
      this._grow(this.capacity * 2);
      this.position.set(pos);
      this.uv.set(uv);
    }
    const image = this.texture.image, i = this.count++;
    let u0 = src.x / image.width, u1 = (src.x + src.w) / image.width;
    let v0 = src.y / image.height, v1 = (src.y + src.h) / image.height;
    if (fx) [u0, u1] = [u1, u0];
    if (fy) [v0, v1] = [v1, v0];
    this.position.set([x, y, 0, x + w, y, 0, x + w, y + h, 0, x, y + h, 0], i * 12);
    this.uv.set([u0, v0, u1, v0, u1, v1, u0, v1], i * 8);
  }

  end(used) {
    this.geometry.setDrawRange(0, this.count * 6);
    this.geometry.attributes.position.needsUpdate = this.geometry.attributes.uv.needsUpdate = true;
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
const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const join = (dir, file) => !dir ? file : dir.endsWith('/') ? dir + file : dir + '/' + file;
