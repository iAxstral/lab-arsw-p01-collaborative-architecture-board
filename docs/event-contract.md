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
| `ELEMENT_UPDATED` | `element` | Change the text of an existing element. |
| `ELEMENT_DELETED` | `elementId` | Delete the element and the connectors that depend on it. |

## Architectural rule

The server must **apply and validate** an event before broadcasting it. Clients should update their state from accepted events and then render the state. The STOMP callback must not become a second UI controller.

## Rules applied by the server

`BoardEventApplicationService` loads the Board, applies the event and saves the
result. The Board and element invariants (`Board`, `BoardElement`) are the same
as for the REST path.

| Type | Rule |
|---|---|
| `ELEMENT_CREATED` | Requires `payload.element`. An element whose `type` is `CONNECTOR` is **rejected** (use `CONNECTOR_CREATED`). If an element with the same `id` already exists the event is **ignored** (the Board does not change) but it is **still broadcast**, so clients must apply it idempotently. |
| `CONNECTOR_CREATED` | Requires `payload.element`. An element that is not a `CONNECTOR` is **rejected**. The connector must satisfy the Board invariants: `sourceId` and `targetId` exist, differ from each other and are not connectors. A duplicated connector `id` is ignored and still broadcast, like `ELEMENT_CREATED`. |
| `ELEMENT_MOVED` | Requires `elementId`, `x` and `y`. Moving an element that does not exist is **rejected**. Moving a `CONNECTOR` is **rejected**. Only `x` and `y` change. Applying the same move twice leaves the same Board. |
| `ELEMENT_UPDATED` | Requires `payload.element`. Updating an element that does not exist is **rejected**. Only the `text` changes: position, size, type and endpoints keep their server-side values. The broadcast event carries the element **as it was left on the server**, with `payload.elementId` set to its id. |
| `ELEMENT_DELETED` | Requires `elementId`. Deletes the element and, in cascade, every connector whose `sourceId` or `targetId` references it. Deleting an element that does not exist is a **no-op** (accepted and broadcast). |

## Rejected events

An event is rejected when it is malformed (for example a blank `eventId`), when
the `boardId` in the destination `/app/boards/{boardId}/events` differs from
`event.boardId`, when the Board does not exist, or when it breaks one of the
rules above. A rejected event is **not broadcast** and nothing is sent back to
the sender: the server only writes a `Rejected board event: <reason>` line in
its log.

## Known limitation

The order of events sent in a burst is not guaranteed (the STOMP inbound channel
is multithreaded): a `MOVE` sent right after the `CREATE` of the same element can
be processed first and be rejected. It is analyzed in Lab 7.

## Deliberately deferred

This contract has no sequence number or version yet. Lab #7 will use simultaneous edits to expose why a consistency strategy is required.
