# Architecture Evidence

Diagrams describing the code as delivered in Lab 04, Lab 05 and Lab 06.
Reasoning behind the boundaries is in
[ADR-001](../ADR-001-repository-boundary.md),
[ADR-002](../ADR-002-client-boundaries.md) and
[ADR-003](../ADR-003-rest-vs-realtime.md).

| File | What it shows |
|---|---|
| [application-view-lab4.puml](application-view-lab4.puml) / [.png](application-view-lab4.png) | ArchiMate application view, Lab 04 baseline: REST interface, application service, `BoardRepository` port and `InMemoryBoardRepository`. |
| [application-view-lab5.puml](application-view-lab5.puml) / [.png](application-view-lab5.png) | Evolution of the Lab 04 view: adds the Web Client, `BoardApiClient` (the only element connected to the REST interface) and `BoardView`. |
| [application-view-lab6.puml](application-view-lab6.puml) / [.png](application-view-lab6.png) | Evolution of the Lab 05 view: adds the WebSocket/STOMP interface, `BoardWebSocketController`, `BoardEventApplicationService` (reusing the same `BoardRepository` port as the REST path), the `BoardEvent` contract, and `BoardRealtimeClient` (the only client element connected to the WebSocket/STOMP interface). |
| [class-diagram.md](class-diagram.md) | Mermaid class/module diagram: backend classes and domain records, the Lab 05 client modules (`BoardApiClient`, `BoardState`, `BoardView`, `app.js`) and the Lab 06 additions (`BoardEvent`/`BoardEventType`/`BoardEventPayload`, `BoardEventApplicationService`, `BoardWebSocketController`, `BoardRealtimeClient`, `BoardEvents`). |