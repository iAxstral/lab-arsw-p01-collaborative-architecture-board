# REST Contract — Lab 5

Base path: `/api/boards`

| Method | Resource | Request | Success response | Error cases |
|---|---|---|---|---|
| POST | `/api/boards` | `{ "name": "Architecture Session" }` | `201 Created`<br>`Location: /api/boards/{boardId}`<br>`{ "id": "…", "name": "Architecture Session", "elements": [] }` | `400 INVALID_REQUEST` when `name` is blank or missing.<br>`400 MALFORMED_REQUEST` when the body is not valid JSON |
| GET | `/api/boards/{boardId}` | - | `200 OK`<br>`{ "id": "…", "name": "…", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist |
| PUT | `/api/boards/{boardId}` | `{ "name": "New name", "elements": [ { "id": "el-1", "type": "RECTANGLE", "x": 0, "y": 0, "width": 120, "height": 60, "text": "note" } ] }` | `200 OK`<br>`{ "id": "boardId", "name": "New name", "elements": [ ... ] }` | `404 BOARD_NOT_FOUND` when `boardId` does not exist.<br>`400 INVALID_REQUEST` when `name` is blank or `elements` is missing.<br>`400 INVALID_INPUT` when an element fails its domain invariants (blank id, missing type, negative width/height) or when two elements share the same `id`, or when a `CONNECTOR` breaks the rules listed under "`CONNECTOR` elements".<br>`400 MALFORMED_REQUEST` when the body is not valid JSON |

### `BoardElement` shape

```json
{
  "id": "string, required, unique within the board",
  "type": "RECTANGLE | TEXT | CONNECTOR",
  "x": "number",
  "y": "number",
  "width": "number, >= 0",
  "height": "number, >= 0",
  "text": "string, optional (defaults to empty string)",
  "sourceId": "string, required when type is CONNECTOR; must be absent, null or blank otherwise",
  "targetId": "string, required when type is CONNECTOR; must be absent, null or blank otherwise"
}
```

### `CONNECTOR` elements

A `CONNECTOR` does not render as a shape; it represents a line between two
other elements already present in the same `elements` list.

| Field | Rule |
|---|---|
| `sourceId` | Required. Must match the `id` of another element in the same board, and that element must not be a `CONNECTOR` |
| `targetId` | Required. Must match the `id` of another element in the same board, must not be a `CONNECTOR`, and must be different from `sourceId` |
| `x`, `y`, `width`, `height`, `text` | Not used to render a connector; accepted as `0`/`""` |

A `CONNECTOR` referencing a `sourceId`/`targetId` that is missing from the
submitted `elements`, or with `sourceId == targetId`, fails as a domain
invariant — same `400 INVALID_INPUT` error contract as any other element
invariant violation, and nothing is persisted.

Two further rules:

- **A connector cannot connect another connector.** If the `sourceId` or
  `targetId` of a `CONNECTOR` refers to an element whose `type` is also
  `CONNECTOR`, the request fails with `400 INVALID_INPUT` (for example
  `Connector target cannot be another connector: <id>`).
- **Only connectors may define `sourceId`/`targetId`.** A `RECTANGLE` or
  `TEXT` that carries a non-blank `sourceId` or `targetId` fails with
  `400 INVALID_INPUT` and the message
  `Only CONNECTOR elements may define sourceId/targetId`. Missing, `null`,
  empty or blank values are accepted and normalized to `null`.

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
- `CONNECTOR` elements are validated at the `Board` level, not per-element:
  the check runs against the full `elements` list being submitted, so a
  connector can reference another element created in the very same `PUT`
  request.

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
| 500 | `INTERNAL_ERROR` | Any failure not covered by the cases above; no internal detail or stack trace is exposed. This currently includes requests to a path with no handler (e.g. `GET /api/nope`), unsupported HTTP methods (e.g. `DELETE /api/boards/{boardId}`) and unsupported media types (e.g. `Content-Type: text/plain`): they are not mapped to 404/405/415 |

No deviation from the starter's error *shape* (`ApiError`) was introduced;
the set of `code` values was extended beyond the original starter template
to cover cases the implementation actually produces (malformed JSON and
unexpected failures, the latter also covering unmatched routes), all still
funneled through the same `GlobalExceptionHandler` and the same `ApiError`
record.