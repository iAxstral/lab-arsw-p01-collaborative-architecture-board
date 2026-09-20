# Architecture Evidence

Diagrams describing the code as delivered in Lab 04 and Lab 05. Reasoning
behind the boundaries is in [ADR-001](../ADR-001-repository-boundary.md) and
[ADR-002](../ADR-002-client-boundaries.md).

| File | What it shows |
|---|---|
| [application-view-lab4.puml](application-view-lab4.puml) / [.png](application-view-lab4.png) | ArchiMate application view, Lab 04 baseline: REST interface, application service, `BoardRepository` port and `InMemoryBoardRepository`. |
| [application-view-lab5.puml](application-view-lab5.puml) / [.png](application-view-lab5.png) | Evolution of the Lab 04 view: adds the Web Client, `BoardApiClient` (the only element connected to the REST interface) and `BoardView`. |
| [class-diagram.md](class-diagram.md) | Mermaid class/module diagram: backend classes and domain records plus the client modules (`BoardApiClient`, `BoardState`, `BoardView`, `app.js`). |
