// drawCycle against core's SequenceCycle, as engines/godot/tests/sequence_cycle.gd holds PlaxPage.draw_cycle (r38): the
// first picks SequenceCases.sequenceCycleOfAKnownSeedIsPinned pins, then every cycle of engines/rounds/sequence/picks.json
// (written by tools/r183-sequence/PickTable.java from the JVM's SequenceCycle: edge and random seeds, weights of every
// shape). Then PlaxSequence.draw, as SequenceCases holds ParallaxLayer.drawCycle: at many positions and pads, positive
// and going back, the slots it draws are the ones a walk over every slot finds in view, in order. Logs
// "sequence_cycle: PASS" or FAIL lines, and POSTs them to done. engines/threejs/tools/frame-check.sh opens
//   sequence_cycle.html
// through engines/threejs/tools/run-page.sh whenever it checks engines/rounds/sequence.
import { PlaxBackground, PlaxSequence, drawCycle, sequenceNext } from '../src/plax.js';

const log = [];
const say = line => { log.push(line); console.log(line); };
let failures = 0;

function equal(actual, expected, what) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    failures++;
    say(`FAIL: ${what}: ${JSON.stringify(actual)}, not ${JSON.stringify(expected)}`);
  }
}

// Java's int of a state, as SequenceCases prints it.
const signed = value => value | 0;
const cycle = (seed, weights, length) => Array.from(drawCycle(seed, weights, length));

// Records what PlaxSequence.draw draws instead of drawing it.
class Recorder extends PlaxBackground {
  constructor() { super(); this.drawn = []; }
  _drawRegion(l, x, _y, width) { this.drawn.push([l.id, x, width]); }
}

// A 24-slot sequence of segments 2, 0.75, 1.25 and 3.5 heights wide, its first (the layer) 14 wide: at pads from 3
// down to -12 (the slots going back after the narrowest from -5.25 on), seen through 0..40 from x -120 to 40.
function checkSlotsInView() {
  const bg = new Recorder(), aspects = [2, 0.75, 1.25, 3.5];
  let walks = 0;
  for (const pad of [3, 0, -2, -5, -5.5, -8, -12]) {
    const l = {
      model: { kind: 'SEQUENCE', sequenceSeed: 183, sequenceLength: 24, padX: pad, sizeRatio: 0.35 },
      segments: aspects.map((aspect, id) => ({ id, aspect })), weights: [1, 3, 1, 1], imageW: 2, imageH: 1,
      narrowest: 0.75,
    };
    bg._drawCycleOf(l);
    for (let step = 0; step < 161; step++) {
      const x = -120 + step;
      bg.drawn = [];
      PlaxSequence.draw(bg, l, x, 0, 0, 40, false, false);
      const expected = [];
      for (let slot = 0; slot < l.cycle.length; slot++) {
        const left = x + l.height * l.edges[slot] + slot * pad, width = l.height * aspects[l.cycle[slot]];
        if (left < 40 && left + width > 0) expected.push([l.cycle[slot], left, width]);
      }
      walks++;
      if (JSON.stringify(bg.drawn) !== JSON.stringify(expected)) {
        failures++;
        say(`FAIL: pad ${pad} at x ${x}: drew ${JSON.stringify(bg.drawn)}, not ${JSON.stringify(expected)}`);
        break;
      }
    }
  }
  bg.dispose();
  return walks;
}

async function check() {
  equal(signed(sequenceNext(1)), 270369, 'xorshift32 (13, 17, 5) of 1');
  equal(cycle(42, [1, 2, 3], 12), [0, 1, 1, 2, 0, 2, 0, 2, 2, 2, 2, 2], 'seed 42, weights 1 2 3');
  equal(cycle(-1640531527, [25, 50, 25], 12), [1, 1, 2, 0, 2, 0, 1, 0, 1, 2, 0, 0], 'a negative seed, weights 25 50 25');
  equal(cycle(0, [1, 1], 8), [1, 0, 0, 0, 0, 1, 0, 1], 'seed 0 starts from ZERO_SEED');
  equal(cycle(7, [0, 5, -3], 12), [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1], 'only the weighted segment');
  equal(drawCycle(5, [1], 0).length, 1, 'a cycle holds one slot at least');

  const table = await (await fetch('/engines/rounds/sequence/picks.json')).json();
  for (const [state, next] of table.next) equal(signed(sequenceNext(state >>> 0)), next, `next(${state})`);
  for (const row of table.cycles) {
    equal(cycle(row.seed, row.weights, row.cycle.length), row.cycle, `seed ${row.seed}, weights ${row.weights}`);
  }
  const walks = checkSlotsInView();
  say(`sequence_cycle: ${table.cycles.length} cycles from the JVM, ${walks} views walked, ${failures ? 'FAIL' : 'PASS'}`);
}

check().catch(e => say('FAIL: ' + (e.stack || e)))
  .finally(() => fetch('done', { method: 'POST', body: log.join('\n') + '\n' }));
