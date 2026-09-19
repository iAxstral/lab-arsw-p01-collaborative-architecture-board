# ARSW Collaborative Architecture Board — Lab 05

Interactive SVG web client over the REST API from Lab #4.

## Features
- Create and load a Board (`POST` / `GET /api/boards`).
- Add RECTANGLE and TEXT; select, drag, edit text (double-click) and delete (button or Delete key).
- Create CONNECTORs: select a shape → *Connect selected + next* → click the target.
- Save the complete Board with `PUT /api/boards/{id}`.
- Remote states: Loading, Success, Error with Retry; unsaved-changes indicator.

## Client boundaries
```text
BoardApp (app.js)
  |--> BoardApiClient (api/board-api-client.js) --> REST
  |--> BoardState     (state/board-state.js)
  `--> BoardView      (ui/board-view.js)        --> SVG/DOM
```
Details: [ADR-002](docs/ADR-002-client-boundaries.md) · [API contract](docs/api-contract.md) ·
[Architecture diagrams](docs/architecture/README.md) · [AI usage](docs/AI_USAGE.md)

## Run
```bash
mvn test
mvn spring-boot:run
```
Open http://localhost:8080/

## Continuity
The result of this lab is the input for Lab #6, where the same Board gains WebSocket/STOMP collaboration.
No WebSocket code is included yet.
