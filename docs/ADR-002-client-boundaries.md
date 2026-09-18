# ADR-002 — Client Boundaries

## Status
Accepted

## Context
Lab 05 adds an interactive SVG client that creates/loads/saves boards through
the REST API delivered in Lab 04 (`docs/api-contract.md`), and drags/connects
elements entirely on the client before an explicit Save. We need to decide:

1. Where the only `fetch()` call in the client lives, and what shape it hands
   back to callers — so "no fetch outside the HTTP access module" is an
   actual, checkable property of the code, not just a convention.
2. How to represent client-side board/selection/connect/remote-request state
   so it can be rendered deterministically, with no hidden side effects.
3. How the SVG rendering code can react to user interaction (drag, click,
   connect) without depending on `BoardApiClient` and without being allowed
   to mutate `BoardState` on its own.
4. Which single module is allowed to decide "what triggers what" — e.g.
   whether a pointer-down on a shape means *select it* or *complete a
   pending connector* — so that decision is not duplicated or, worse,
   made inconsistently in two places.

## Decision
Four modules, three of which are only ever consumed by the fourth.

**`BoardApiClient`** (`static/js/api/board-api-client.js`) is a plain object
with three async methods, `create(name)`, `load(id)`, `save(board)`, each
wrapping exactly one `fetch()` call against `/api/boards` (POST),
`/api/boards/{id}` (GET) and `/api/boards/{id}` (PUT). A private `parse()`
helper is the single place that turns a non-OK HTTP response into a thrown
`BoardApiError(status, code, message)`, read directly from the `ApiError`
JSON shape produced by `GlobalExceptionHandler` (`code`, `message`). `load`
also throws a `BoardApiError(400, 'INVALID_INPUT', …)` synchronously before
ever calling `fetch` when `id` is blank — the only validation duplicated
client-side, and only to avoid a network round trip for input that is
obviously invalid, not to replace backend validation. This module has no DOM
reference and no state; `fetch` appears in exactly these three call sites.

**`BoardState`** (`static/js/state/board-state.js`) is a factory,
`createBoardState()`, closing over `board {id, name, elements}`,
`selectedId`, `connectSourceId` and `remote {status, lastAction, error}`. It
exposes `snapshot()` — a `structuredClone`, the only read path — and
imperative mutators: `setBoard`, `setName`, `select`, `setRemote`,
`addRectangle`, `addText`, `moveSelected`, `beginConnect`/`completeConnect`,
`removeSelected`, `toPersistedBoard`. Every mutator replaces `board` with a
new object (`{...board, …}`, `.map`, `.filter`) instead of mutating in
place. It has no `fetch`, no DOM reference, and no import of
`BoardApiClient` or `board-view.js`.

**`BoardView`** (`static/js/ui/board-view.js`) is `createBoardView(svg,
messageEl, retryBtn, handlers)`, returning a single `render(snapshot, hint)`
function — the only place this module touches the DOM. `render` rebuilds
the `<svg>` children from `snapshot.board.elements` (RECTANGLE/TEXT as a
`<g>` wrapping `<rect>`/`<text>`; CONNECTOR as a `<line>` between the
centers of the source/target elements looked up in a local `Map`, never as
a shape of its own), and separately drives `messageEl`/`retryBtn` from
`snapshot.remote`. It never imports `board-api-client.js` or
`board-state.js`; the only way it talks to the outside world is by calling
the three functions it was given in `handlers`
(`onElementPointerDown`, `onElementDrag`, `onCanvasPointerDown`).

**`app.js`** imports all three of the above and is the only module that
does. It queries the DOM once for every control declared in `index.html`,
builds `state` and `view`, and owns every `addEventListener` in the client.
Its `handle*` functions are the only code that calls both a `state` mutator
and `renderAll()` in the same place, and the only three
(`handleCreate`, `handleLoad`, `handleSave`) that call `BoardApiClient`.
`app.js` is also the only module that interprets `connectSourceId`:
`handleElementPointerDown` reads `snapshot.connectSourceId` to decide
whether a pointer-down on a shape means `state.select(id)` or
`state.completeConnect(id)` — a decision `board-view.js` deliberately does
not make, since it only ever reports "pointer went down on element X"
through `onElementPointerDown`. `handleRetry` reads `remote.lastAction` and
re-invokes the matching `handle*` function, which re-reads the *current*
DOM input values rather than caching stale arguments from the failed
attempt.

## Positive consequences
- `fetch` exists in exactly one file, grep-verifiable:
  `grep -rn "fetch(" src/main/resources/static/js` returns only
  `board-api-client.js`'s three call sites.
- `board-view.js` can be exercised or replaced (e.g. swapped for a
  `<canvas>` renderer) by feeding it snapshots and asserting on `handlers`
  calls, with no running server and no `BoardState` instance — it imports
  neither.
- `board-state.js` has no `document`/`window`/`svg` reference, so
  `createBoardState()` and its methods can run in a plain Node/Jest
  environment with no jsdom.
- Because every state mutation goes through a `state.*` call and every
  visible effect goes through `view.render(state.snapshot(), hint)`,
  `app.js`'s `renderAll()` is the single, searchable place where "state
  changed → screen updated" happens — `board-view.js` exposes only
  `render`, so there is no code path that updates the DOM without it.
- Loading/success/error/retry is centralized in `state.remote` plus
  `app.js`'s `handleRetry`; adding a fourth remote action later means one
  more `case` in `handleRetry`, not a new UI concept.

## Trade-off
- `board-view.js` fully rebuilds the `<svg>` subtree on every render,
  including every `pointermove` while dragging
  (`handleElementDrag` → `state.moveSelected` → `renderAll()`). Simple and
  correct for the handful of elements a lab board has; it would not scale
  to hundreds of elements without a diffing strategy. Accepted because the
  assignment's scope is a single interactive board, not a dense canvas.
- The guard that a `CONNECTOR` cannot be used as a connect source or target
  lives in `app.js` (`isConnectable`), not in `board-state.js`. This keeps
  `board-state.js` exactly as delivered, but the invariant is enforced only
  at the orchestration layer: `completeConnect` itself does not check
  element types, so a caller of `BoardState` that bypassed `app.js` could
  still create a connector pointing at another connector.
- `board-state.js` exposes no way to cancel an in-progress
  `connectSourceId` other than completing it via `completeConnect` or
  discarding *all* local edits via `setBoard`. `app.js` does not work
  around this — doing so would mean either changing `board-state.js` (out
  of scope for this ADR) or re-implementing connect state outside it — so a
  stuck "connect mode" can only be resolved by completing the connection or
  by loading/creating a board.

## Evidence / validation
- `grep -rn "fetch(" src/main/resources/static/js` → 3 matches, all inside
  `board-api-client.js`.
- `grep -rn "BoardApiClient\|board-api-client" src/main/resources/static/js/ui src/main/resources/static/js/state`
  → no matches: neither `board-view.js` nor `board-state.js` reference the
  API client.
- `grep -n "^import" src/main/resources/static/js/app.js` → the only file
  importing all three of `board-api-client.js`, `board-state.js` and
  `ui/board-view.js`.
- Manual end-to-end run (`mvn spring-boot:run` plus a real browser): create
  → boardId assigned and shown; add rectangle/text; drag (clamped to the
  SVG viewBox); connect (a line between centers, not a shape); delete
  (cascades to remove connectors referencing the deleted element); save
  (PUT) → reload the page → load recovers the identical board; forcing
  `GET /api/boards/does-not-exist` surfaces
  `"Board not found: does-not-exist (BOARD_NOT_FOUND)"` with a working
  Retry that re-reads the corrected `boardId` input on click.
