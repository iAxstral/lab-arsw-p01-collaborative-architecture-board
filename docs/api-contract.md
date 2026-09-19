# API Contract — Lab 05

## Stable operations
- POST `/api/boards`
- GET `/api/boards/{boardId}`
- PUT `/api/boards/{boardId}`

## BoardElement evolution
`type` supports `RECTANGLE`, `TEXT`, `CONNECTOR`.

For CONNECTOR, `sourceId` and `targetId` are required and must reference existing non-connector elements.

## Examples

### Create — `POST /api/boards`
```json
{ "name": "Architecture Board" }
```
`201 Created`
```json
{ "id": "f5849f7e-584f-471a-ac70-ad69ebc87c9a", "name": "Architecture Board", "elements": [] }
```

### Load — `GET /api/boards/{boardId}` → `200 OK`
Same shape as the response above, with `elements`.

### Save — `PUT /api/boards/{boardId}`
The client always replaces the complete board (no partial endpoints).
```json
{
  "name": "Architecture Board",
  "elements": [
    { "id": "rect-1", "type": "RECTANGLE", "x": 100, "y": 90, "width": 170, "height": 70, "text": "API", "sourceId": null, "targetId": null },
    { "id": "text-1", "type": "TEXT", "x": 400, "y": 210, "width": 150, "height": 30, "text": "DB", "sourceId": null, "targetId": null },
    { "id": "conn-1", "type": "CONNECTOR", "x": 0, "y": 0, "width": 0, "height": 0, "text": "", "sourceId": "rect-1", "targetId": "text-1" }
  ]
}
```
`200 OK` returns the stored board.

## Errors
All errors share one body:
```json
{ "timestamp": "2026-09-19T04:51:18Z", "status": 404, "code": "BOARD_NOT_FOUND", "message": "Board not found: nope", "path": "/api/boards/nope" }
```

| Status | `code` | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Bean validation failed (e.g. blank name). |
| 400 | `INVALID_INPUT` | Domain rule broken: duplicate element id, connector with missing/identical endpoints, or endpoint that is another connector. |
| 404 | `BOARD_NOT_FOUND` | Unknown board id. |

## Client decisions (compatible with the contract)
- `BoardApiClient` maps every failure to `BoardApiError{status, code, message}`; a network failure uses `status: 0`, `code: NETWORK_ERROR`. A blank id fails locally with `INVALID_ID` before any request.
- Deleting a shape in the client also deletes its connectors, so a saved board never has dangling connectors.
- Coordinates are rounded to integers by the client; the server stores doubles.

