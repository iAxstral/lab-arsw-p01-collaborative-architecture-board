# ADR-002 — Client boundaries

**Status:** Accepted (Lab #5)

## Context
Lab #5 adds an interactive SVG web client on top of the REST API from Lab #4
(`POST /api/boards`, `GET /api/boards/{id}`, `PUT /api/boards/{id}`). The client must create/load a Board,
add RECTANGLE and TEXT, select/move/delete elements, create CONNECTORs, save through REST and handle
Loading, Success, Error and Retry.

The starter mixed responsibilities: `BoardApp` decided when to connect, the view exposed a `connectTarget`
callback, the state stored a function (`lastAction`) that made `structuredClone` fail, and errors escaped
as unhandled rejections. Lab #6 will add WebSocket/STOMP to the same Board, so the boundaries chosen now
must let a second transport plug in without rewriting the UI or the state.

## Decision
Four modules with one responsibility each and a one-way dependency rule:

| Module | File | Responsibility | Must not |
|---|---|---|---|
| `BoardApp` | `js/app.js` | Composition root. Translates view events and button clicks into state changes and remote calls; owns the remote workflow (Loading/Success/Error/Retry) and the retry closure. | Touch SVG nodes or build URLs. |
| `BoardApiClient` | `js/api/board-api-client.js` | Only module that knows `fetch`, URLs, status codes. Validates ids, maps every failure (HTTP or network) to `BoardApiError{status,code,message}`. Persists with a full `PUT` of the board. | Read or mutate state, touch the DOM. |
| `BoardState` | `js/state/board-state.js` | Single source of truth: board, selection, connect mode, dirty flag, remote status. Domain rules: only shapes move, connectors need two different existing shapes and are never duplicated, deleting a shape deletes its connectors. Snapshots are plain data. | Know the DOM or HTTP. |
| `BoardView` | `js/ui/board-view.js` | Pure projection of a snapshot to SVG; converts pointer/keyboard gestures into events (`select`, `move`, `remove`, `edit`). Drag keeps the grab offset and clamps to the viewBox. | Keep business state or call the API. |

Dependencies: `BoardApp → {BoardApiClient, BoardState, BoardView}`; the other three do not import each other.

Other decisions:
- **Persistence is explicit:** edits are local until *Save* sends the whole Board with `PUT` (no `/move` or `/draw` endpoints). A `dirty` flag warns before discarding changes.
- **Remote status** is data in `BoardState` (`idle|loading|success|error` + operation + error); the retry *closure* lives in `BoardApp` because functions are not state. While `loading`, remote and local actions are disabled and re-entrant calls are ignored.
- **Retry** re-runs the same operation with its original success handler; `save` reads the current state at retry time.
- The backend also enforces that connectors reference existing **non-connector** elements, so the rule does not depend on the client.

## Consequences
- (+) Each module can be tested alone (`BoardState` runs in plain Node; the API client can be mocked).
- (+) Lab #6 can add a realtime adapter next to `BoardApiClient` and feed remote changes through `BoardState` without touching `BoardView`.
- (+) UI states are deterministic: everything rendered derives from one snapshot.
- (−) Whole-board `PUT` means last-writer-wins; concurrent edits will need the consistency strategy planned for Lab #7.
- (−) Re-rendering the whole SVG on every pointer move is simple but not optimal for very large boards.
- (−) Vanilla ES modules with no build step: no type checking and no bundler.

## Trade-off
Alternatives considered:
1. **Single `app.js`** (fewer files, faster to write) — rejected: view, state and HTTP become coupled, retry and Lab #6 realtime are harder to add.
2. **Framework (React/Vue) + store** — rejected: adds a build chain and concepts beyond the lab goal; the four-module split gives the same separation with the platform alone.
3. **Fine-grained REST calls per gesture** — rejected: contradicts the stable API contract and floods the server on every drag step.

We accept full re-render and last-writer-wins in exchange for simplicity and a clear seam for Lab #6.

## Evidence
- Code: `src/main/resources/static/js/{app.js, api/, state/, ui/}`.
- Backend rule + test: `Board.validateConnectors`, `BoardConnectorTest.connectorCannotReferenceAnotherConnector`.
- Diagrams: `docs/architecture/README.md` (Application View and class diagram).
- Manual run: create → add RECTANGLE/TEXT → move → connect → delete → save → reload; `load` with unknown id shows Error + Retry; `PUT` with a dangling connector returns `400 INVALID_INPUT`.
