// Prints what the three.js reader's parser (plax.js parseAtlas) reads of each atlas named, in AtlasList.java's
// words: atlas-check.sh compares it with libGDX's (r567). plax.js imports three, which node does not resolve, so
// parseAtlas is cut out of its source and run alone: it reads text only.
//   node atlas_list.mjs PLAX_JS ATLAS...
import { readFileSync } from 'node:fs';

const [plaxPath, ...atlases] = process.argv.slice(2);
const source = readFileSync(plaxPath, 'utf8');
const start = source.indexOf('function parseAtlas(');
const end = source.indexOf('\n}\n', start);
if (start < 0 || end < 0) throw new Error(`no parseAtlas in ${plaxPath}`);
const parseAtlas = new Function(`${source.slice(start, end + 2)}\nreturn parseAtlas;`)();
for (const path of atlases) {
  console.log(`== ${path}`);
  for (const r of parseAtlas(readFileSync(path, 'utf8'), '').regions) {
    console.log(`${r.name} at ${r.x},${r.y} size ${r.width}x${r.height} orig ${r.originalWidth}x${r.originalHeight}`
      + ` offset ${Math.trunc(r.offsetX)},${Math.trunc(r.offsetY)} rotate ${r.rotate}`);
  }
}
