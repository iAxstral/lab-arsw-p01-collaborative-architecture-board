# Class / Module Diagram

Renders natively on GitHub. Scope is limited to what explains the real
dependencies between layers (backend Lab 04 + client Lab 05) — not a full
class inventory. Getters/setters, Spring annotations, DTO validation
annotations and private helpers are intentionally omitted; see
`docs/ADR-001-repository-boundary.md` and `docs/ADR-002-client-boundaries.md`
for the reasoning behind each boundary shown here.

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
