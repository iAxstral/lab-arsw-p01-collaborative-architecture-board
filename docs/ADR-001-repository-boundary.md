# ADR-001 — Repository Boundary

## Status
Accepted

## Context
The application layer (`BoardApplicationService`) needs to persist and
retrieve `Board` aggregates, but Lab 04 only requires an in-memory
implementation, and Lab 05/06/07 are expected to evolve persistence (and
later concurrency) without forcing changes to the use cases or to the REST
layer.

We need to decide:

1. Where the boundary between "what the application needs" and "how it is
   stored" lives.
2. Which operations that boundary exposes — and, just as important, which
   operations it should *not* expose just because a typical repository
   framework would offer them.
3. What "save" means, and whether "does this board exist" deserves its own
   port method or can be answered through an operation the application
   already needs.

## Decision
We apply the Dependency Inversion Principle: the application layer defines
an output port, `BoardRepository` (in `application.port.out`), containing
only the two operations the current use cases actually need:

- `Board save(Board board)`
- `Optional<Board> findById(String boardId)`

The port lives inside the application package, has no dependency on Spring,
HTTP, or any storage technology, and is expressed entirely in domain types
(`Board`). `InMemoryBoardRepository`, in `infrastructure.persistence`, is the
only class that knows the storage technology (a `HashMap`) and implements
this port. Spring wires the concrete adapter into the service via
constructor injection, so the service depends only on the interface, never
on `InMemoryBoardRepository`.

`save` is defined as an **upsert** keyed by `board.id()`. `findById` returns
`Optional<Board>` instead of throwing, so the "not found" decision — and the
exception type used to signal it — belongs to the application layer, not to
the adapter.

An earlier version of this port also exposed `existsById(String boardId)`,
intended for `replaceBoard` to check existence before writing. We removed
it: every caller that needs to know whether a board exists also needs the
board itself right after (to preserve its `elements`/identity or to return
it), so `findById` already answers that question. Keeping `existsById` would
have meant doing two lookups — `existsById` then `findById` — for a single
use case, or duplicating the "not found" branching logic between the port
and the service. `BoardApplicationService.replaceBoard` now calls
`getBoard(boardId)` (which itself is `findById(...).orElseThrow(...)`) to
both confirm existence and obtain the current board in one round trip to the
adapter, then persists the replacement through the same `save`.

## Positive consequences
- The application layer is testable without Spring or any real storage:
  `BoardApplicationServiceTest` instantiates `InMemoryBoardRepository`
  directly, with no Spring context.
- Swapping `InMemoryBoardRepository` for a JDBC/JPA/Mongo adapter in a later
  lab requires no change to `BoardApplicationService`, `BoardRestController`,
  or the domain model — only a new class implementing `BoardRepository` and
  a Spring wiring change.
- The port surface is intentionally minimal (2 methods), which keeps future
  adapters simple to implement and keeps the application layer honest about
  what it actually needs, instead of exposing a generic CRUD/DAO-style
  interface with methods nothing calls.
- Removing `existsById` avoids a second network/adapter round trip in
  `replaceBoard`: one `findById` call now serves both the existence check
  and the data needed to build the replacement.

## Trade-off
- Because the port has no `existsById`, any future use case that only needs
  a cheap existence check (without needing the board's data) will have to
  either add that method back or pay for a full `findById`. We accept this
  now because no such use case exists yet; adding it later is a small,
  localized change to one interface and one adapter.
- Because `save` is a single upsert operation, the repository cannot by
  itself distinguish "create" from "update" failures. That responsibility is
  pushed up into the application service (`getBoard` + explicit
  `BoardNotFoundException` in `replaceBoard`). This is intentional: it keeps
  the port small and technology-agnostic, at the cost of requiring every
  adapter implementation to honor the same "no business rules here"
  contract.
- The port does not expose `deleteById` or `findAll`, because no current use
  case needs them. Adding board deletion or listing in a later lab will
  require extending this interface; this is accepted since the port is
  meant to grow with the use cases (YAGNI over speculative generality).

## Evidence / validation
- `BoardApplicationServiceTest` exercises all three use cases
  (`createBoard`, `getBoard`, `replaceBoard`) against `InMemoryBoardRepository`
  and asserts: distinct id generation, empty elements on creation,
  `BoardNotFoundException` when reading or replacing a missing board,
  identity preservation across a successful replace, rejection of duplicated
  element ids, and that a board is left untouched when a replace attempt is
  invalid.
- `BoardRestController` never references `InMemoryBoardRepository` or any
  storage type directly — it only depends on `BoardApplicationService`,
  confirming the port boundary is respected end-to-end from HTTP down to
  storage.
- `BoardRestControllerTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`)
  exercises the real HTTP contract through the whole stack — controller,
  application service, in-memory adapter, and `GlobalExceptionHandler` —
  which is only possible because none of those layers depend on a specific
  storage technology.

## Sustentación — swapping `InMemoryBoardRepository` for another adapter

**Question:** If `InMemoryBoardRepository` is replaced by another adapter
tomorrow, which components must change and which must remain untouched?

**Answer, matching the delivered code:**

- **Must change:** only `infrastructure/persistence/InMemoryBoardRepository.java`
  is deleted/replaced by a new class (e.g. `JpaBoardRepository`,
  `MongoBoardRepository`) that implements `BoardRepository` (`save` and
  `findById`) and is annotated so Spring can discover and inject it (e.g.
  `@Repository`). Any technology-specific mapping (entities, documents, SQL,
  drivers) lives only inside that new class.
- **Must remain intact:**
    - `application/port/out/BoardRepository.java` — its two method signatures
      do not need to change, since they are already technology-agnostic.
    - `application/service/BoardApplicationService.java` — it depends only on
      the `BoardRepository` interface, injected by constructor; it has zero
      references to `InMemoryBoardRepository` or to any storage type. Its
      `getBoard`/`existsById-via-findById` reasoning is unaffected by how
      `findById` is implemented underneath.
    - `infrastructure/web/rest/BoardRestController.java`,
      `GlobalExceptionHandler.java`, `ApiError.java`, `CreateBoardRequest.java`,
      `ReplaceBoardRequest.java` — the HTTP layer only talks to
      `BoardApplicationService` and has no knowledge of persistence at all.
    - `domain/model/Board.java`, `BoardElement.java`, `ElementType.java` — the
      domain model is independent of both HTTP and persistence concerns.
    - `BoardApplicationServiceTest` would keep passing unchanged against a
      fake/in-memory double if one is kept for fast unit tests, and
      `BoardRestControllerTest` would keep passing unchanged against the new
      adapter, since it exercises behavior through the HTTP contract, not
      through the storage implementation.

This is the direct, observable consequence of applying DIP through the
`BoardRepository` port described above: the only class that needs to know
*how* boards are stored is the adapter itself.