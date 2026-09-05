# REST Contract — Lab 04

Base path: `/api/boards`

| Method | Resource | Request | Success response | Error cases |
|---|---|---|---|---|
| POST | `/api/boards` | `{ "name": "Architecture Session" }` | `201 Created`<br>`Location: /api/boards/{boardId}`<br>`{ "id": "…", "name": "Architecture Session", "elements": [] }` | `400 INVALID_REQUEST` when `name` is blank or missing.<br>`400 MALFORMED_REQUEST` when the body is not valid JSON |
| GET | `/api/boards/{boardId}` | - | `200 OK`<br>`{ "id": "…", "name": "…", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist |
| PUT | `/api/boards/{boardId}` | `{ "name": "New name", "elements": [ { "id": "el-1", "type": "RECTANGLE", "x": 0, "y": 0, "width": 120, "height": 60, "text": "note" } ] }` | `200 OK`<br>`{ "id": "boardId", "name": "New name", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist.<br>`400 INVALID_REQUEST` when `name` is blank or `elements` is missing.<br>`400 INVALID_INPUT` when an element fails its domain invariants (blank id, missing type, negative width/height) or when two elements share the same `id`.<br>`400 MALFORMED_REQUEST` when the body is not valid JSON |

### `BoardElement` shape

```json
{
  "id": "string, required, unique within the board",
  "type": "RECTANGLE | TEXT",
  "x": "number",
  "y": "number",
  "width": "number, >= 0",
  "height": "number, >= 0",
  "text": "string, optional (defaults to empty string)"
}
```

### Notes on semantics

- `POST /api/boards` always creates a **new** board with a server-generated
  `id` (UUID) and an empty `elements` list. Clients cannot choose the id.
  The response includes a `Location` header pointing to the new resource
  (`/api/boards/{boardId}`).
- `PUT /api/boards/{boardId}` **replaces** the name and elements of an
  existing board. It never creates a board implicitly: replacing a
  non-existent id fails with `404 BOARD_NOT_FOUND` instead of silently
  creating one (see ADR-001).
- The board `id` cannot be changed through `PUT`; the path variable is
  authoritative and is not expected to be duplicated in the body.
- `elements` must not contain two entries with the same `id`. This is
  enforced as an application rule (not a Bean Validation annotation), so a
  duplicate reports `400 INVALID_INPUT`, not `400 INVALID_REQUEST`.
- If an element in the request body violates its own invariants (e.g.
  negative `width`), the failure happens while the JSON is being
  deserialized into `BoardElement`, before the controller method runs. It is
  still reported as `400 INVALID_INPUT` with the original invariant message
  (see error contract below), not as a generic malformed-body error.

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
| 400 | `INVALID_REQUEST` | Bean Validation failure on the request record (e.g. blank `name`, missing `elements`). All field errors are joined into `message`, not just the first one |
| 400 | `INVALID_INPUT` | A domain invariant is violated while constructing `Board`/`BoardElement` (e.g. negative dimensions, blank id), or the application rejects duplicated element ids in `replaceBoard` |
| 400 | `MALFORMED_REQUEST` | The request body could not be parsed as JSON at all (and the failure is not a domain invariant caught during deserialization) |
| 404 | `BOARD_NOT_FOUND` | The requested `boardId` does not exist |
| 404 | `RESOURCE_NOT_FOUND` | No handler matches the requested path |
| 500 | `INTERNAL_ERROR` | Any unexpected failure not covered by the cases above; no internal detail or stack trace is exposed |

No deviation from the starter's error *shape* (`ApiError`) was introduced;
the set of `code` values was extended beyond the original starter template
to cover cases the implementation actually produces (malformed JSON,
unmatched routes, and unexpected failures), all still funneled through the
same `GlobalExceptionHandler` and the same `ApiError` record.