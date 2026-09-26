# ADR-003 — REST and WebSocket/STOMP coexistence

## Status
Accepted

## Context
Lab 06 asks two browsers loading the same `boardId` to share a collaborative
session: creating, moving, connecting or deleting an element in one browser
must reach the other through STOMP/WebSocket, while the initial load and the
snapshot recovery still go through the REST API delivered in Lab 04/05
(`docs/api-contract.md`). We need to decide:

1. Whether WebSocket replaces REST as the way clients read and write a
   `Board`, or whether the two transports coexist — and if they coexist,
   which one owns the truth.
2. Where a `BoardEvent` arriving over STOMP gets validated and applied, so
   the invariants a `Board`/`BoardElement` must satisfy are not duplicated
   or, worse, loosened for the real-time path.
3. Whether the STOMP endpoint is allowed to touch `BoardRepository`
   directly, or must go through an application service, mirroring how
   `BoardRestController` is forbidden from touching persistence directly
   (ADR-001).
4. How a client that just sent an invalid or out-of-order event finds out,
   given that STOMP over the simple broker has no request/response cycle
   the way HTTP does.

## Decision
REST and WebSocket/STOMP are two **entry points into the same application
and the same stored `Board`**, not two competing sources of truth.

**Server side.** `BoardRestController` (`/api/boards`) and
`BoardWebSocketController` (`/app/boards/{boardId}/events` in,
`/topic/boards/{boardId}` out) are both thin adapters. Neither talks to
`BoardRepository` directly. `BoardRestController` delegates to
`BoardApplicationService`; `BoardWebSocketController` delegates to
`BoardEventApplicationService`, a **new, separate** application service —
not a reuse of `BoardApplicationService`'s methods — because the unit of
work is different: REST use cases operate on a whole `Board` (`createBoard`,
`getBoard`, `replaceBoard`), while the WebSocket path applies one incremental
`BoardEvent` (`ELEMENT_CREATED`, `ELEMENT_MOVED`, `ELEMENT_UPDATED`,
`ELEMENT_DELETED`, `CONNECTOR_CREATED`) as a read-modify-write against the
current `Board`. Both services are constructor-injected with the **same**
`BoardRepository` port defined in ADR-001, so there is exactly one place a
`Board` is stored regardless of which transport changed it, and both
services enforce the invariants that already live in `Board` and
`BoardElement` (duplicate ids, connector endpoints existing/distinct/not
themselves connectors, and so on) — `BoardEventApplicationService` does not
re-derive a looser version of those rules for the real-time path.

`BoardWebSocketController.handle` first checks that the destination
`{boardId}` matches `event.boardId()`, then calls
`BoardEventApplicationService.apply(event)`, and **only if that call returns
normally** does it `SimpMessagingTemplate.convertAndSend` the (possibly
normalized) event to `/topic/boards/{boardId}`. A `BoardNotFoundException`
or `IllegalArgumentException` thrown by `apply` is caught by a
`@MessageExceptionHandler` that logs `"Rejected board event: <reason>"` and
returns nothing: STOMP's simple broker has no reply channel comparable to an
HTTP response, so a rejected event is silently dropped from the sender's
point of view — see `docs/event-contract.md` for the full list of rejection
reasons. This mirrors `GlobalExceptionHandler` on the REST side (reject,
report, keep the Board unchanged) using the mechanism STOMP actually offers,
not a WebSocket-shaped attempt to imitate an HTTP status code.

**Client side.** `BoardApiClient` (REST) and `BoardRealtimeClient`
(WebSocket/STOMP) are two separate modules — see ADR-002 for why
`BoardApiClient` is the only module allowed to call `fetch`. Load/save and
snapshot recovery always go through `BoardApiClient`; `app.js` is the only
module that imports `BoardRealtimeClient`, and being connected live is
optional and explicit (`Connect live` / `Disconnect` buttons, independent
`liveStatus` state), not implied by having a board loaded. Live collaboration
is additive on top of the Lab 05 client, not a replacement for it: a browser
that never presses `Connect live` still creates, loads, saves, drags,
connects and deletes exactly as it did in Lab 05.

## Positive consequences
- One authoritative store. `BoardEventApplicationService` and
  `BoardApplicationService` are two callers of the same `BoardRepository`
  port, so there is never a "REST view" and a "WebSocket view" of a
  `Board` that could disagree — the next `GET /api/boards/{id}` after a
  STOMP-only session sees every accepted event, and `docs/AI_USAGE.md`
  aside, no new persistence code was needed for Lab 06.
- The Board/BoardElement invariants are defined once. A connector whose
  `sourceId` does not exist is rejected the same way whether the client
  used `PUT /api/boards/{id}` or sent a `CONNECTOR_CREATED` event — nobody
  had to remember to copy a validation rule into a second place.
- Because `BoardWebSocketController` only broadcasts what
  `BoardEventApplicationService.apply` returned, a burst of accepted and
  rejected events never desynchronizes subscribers: every subscriber to
  `/topic/boards/{boardId}` sees the exact same sequence of accepted
  events, in the same order the server accepted them, because there is a
  single broadcast call per accepted event and the simple broker preserves
  publish order per destination.
- Protocol concerns stay out of the domain: `Board` and `BoardElement` have
  no notion of "event", "topic" or "actor" — `BoardEvent` lives entirely in
  `application.event`, so a future transport (e.g. Server-Sent Events)
  could reuse `BoardEventApplicationService` without touching the domain.
- Real-time collaboration degrades gracefully: `publishLocal` in `app.js`
  is a no-op when `realtime.isConnected()` is false, so every button keeps
  working exactly as it did in Lab 05 for a browser that is not connected
  live, or whose connection drops mid-session.

## Trade-off
- Two application services now read/write the same `Board` through
  `BoardRepository.save`, which is a plain, unsynchronized read-modify-write
  on both paths. Lab 06's own scope explicitly excludes solving concurrent
  writes (see "Fuera de alcance" in the assignment); this ADR does not
  claim REST and WebSocket writes are safe when they race each other — only
  that they observe and mutate one consistent store when they do not race,
  and that the ordering problem is visible and named
  (`docs/event-contract.md`, "Known limitation") rather than hidden behind
  premature locking. Lab 07 is where this is addressed.
- `BoardEventApplicationService` intentionally does **not** reuse
  `BoardApplicationService`'s methods, so the two services share the port
  and the invariants but not code: `ELEMENT_MOVED`'s "load, replace one
  element, save" logic is not expressed as a call into
  `BoardApplicationService.replaceBoard`. This avoids forcing an
  event-shaped operation through a whole-Board-replace API designed for
  REST's PUT semantics, at the cost of two services that both know how to
  turn a change into a `repository.save(new Board(...))` call.
- A rejected STOMP event produces no acknowledgement to its sender beyond a
  server log line — the assignment's own contract (`docs/event-contract.md`)
  accepts this as the behavior of a simple broker with no reply channel, but
  it does mean a client that mis-sends an event currently has no way to
  learn *why* nothing happened except by reading server logs, unlike the
  REST path where `ApiError` always carries a reason back to the caller.
- `Connect live` is a manual, per-tab action instead of an implicit
  subscription on load. This was chosen so "is this tab receiving live
  updates" is always an observable, explicit piece of UI state
  (`liveStatus`) instead of something inferred from whether a board is
  loaded — the cost is one extra click per tab in the demonstration script.

## Evidence / validation
- `BoardEventApplicationServiceTest` exercises `apply` for each
  `BoardEventType` directly against `InMemoryBoardRepository`, with no
  Spring context and no socket — confirming the event rules do not depend
  on STOMP at all.
- `BoardWebSocketControllerTest` opens real STOMP sessions
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `WebSocketStompClient`) and asserts, over the wire: an accepted event is
  applied to the stored `Board` *and* broadcast
  (`acceptedEventIsAppliedAndBroadcastToSubscribers`); a rejected event is
  **not** broadcast and the `Board` is left untouched
  (`rejectedEventIsNotBroadcastAndTheSessionKeepsWorking`); an event whose
  `payload`/destination `boardId` mismatch is dropped
  (`eventWhoseBoardIdDiffersFromTheDestinationIsNotBroadcast`); an event for
  an unknown board is dropped (`eventForAnUnknownBoardIsNotBroadcast`); a
  malformed event does not break the session, so the next valid event from
  the same client still gets through
  (`malformedEventIsRejectedWithoutBreakingTheSession`); and — the
  criterion this ADR is most directly about — a subscriber of a different
  `boardId` never receives another board's events, verified by subscribing
  to two topics on the same session and asserting the foreign queue stays
  empty.
- `BoardEventContractTest` asserts `BoardEvent`'s compact constructor
  rejects a blank `boardId`/`actorId`, independent of any transport.
- Manual end-to-end run: three browser tabs, two on the same `boardId`
  connected live and one on a different `boardId`; creating, moving,
  connecting and deleting in one of the first two tabs reaches the other
  without a reload, and never reaches the third; reloading a tab still
  recovers the current snapshot through `GET /api/boards/{id}`, confirming
  REST remains the source of truth for "what does this board look like
  right now" even after a live session.
- `grep -rn "BoardRepository" src/main/java/edu/eci/arsw/collabboard/application/service`
  → only `BoardApplicationService` and `BoardEventApplicationService`
  depend on the port, and both depend on the interface, never on
  `InMemoryBoardRepository` — the same DIP boundary from ADR-001 holds for
  the new service.