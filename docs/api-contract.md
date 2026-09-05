# REST Contract — Lab 04

Base path: `/api/boards`

| Method | Resource | Request | Success response | Error cases |
|---|---|---|---|---|
| POST | `/api/boards` | `{ "name": "Architecture Session" }` | `201 Created`<br>`{ "id": "…", "name": "Architecture Session", "elements": [] }` | `400 INVALID_REQUEST` when `name` is blank or missing |
| GET | `/api/boards/{boardId}` | - | `200 OK`<br>`{ "id": "…", "name": "…", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist |
| PUT | `/api/boards/{boardId}` | `{ "name": "New name", "elements": [ { "id": "el-1", "type": "RECTANGLE", "x": 0, "y": 0, "width": 120, "height": 60, "text": "note" } ] }` | `200 OK`<br>`{ "id": "boardId", "name": "New name", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist.<br>`400 INVALID_REQUEST` when `name` is blank or `elements` is missing.<br>`400 INVALID_INPUT` when an element fails its domain invariants (e.g. blank id, missing type, negative width/height) |

### `BoardElement` shape

```json
{
  "id": "string, required",
  "type": "RECTANGLE | TEXT",
  "x": "number",
  "y": "number",
  "width": "number, >= 0",
  "height": "number, >= 0",
  "text": "string, optional (defaults to empty string)"
}
```

### Notes on semantics

- `POST /api/boards` always creates a **new** board with a server-generated `id` (UUID) and an empty `elements` list. Clients cannot choose the id.
- `PUT /api/boards/{boardId}` **replaces** the name and elements of an existing board. It never creates a board implicitly: replacing a non-existent id fails with `404 BOARD_NOT_FOUND` instead of silently creating one (see ADR-001 for why the underlying repository operation is an upsert while the API-level use case is not).
- The board `id` cannot be changed through `PUT`; the path variable is authoritative and is not expected to be duplicated in the body.

## Error contract

All error responses share this shape:

```json
{
  "timestamp": "2026-...",
  "status": 404,
  "code": "BOARD_NOT_FOUND",
  "message": "Board not found: <boardId>",
  "path": "/api/boards/<boardId>"
}
```

| HTTP status | `code` | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Bean validation failure on the request body (e.g. blank `name`, missing `elements`) |
| 400 | `INVALID_INPUT` | A domain invariant is violated while constructing `Board`/`BoardElement` (e.g. negative dimensions) |
| 404 | `BOARD_NOT_FOUND` | The requested `boardId` does not exist |
| 501 | `LAB_NOT_IMPLEMENTED` | A use case has not been implemented yet (kept only so an incomplete starter fails loudly instead of leaking a stack trace; should not occur once the lab is complete) |

No deviation from the starter contract was introduced.