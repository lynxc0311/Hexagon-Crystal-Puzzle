# Hexagon Crystal Puzzle

A glass crystal puzzle hosted on GitHub Pages. Open `index.html` in a modern browser with WebGL enabled. Three.js is loaded from the existing pinned CDN URL.

## Difficulty settings

Choose **Settings → Difficulty**, then **Play** to start a new puzzle. **Reset** returns to the solved board and applies the selected difficulty. Board size remains independent, with 12, 27 or 48 pieces. Settings are remembered locally. Changing difficulty does not change an active round's assistance allowance.

| Mode | Hints per round | Undo actions per round | Scramble selection |
| --- | --- | --- | --- |
| Relaxed | Unlimited | Unlimited | One short legal walk |
| Normal | 5 | Unlimited | Best of 3 legal walks |
| Hard | 1 | 5 | Best of 6 longer legal walks |
| Challenge | Disabled | Disabled | Best of 9 longer legal walks |

The original target, board layout, glass rendering, rotation, flip, keyboard and touch controls are preserved. Hints highlight a pivot and indicate clockwise, counterclockwise or flip. A repeated request on the same unchanged state does not consume another hint. Undo never refunds assistance already used.

Scrambles use legal reversible moves, reject already visited board states, and rank candidate boards by color displacement and piece pairing changes. This is a difficulty heuristic, not a minimum-move guarantee; a small board can still be simple at a high setting. Random choices use `crypto.getRandomValues` when available.

## Solver resistance

Challenge does not record the scramble solution or user undo history. Assisted modes erase solution traces, including copies in undo history, when the last hint is used. Handler checks enforce the same limits as the disabled buttons.

This is a browser-only game, not a secure competitive verification system. A solver can still inspect or reconstruct visible state, modify local code, or independently search for a solution. These changes remove the built-in hint shortcut at Challenge difficulty; they do not prevent all solvers.

## Checks

Run with Node.js 20 or newer:

```sh
node tests/puzzle.test.cjs
```

The dependency-free test harness exercises the actual inline game logic with rendering stubbed out. It checks 96 scrambles across all modes and sizes, inverse reachability (including Challenge through test-only observation), mixed user moves, hint/undo budgets, route deletion, setting isolation, animation input guards, reset cancellation, and saved settings validation. WebGL rendering still requires browser verification.

GitHub Pages continues to serve the root `index.html`; no build step is required.
