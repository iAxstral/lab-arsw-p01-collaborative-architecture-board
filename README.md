# ARSW Collaborative Architecture Board — Lab 05

An interactive board where you create a `Board`, add rectangles and text,
drag them, connect them with connectors, and save the whole board through a
REST API. The backend (Lab 04) is a small hexagonal Spring Boot application;
the Lab 05 client is plain JavaScript ES Modules drawing on native SVG — no
framework and no build step.

## Requirements

- Java 21
- Maven

## Run

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080/>. Boards live in memory
(`InMemoryBoardRepository`), so restarting the server discards them.

## Test

Automated tests:

```bash
mvn test
```

Manual end-to-end flow in the browser:

1. Type a name and click **New**: the assigned `boardId` appears in the id field.
2. Click **+ Rectangle** and **+ Text** to add elements.
3. Drag an element inside the canvas to move it.
4. Select a rectangle or text, click **Connect selected + next**, then click a
   different element: a connector line is drawn between their centers.
5. Select an element (or a connector) and click **Delete selected**. Deleting
   an element also removes the connectors attached to it.
6. Click **Save** (an explicit `PUT`; moves are not autosaved).
7. Reload the page, paste the `boardId` and click **Load**: the board comes back
   as saved.
8. Load an unknown id to see the error state; **Retry** repeats the last failed
   operation (create, load or save).

## Project structure

```text
src/main/java/edu/eci/arsw/collabboard/
  domain/model/                 Board, BoardElement, ElementType (immutable records)
  application/service/          BoardApplicationService (use cases)
  application/port/out/         BoardRepository (output port)
  application/exception/        BoardNotFoundException
  infrastructure/persistence/   InMemoryBoardRepository (adapter)
  infrastructure/web/rest/      BoardRestController, GlobalExceptionHandler, ApiError, request records

src/main/resources/static/
  index.html, css/app.css       static shell
  js/app.js                     BoardApp: the only module wiring state, view and API client
  js/api/board-api-client.js    BoardApiClient: the only place that calls fetch()
  js/state/board-state.js       BoardState: immutable board/selection/connect/remote state
  js/ui/board-view.js           BoardView: pure projection of the state onto SVG

src/test/java/...               service, domain (CONNECTOR) and HTTP contract tests
```

Client dependencies:

```text
BoardApp (app.js)
  |--> BoardApiClient --HTTP/JSON--> REST API (/api/boards)
  |--> BoardState
  `--> BoardView --> SVG / DOM
```

## Known limitation

Once the "connect" mode is started (**Connect selected + next**),
`connectSourceId` cannot be cancelled: it is cleared only by completing the
connection or by creating/loading another board. This comes from the
`BoardState` contract and is recorded as a trade-off in
[ADR-002](docs/ADR-002-client-boundaries.md).

## Documentation

- [ADR-001 — Repository boundary](docs/ADR-001-repository-boundary.md)
- [ADR-002 — Client boundaries](docs/ADR-002-client-boundaries.md)
- [REST API contract](docs/api-contract.md)
- [Architecture diagrams](docs/architecture/README.md)
- [AI usage declaration](docs/AI_USAGE.md)

## Evidence

- ArchiMate application view, Lab 04: [PNG](docs/architecture/application-view-lab4.png) · [source](docs/architecture/application-view-lab4.puml)
- ArchiMate application view, Lab 05: [PNG](docs/architecture/application-view-lab5.png) · [source](docs/architecture/application-view-lab5.puml)
- Class / module diagram: [class-diagram.md](docs/architecture/class-diagram.md)
<!-- PENDING: link the GIF/screenshot of the working client here once it is added to the repo. -->
