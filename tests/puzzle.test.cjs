'use strict';
// Execute the real game logic in a browser stub. Rendering is deliberately
// excluded; no test hooks or exported game state are added to the live page.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { webcrypto } = require('node:crypto');

const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const source = html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
new vm.Script(source);
const startup = 'buildUI();wireTabs();initGL();live();initialize(n);describeDifficulty();bgStart();';
assert.ok(source.includes(startup), 'Game startup must be stubbed before testing');

function harness(savedPreference) {
  const elements = new Map();
  const storage = new Map(savedPreference === undefined ? [] : [['hexDifficultyV2', savedPreference]]);
  function element(id) {
    if (!elements.has(id)) elements.set(id, {
      textContent: '', value: '', disabled: false, hidden: false,
      style: { setProperty() {} }, classList: { add() {}, remove() {} },
      getContext() { return {}; }, setAttribute() {}, addEventListener() {}
    });
    return elements.get(id);
  }
  const box = {
    crypto: webcrypto, Uint32Array, console,
    document: { getElementById: element, addEventListener() {} },
    localStorage: { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) },
    innerWidth: 1200, innerHeight: 800, addEventListener() {},
    setInterval() { return 1; }, clearInterval() {}, setTimeout, clearTimeout, Date, performance
  };
  const hooks = `
    draw=()=>{};
    let edges=null;
    const originalRotate=rotateCluster,originalFlip=flipCluster;
    rotateCluster=(c,dir)=>{const from=snapshot();originalRotate(c,dir);if(edges&&!edges.has(snapshot()))edges.set(snapshot(),{from,move:{key:c.key,kind:'rotate',dir}})};
    flipCluster=c=>{const from=snapshot();originalFlip(c);if(edges&&!edges.has(snapshot()))edges.set(snapshot(),{from,move:{key:c.key,kind:'flip',dir:0}})};
    globalThis.api={
      init:(mode,size)=>{difficulty=mode;initialize(size)},
      load:()=>initialize(n), shuffle,play,candidates,snapshot,inverseMove,bindPivot,queueTap,acceptMotion,
      motionState:()=>({enabled:motion.enabled,active:motion.active,available:motion.available,x:pm.x,y:pm.y}),
      captureInput:()=>{const actions=[];play=(...args)=>actions.push(args);return actions},
      solution:()=>sol.map(m=>({...m})), initial:()=>initial,
      click:id=>E(id).onclick(),
      preference:v=>E('difficulty').onchange({target:{value:v}}),
      state:()=>({pieces:clonePieces(pieces),history:history.map(h=>({s:h.s})),hintsUsed,undosUsed,mode:roundRules.label,roundActive,size:n,selected:difficulty}),
      captureShuffle:()=>{edges=new Map();shuffle();const result=edges;edges=null;return result},
      apply:m=>{const c=candidates().find(c=>c.key===m.key);if(!c)throw Error('Illegal inverse');if(m.kind==='flip')flipCluster(c);else rotateCluster(c,m.dir)},
      pending:()=>{pendingClick={timer:setTimeout(()=>{play(candidates()[0].key)},20)}},
      busy:v=>animT=v?{}:null
    };
  `;
  vm.runInNewContext(source.replace(startup, hooks), box);
  return { api: box.api, elements, storage };
}

function checkPieces(api, size) {
  const pieces = api.state().pieces;
  const ids = pieces.flatMap(p => p.ids);
  assert.equal(pieces.length, 3 * size * size);
  assert.equal(ids.length, 6 * size * size);
  assert.equal(new Set(ids).size, ids.length, 'Every triangle belongs to exactly one piece');
}

(async () => {
  const { api: a, elements } = harness();
  let boards = 0, maxGenerationMs = 0;
  for (const mode of ['relaxed', 'normal', 'hard', 'challenge']) {
    for (const size of [2, 3, 4]) {
      for (let repeat = 0; repeat < 8; repeat++) {
        a.init(mode, size);
        const goal = a.snapshot(), started = performance.now();
        const edges = mode === 'challenge' ? a.captureShuffle() : (a.shuffle(), null);
        maxGenerationMs = Math.max(maxGenerationMs, performance.now() - started);
        assert.notEqual(a.snapshot(), goal, 'Scramble must not be solved');
        checkPieces(a, size);
        boards++;
        if (mode === 'challenge') {
          assert.equal(a.solution().length, 0, 'Challenge retains no solution');
          // A test-only graph observes actual legal generation steps, including
          // rejected candidates. Production never keeps this graph or a route.
          let state = a.snapshot(), route = [], seen = new Set();
          while (state !== goal) {
            assert.ok(!seen.has(state), 'Observed reverse route must terminate');
            seen.add(state);
            const edge = edges.get(state);
            assert.ok(edge, 'Selected Challenge board must be reachable');
            route.push(a.inverseMove(edge.move));
            state = edge.from;
          }
          for (const move of route) a.apply(move);
          assert.equal(a.snapshot(), goal);
          a.play(a.candidates()[0].key);
          assert.equal(a.state().history.length, 0);
          assert.equal(a.solution().length, 0);
        } else {
          const route = a.solution();
          assert.ok(route.length);
          for (const move of route.reverse()) a.apply(a.inverseMove(move));
          assert.equal(a.snapshot(), goal, 'Hint route must reach the exact goal');
        }
        checkPieces(a, size);
      }
    }
  }

  for (const size of [2, 3, 4]) {
    a.init('relaxed', size); a.shuffle();
    for (let i = 0; i < 20; i++) {
      const pivots = a.candidates();
      a.play(pivots[i % pivots.length].key, i % 3 ? 'rotate' : 'flip', i % 2 ? 1 : -1);
    }
    for (const move of a.solution().reverse()) a.apply(a.inverseMove(move));
    assert.equal(a.snapshot(), a.initial(), 'Mixed user moves must remain reversible');
  }

  a.init('hard', 3); a.shuffle(); a.click('hintBtn');
  assert.equal(a.state().hintsUsed, 1);
  assert.equal(a.solution().length, 0, 'Last hint erases the solution');
  a.click('hintBtn'); assert.equal(a.state().hintsUsed, 1);
  for (let i = 0; i < 7; i++) a.play(a.candidates()[0].key);
  for (let i = 0; i < 8; i++) a.click('undo');
  assert.equal(a.state().undosUsed, 5);
  assert.equal(a.solution().length, 0);
  assert.ok(a.state().history.every(h => h.s.length === 0), 'Undo cannot restore a used hint route');

  a.init('normal', 3); a.shuffle(); a.click('hintBtn'); a.click('hintBtn');
  assert.equal(a.state().hintsUsed, 1, 'Repeated hint on an unchanged board is not charged twice');
  // A flip changes piece pairings; rotating a monochrome cluster can leave
  // the visible state unchanged and correctly refuse another hint.
  for (let i = 0; i < 7; i++) { a.play(a.candidates()[0].key, 'flip'); a.click('hintBtn'); }
  assert.equal(a.state().hintsUsed, 5);
  assert.equal(a.solution().length, 0);

  a.init('challenge', 3); a.shuffle(); a.preference('relaxed');
  assert.equal(a.state().mode, 'Challenge', 'Pending settings cannot relax an existing round');
  a.click('hintBtn'); a.play(a.candidates()[0].key); a.click('undo');
  assert.equal(a.state().hintsUsed, 0);
  assert.equal(a.state().undosUsed, 0);
  assert.equal(a.state().history.length, 0);
  a.shuffle(); assert.equal(a.state().mode, 'Relaxed');

  a.init('normal', 4); a.shuffle();
  const before = a.snapshot();
  a.busy(true); a.click('hintBtn'); a.click('undo'); a.play(a.candidates()[0].key);
  assert.equal(a.snapshot(), before, 'Input during animation must not change state or budgets');
  assert.equal(a.state().hintsUsed, 0);
  a.busy(false); a.pending(); a.click('reset');
  await new Promise(resolve => setTimeout(resolve, 40));
  assert.equal(a.snapshot(), a.initial(), 'Reset cancels deferred click input');
  assert.equal(a.state().roundActive, false);
  assert.equal(a.state().history.length, 0);
  assert.equal(elements.get('hintBtn').disabled, true);

  // Clockwise-only gestures still solve a counterclockwise hint in two taps.
  a.init('relaxed', 3);
  const center = a.candidates().find(p => Math.hypot(p.pt.x, p.pt.y) < 1);
  a.play(center.key, 'rotate', 1); a.click('hintBtn');
  assert.equal(elements.get('flag').textContent, 'TAP ×2 ↻');
  a.play(center.key, 'rotate', 1); a.play(center.key, 'rotate', 1);
  assert.equal(a.snapshot(), a.initial());
  assert.equal(a.solution().length, 0, 'Three clockwise moves cancel in the hint trail');
  for (const size of [2, 3, 4]) {
    a.init('relaxed', size); a.shuffle();
    let guard = 2000;
    while (a.snapshot() !== a.initial() && guard-- > 0) {
      const route = a.solution(); assert.ok(route.length);
      const move = a.inverseMove(route[route.length - 1]);
      if (move.kind === 'flip') a.play(move.key, 'flip');
      else a.play(move.key, 'rotate', 1);
    }
    assert.ok(guard > 0, 'Clockwise and flip route must terminate');
  }

  const beforeMotion = a.snapshot();
  a.acceptMotion({available:true,x:4,y:-3});
  assert.equal(a.motionState().x,1); assert.equal(a.motionState().y,-1);
  assert.equal(a.snapshot(),beforeMotion,'Sensor input must never move a puzzle piece');
  a.acceptMotion({x:NaN,y:0}); assert.equal(a.motionState().x,1);
  elements.get('motionTilt').onchange({target:{checked:false}});
  a.acceptMotion({x:.8,y:.9}); assert.equal(a.motionState().x,0); assert.equal(a.motionState().active,false);

  const gestureHarness = harness();
  const actions = gestureHarness.api.captureInput();
  const listeners = new Map();
  const pivot = {addEventListener:(name,fn)=>listeners.set(name,fn),setPointerCapture(){}};
  gestureHarness.api.bindPivot(pivot,'pivot');
  const fire=(name,extra={})=>listeners.get(name)({preventDefault(){},pointerType:'touch',isPrimary:true,pointerId:1,clientX:20,clientY:20,...extra});
  fire('pointerdown');fire('pointerup');fire('click',{button:0});
  assert.equal(actions.length,0,'A single touch must wait for a possible double tap');
  await new Promise(resolve=>setTimeout(resolve,330));
  assert.deepEqual(JSON.parse(JSON.stringify(actions[0])),['pivot','rotate',1]);
  actions.length=0;
  fire('pointerdown');fire('pointerup');fire('pointerdown');fire('pointerup');
  fire('dblclick');
  await new Promise(resolve=>setTimeout(resolve,330));
  assert.equal(actions.length,1);assert.deepEqual(JSON.parse(JSON.stringify(actions[0])),['pivot','flip']);
  actions.length=0;
  fire('pointerdown');fire('pointermove',{clientX:80});fire('pointerup',{clientX:80});
  await new Promise(resolve=>setTimeout(resolve,330));assert.equal(actions.length,0,'Dragging is not a tap');
  fire('contextmenu');assert.equal(actions.length,0,'Right click cannot rotate counterclockwise');
  fire('keydown',{key:'Enter',shiftKey:true});assert.deepEqual(JSON.parse(JSON.stringify(actions[0])),['pivot','rotate',1]);

  for (const saved of ['broken JSON', 'null', '{"mode":"__proto__","size":999}']) {
    const { api } = harness(saved); api.load();
    assert.equal(api.state().selected, 'normal'); assert.equal(api.state().size, 3);
  }
  const { api: restored } = harness('{"mode":"hard","size":4}'); restored.load();
  assert.equal(restored.state().mode, 'Hard'); assert.equal(restored.state().size, 4);

  console.log(JSON.stringify({ passed: true, boards, modes: 4, sizes: 3, maxGenerationMs: Math.round(maxGenerationMs), checks: ['syntax', 'all-mode reachability', 'piece partition', 'mixed rotate and flip', 'hint limits', 'undo limits', 'solution deletion', 'round isolation', 'animation guard', 'reset cancellation', 'saved preference validation', 'clockwise-only solution', 'touch and ghost-click handling', 'motion clamp and isolation'] }));
})().catch(error => { console.error(error); process.exitCode = 1; });
