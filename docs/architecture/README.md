# Architecture Evidence — Lab 05

Both diagrams describe the code delivered in this repository. See [ADR-002](../ADR-002-client-boundaries.md).

## 1. Application View (ArchiMate)
Model file: [application-view.archimate.xml](application-view.archimate.xml) (Open Exchange Format;
import it in Archi with *File → Import → Open Exchange XML Model* and export the PNG next to it if required).

Simplified rendering of the same view:

```mermaid
flowchart TB
  user([Board user])
  subgraph browser["Node: Web browser"]
    view["BoardView<br/>(Application Component, SVG)"]
    app["BoardApp<br/>(Application Component)"]
    state["BoardState<br/>(Application Component)"]
    client["BoardApiClient<br/>(Application Component)"]
  end
  subgraph server["Node: Spring Boot"]
    rest{{"Board REST API<br/>/api/boards (Application Interface)"}}
    ctrl["BoardRestController"]
    svc["BoardApplicationService"]
    repo["InMemoryBoardRepository"]
  end
  data[("Board / BoardElement<br/>(Data Object)")]

  user -- "gestures" --> view
  view -- "select / move / remove / edit" --> app
  app -- "commands" --> state
  state -- "snapshot" --> view
  app -- "create / load / save" --> client
  client -- "HTTP JSON" --> rest
  rest --- ctrl --> svc --> repo
  repo -. "reads / writes" .-> data
  state -. "reads / writes" .-> data
```

Lab #6 will add a realtime adapter next to `BoardApiClient` that feeds `BoardState`; `BoardView` stays unchanged.

## 2. Class / module diagram

```mermaid
classDiagram
  direction LR
  class BoardApp {
    <<module app.js>>
    -retryAction
    -notice
    +refresh()
    +remote(label, action, onSuccess)
  }
  class BoardApiClient {
    <<module board-api-client.js>>
    +create(name) Board
    +load(id) Board
    +save(board) Board
  }
  class BoardApiError {
    +status
    +code
    +message
  }
  class BoardState {
    <<module board-state.js>>
    +snapshot()
    +setBoard(board)
    +setName(name)
    +select(id)
    +setRemote(status, operation, error)
    +addRectangle()
    +addText()
    +moveSelected(x, y)
    +renameSelected(text)
    +beginConnect()
    +cancelConnect()
    +completeConnect(targetId)
    +removeSelected()
    +toPersistedBoard()
  }
  class BoardView {
    <<module board-view.js>>
    +render(snapshot)
    +on(handlers)
  }
  class Board {
    id
    name
    elements
  }
  class BoardElement {
    id
    type
    x, y, width, height
    text
    sourceId
    targetId
  }
  class BoardRestController {
    <<Spring>>
    POST /api/boards
    GET /api/boards/id
    PUT /api/boards/id
  }
  class BoardApplicationService
  class BoardRepository {
    <<interface>>
  }
  class InMemoryBoardRepository

  BoardApp --> BoardApiClient : uses
  BoardApp --> BoardState : uses
  BoardApp --> BoardView : uses
  BoardApiClient ..> BoardApiError : throws
  BoardApiClient ..> BoardRestController : HTTP JSON
  BoardState o-- Board
  Board "1" *-- "0..*" BoardElement
  BoardView ..> BoardState : renders snapshot
  BoardRestController --> BoardApplicationService
  BoardApplicationService --> BoardRepository
  InMemoryBoardRepository ..|> BoardRepository
```

`BoardApiClient`, `BoardState` and `BoardView` do not depend on each other; only `BoardApp` composes them.
