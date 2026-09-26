# Class / Module Diagram

Renders natively on GitHub. Scope is limited to what explains the real
dependencies between layers (backend Lab 04 + client Lab 05 + real-time
Lab 06) — not a full class inventory. Getters/setters, Spring annotations,
DTO validation annotations and private helpers are intentionally omitted;
see `docs/ADR-001-repository-boundary.md`, `docs/ADR-002-client-boundaries.md`
and `docs/ADR-003-rest-vs-realtime.md` for the reasoning behind each boundary
shown here.

```mermaid
classDiagram
    class BoardRestController {
        +create(CreateBoardRequest request, UriComponentsBuilder uriBuilder) ResponseEntity~Board~
        +get(String boardId) Board
        +replace(String boardId, ReplaceBoardRequest request) Board
    }

    class BoardApplicationService {
        +createBoard(String name) Board
        +getBoard(String boardId) Board
        +replaceBoard(String boardId, String name, List~BoardElement~ elements) Board
    }

    class BoardRepository {
        <<interface>>
        +save(Board board) Board
        +findById(String boardId) Optional~Board~
    }

    class InMemoryBoardRepository {
        -Map~String, Board~ boards
        +save(Board board) Board
        +findById(String boardId) Optional~Board~
    }

    class Board {
        <<record>>
        +String id
        +String name
        +List~BoardElement~ elements
    }

    class BoardElement {
        <<record>>
        +String id
        +ElementType type
        +double x
        +double y
        +double width
        +double height
        +String text
        +String sourceId
        +String targetId
    }

    class ElementType {
        <<enumeration>>
        RECTANGLE
        TEXT
        CONNECTOR
    }

    BoardRestController ..> BoardApplicationService : uses
    BoardRestController ..> Board : returns
    BoardApplicationService ..> BoardRepository : depends on (port)
    BoardApplicationService ..> Board : creates / returns
    BoardApplicationService ..> BoardElement : validates (no duplicate ids)
    InMemoryBoardRepository ..|> BoardRepository : implements
    InMemoryBoardRepository ..> Board : stores in Map
    Board *-- BoardElement : contains
    BoardElement --> ElementType : type

    class BoardApiError {
        <<class : extends Error>>
        +int status
        +String code
    }

    class BoardApiClient {
        <<module: board-api-client.js>>
        +create(name) Promise~Board~
        +load(id) Promise~Board~
        +save(board) Promise~Board~
    }

    class BoardState {
        <<module: board-state.js>>
        +snapshot() Snapshot
        +setBoard(next)
        +setName(name)
        +select(id)
        +setRemote(status, lastAction, error)
        +addRectangle() Element
        +addText() Element
        +moveSelected(x, y)
        +beginConnect()
        +completeConnect(targetId) Element
        +removeSelected()
        +toPersistedBoard() Board
    }

    class BoardView {
        <<module: board-view.js>>
        +render(snapshot, hint)
    }

    class AppJs {
        <<module: app.js>>
        +handleCreate()
        +handleLoad()
        +handleSave()
        +handleRetry()
        +handleElementPointerDown(id)
    }

    BoardApiClient ..> BoardApiError : throws
    BoardApiClient ..> BoardRestController : HTTP POST/GET/PUT /api/boards
    AppJs ..> BoardApiClient : create / load / save
    AppJs ..> BoardApiError : instanceof (error message + code)
    AppJs ..> BoardState : mutates + snapshot()
    AppJs ..> BoardView : render(snapshot, hint)

    %% --- Lab 06: real-time collaboration ---

    class BoardEventType {
        <<enumeration>>
        ELEMENT_CREATED
        ELEMENT_MOVED
        ELEMENT_UPDATED
        ELEMENT_DELETED
        CONNECTOR_CREATED
    }

    class BoardEventPayload {
        <<record>>
        +BoardElement element
        +String elementId
        +Double x
        +Double y
    }

    class BoardEvent {
        <<record>>
        +String eventId
        +String boardId
        +BoardEventType type
        +String actorId
        +Instant occurredAt
        +BoardEventPayload payload
    }

    class BoardEventApplicationService {
        +apply(BoardEvent event) BoardEvent
    }

    class BoardWebSocketController {
        +handle(String boardId, BoardEvent event) void
        +onRejectedEvent(Exception exception) void
    }

    class WebSocketConfig {
        <<config>>
        +configureMessageBroker(MessageBrokerRegistry registry) void
        +registerStompEndpoints(StompEndpointRegistry registry) void
    }

    BoardEvent *-- BoardEventPayload : carries
    BoardEvent --> BoardEventType : type
    BoardEventApplicationService ..> BoardEvent : validates / applies
    BoardEventApplicationService ..> BoardRepository : depends on (same port as REST)
    BoardEventApplicationService ..> Board : loads / saves
    BoardEventApplicationService ..> BoardElement : applies the same invariants
    BoardWebSocketController ..> BoardEventApplicationService : apply(event)
    BoardWebSocketController ..> BoardEvent : SEND /app/boards/{boardId}/events\nBROADCAST /topic/boards/{boardId}
    WebSocketConfig ..> BoardWebSocketController : enables @MessageMapping routing

    class BoardEvents {
        <<module: events/board-event.js>>
        +elementCreated(boardId, actorId, element) BoardEvent
        +connectorCreated(boardId, actorId, element) BoardEvent
        +elementMoved(boardId, actorId, elementId, x, y) BoardEvent
        +elementUpdated(boardId, actorId, element) BoardEvent
        +elementDeleted(boardId, actorId, elementId) BoardEvent
    }

    class BoardRealtimeClient {
        <<module: realtime/board-realtime-client.js>>
        +connect(boardId) Promise
        +publish(event) void
        +disconnect() Promise
        +isConnected() boolean
        +boardId() String
    }

    BoardRealtimeClient ..> BoardWebSocketController : STOMP over WebSocket ws://.../ws
    BoardEvents ..> AppJs : builds the event a local action publishes
    AppJs ..> BoardRealtimeClient : connect / publish(event) / onEvent callback
    AppJs ..> BoardEvents : elementCreated / elementMoved / ...
    BoardState ..> BoardEvent : applyEvent(event) — remote transition
```

## Reading notes

- `BoardApiClient ..> BoardRestController` is not a compile-time import (JS
  cannot import a Java class); it represents the real runtime dependency —
  `BoardApiClient` is the only client module whose behavior is defined by
  the wire contract in `docs/api-contract.md`.
- `BoardView` has **no** relationship to `BoardApiClient` or
  `BoardRestController` on purpose: it never calls `fetch` and never
  imports the API client (see ADR-002). Its only dependency is on the
  snapshot shape it receives as a plain argument, which is why it is not
  drawn as depending on `BoardState` either — `AppJs` is the sole module
  that reads `BoardState` and hands the result to `BoardView`.
- `AppJs` lists a representative subset of its `handle*` functions (the
  ones that call `BoardApiClient` plus the one that decides
  select-vs-connect); the remaining handlers
  (`handleAddRectangle`, `handleAddText`, `handleDeleteSelected`,
  `handleConnectClick`, `handleElementDrag`, `handleCanvasPointerDown`,
  `handleRenameChange`) all follow the same `state.*()` → `renderAll()`
  shape already covered by the `AppJs ..> BoardState` relationship.
- `BoardEventApplicationService` depends on the **same** `BoardRepository`
  port `BoardApplicationService` depends on, and enforces the **same**
  `Board`/`BoardElement` invariants — it is a second entry point into the
  same domain, not a parallel one. This is the point of
  `docs/ADR-003-rest-vs-realtime.md`.
- `BoardWebSocketController` mirrors `BoardRestController`: both are thin
  adapters that call an application service and translate the result to
  their own protocol. Neither one calls the other, and neither one contains
  domain rules.
- `BoardRealtimeClient` is the WebSocket/STOMP analogue of `BoardApiClient`:
  it is the only client module that touches `window.Stomp`/`WebSocket`, the
  only one wired to the WebSocket/STOMP interface, and `AppJs` is the only
  module that imports it — `BoardView` and `BoardState` have no relationship
  to it, matching how they have none to `BoardApiClient`.
- `BoardState.applyEvent` is a **pure state transition**: it turns an
  accepted, already-validated `BoardEvent` into a new `board` snapshot (add,
  move, replace or remove an element) with no DOM access. `AppJs` is still
  the only module that calls `renderAll()` afterwards — the STOMP callback
  in `BoardRealtimeClient` never touches the SVG directly, it only invokes
  `onEvent`, which `AppJs` wires to `state.applyEvent(event)` then
  `renderAll()`.
- Client-authored events go the other way: `AppJs` builds a `BoardEvent` with
  `BoardEvents.*` **after** it already applied the change locally through
  `BoardState` (optimistic local update), then calls
  `BoardRealtimeClient.publish(event)`. If the socket is not connected,
  `AppJs` simply skips the publish — collaboration degrades to a
  single-tab Lab 05 experience instead of failing the local edit.