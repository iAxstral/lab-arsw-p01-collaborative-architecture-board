# BoardEvent contract — Lab #6

## Destinations

Client to application:

```text
/app/boards/{boardId}/events
```

Server broadcast:

```text
/topic/boards/{boardId}
```

## Envelope

```json
{
  "eventId": "f2b2d7c6-...",
  "boardId": "board-123",
  "type": "ELEMENT_MOVED",
  "actorId": "client-abc",
  "occurredAt": "2026-09-01T12:30:00Z",
  "payload": {
    "element": null,
    "elementId": "rect-1",
    "x": 420.0,
    "y": 180.0
  }
}
```

## Event types

| Type | Payload | Meaning |
|---|---|---|
| `ELEMENT_CREATED` | `element` | Add RECTANGLE or TEXT. |
| `CONNECTOR_CREATED` | `element` | Add a CONNECTOR with valid source/target ids. |
| `ELEMENT_MOVED` | `elementId`, `x`, `y` | Change position of a non-connector element. |
| `ELEMENT_UPDATED` | `element` | Replace editable properties of an existing element. |
| `ELEMENT_DELETED` | `elementId` | Delete the element and connectors that depend on it. |

## Architectural rule

The server must **apply and validate** an event before broadcasting it. Clients should update their state from accepted events and then render the state. The STOMP callback must not become a second UI controller.

## Deliberately deferred

This contract has no sequence number or version yet. Lab #7 will use simultaneous edits to expose why a consistency strategy is required.
