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
// Ported: IMAGE layers (decal, sizeRatio, flips, speeds, speedXAtRest, pads, mirror, repeat on X and Y,
// useOriginalSize), SEQUENCE layers, the two gradients, plain cross-fades, tints, zoom (not the dolly), resizing.
// EMPTY layers draw nothing. A page with a layer of another kind (SHADER, PARTICLES, FLOOR, LURKER) fails to load, as
// in libGDX a page from a newer version does: they are ported in the order engines/rounds needs them.
import * as THREE from 'three';

export const WORLD_WIDTH = 40;

// What a layer of a page has when its file leaves a field out (Parallax_Model's defaults, as PlaxPage.LAYER_DEFAULTS).
const LAYER_DEFAULTS = {
  regionName: '', regionPosition: 0, flipX: false, flipY: false,
  parallaxScalingSpeedX: 0, parallaxScalingSpeedY: 0, speedXAtRest: 0, sizeRatio: 1,
  decal_X_Ratio: 0, decal_Y_Ratio: 0, padX: 0, padXFactor: 0, padY: 0, padYFactor: 0,
  mirror: false, kind: 'IMAGE', name: '',
  sequenceSegments: [], sequenceSeed: 0, sequenceLength: 16,
};
// The kinds this reader draws; another fails the page's load. A SEQUENCE layer's cycle is drawn by drawCycle.
const KINDS = ['IMAGE', 'EMPTY', 'SEQUENCE'];
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
    page.layers.push(layer);
  });
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
    return built;
  }

  _buildLayer(model, region, useOriginalSize) {
    const l = measureRegion(region, useOriginalSize);
    l.model = model;
    l.distanceX = l.distanceY = 0;
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
  }

  // ParallaxLayer.act.
  _actLayer(l, delta, speedX, speedY) {
    const m = l.model;
    l.distanceY += -delta * speedY * m.parallaxScalingSpeedY;
    l.distanceX += -delta * (m.speedXAtRest + speedX) * m.parallaxScalingSpeedX;
    // Kept within one tile so the tiling loops stay short and floats stay precise.
    const totalW = l.width + m.padX, totalH = l.height + m.padY;
    if (this._repeatX && totalW > 0) l.distanceX %= totalW;
    if (this._repeatY && totalH > 0) l.distanceY %= totalH;
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
      col.set([...top.slice(0, 3), ...top.slice(0, 3), ...bottom.slice(0, 3), ...bottom.slice(0, 3)], i * 12);
    };
    band(0, 0, topH, tt, tb);
    band(1, h - bottomH, h, bt, bb);
    g.attributes.position.needsUpdate = g.attributes.color.needsUpdate = true;
    this._gradients.visible = !!this.page;
  }

  // The opacity of the page the layer belongs to: during a cross-fade, the incoming page's or the outgoing one's.
  _alphaOf(l) {
    if (!this.transferLayers.length) return 1;
    return l.incoming ? this._newAlpha : this._oldAlpha;
  }

  _drawLayer(l) {
    const alpha = this.tint[3] * this._alphaOf(l);
    if (!(alpha > 0)) return;
    l.quads.setColor(this.tint, alpha);
    this._quads = l.quads;
    // Its own view, at its zoom: tiled across, and drawn into the screen at as many pixels a unit.
    const z = this.zoom;
    this._ppu = this.width / (this.worldWidth * z);
    const m = l.model, viewW = this.worldWidth * z, viewH = this.worldHeight * z;
    // EMPTY: drawn by the game, which this reader has no hook for yet.
    if (m.kind === 'EMPTY') return;
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
  }

  begin(order) {
    for (const run of this.runs) run.count = 0;
    this.used = 0;
    this.group.renderOrder = order;
  }

  setColor(rgb, alpha) {
    this.color.setRGB(rgb[0], rgb[1], rgb[2]);
    this.opacity = alpha;
  }

  // A quad at (x, y) in screen pixels, y down, `src` the texels drawn of `texture`, mirrored by fx, fy.
  add(texture, x, y, w, h, src, fx, fy) {
    let run = this.used ? this.runs[this.used - 1] : null;
    if (!run || run.texture !== texture) {
      run = this.runs[this.used] ??= new QuadRun(this.runs.length);
      if (run.texture !== texture) run.setTexture(texture);
      run.material.color.copy(this.color);
      run.material.opacity = this.opacity;
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

// One run of Quads: one texture, one draw.
class QuadRun {
  constructor(order) {
    this.texture = null;
    this.count = 0;
    this.capacity = 0;
    this.geometry = new THREE.BufferGeometry();
    this.material = new THREE.MeshBasicMaterial({
      transparent: true, depthTest: false, depthWrite: false, side: THREE.DoubleSide,
      // SpriteBatch's SRC_ALPHA, ONE_MINUS_SRC_ALPHA on the colour; the alpha stays 1, the background opaque: an
      // alpha under 1 is un-premultiplied when the canvas is shown or saved, and brightens what is half transparent.
      blending: THREE.CustomBlending, blendEquation: THREE.AddEquation,
      blendSrc: THREE.SrcAlphaFactor, blendDst: THREE.OneMinusSrcAlphaFactor,
      blendSrcAlpha: THREE.OneFactor, blendDstAlpha: THREE.OneMinusSrcAlphaFactor, toneMapped: false,
    });
    this.mesh = new THREE.Mesh(this.geometry, this.material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = order;
    this._grow(4);
  }

  setTexture(texture) {
    this.texture = texture;
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

const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const join = (dir, file) => !dir ? file : dir.endsWith('/') ? dir + file : dir + '/' + file;
