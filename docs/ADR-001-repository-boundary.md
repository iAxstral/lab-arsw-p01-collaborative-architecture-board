# ADR-001 — Repository Boundary

## Status
Accepted

## Context
The application layer (`BoardApplicationService`) needs to persist and retrieve
`Board` aggregates, but Lab 04 only requires an in-memory implementation, and
Lab 05/06/07 are expected to evolve persistence (and later concurrency)
without forcing changes to the use cases or to the REST layer.

We need to decide:

1. Where the boundary between "what the application needs" and "how it is
   stored" lives.
2. Which operations that boundary exposes.
3. What "save" means when a board with the same id already exists.

## Decision
We apply the Dependency Inversion Principle: the application layer defines
an output port, `BoardRepository` (in
`application.port.out`), containing only the operations the use cases
actually need:

- `Board save(Board board)`
- `Optional<Board> findById(String boardId)`
- `boolean existsById(String boardId)`

The port lives inside the application package, has no dependency on Spring,
HTTP, or any storage technology, and is expressed entirely in domain types
(`Board`). `InMemoryBoardRepository`, in
`infrastructure.persistence`, is the only class that knows the storage
technology (a `HashMap`) and implements this port. Spring wires the concrete
adapter into the service via constructor injection, so the service depends
only on the interface, never on `InMemoryBoardRepository`.

`save` is defined as an **upsert** keyed by `board.id()`: it inserts when the
id is new and fully replaces when the id already exists. This keeps the port
minimal (no separate `insert`/`update` methods) because both use cases —
`createBoard` and `replaceBoard` — end up writing a complete `Board` value
through the same operation. The decision of *whether* an update is allowed
(i.e., whether the board must already exist) is a use-case rule, not a
storage rule, so it is enforced in `BoardApplicationService.replaceBoard`
via `existsById`, not inside the repository. The repository itself has no
opinion on business rules; it only persists what it is asked to persist.

## Positive consequences
- The application layer is testable without Spring or any real storage:
  `BoardApplicationServiceTest` instantiates `InMemoryBoardRepository`
  directly, but could equally use a hand-written test double implementing
  `BoardRepository`.
- Swapping `InMemoryBoardRepository` for a JDBC/JPA/Mongo adapter in a later
  lab requires no change to `BoardApplicationService`, `BoardRestController`,
  or the domain model — only a new class implementing `BoardRepository` and a
  Spring wiring change.
- The port surface is intentionally small (3 methods), which keeps future
  adapters simple to implement and keeps the application layer honest about
  what it actually needs, instead of exposing a generic CRUD/DAO-style
  interface.

## Trade-off
- Because `save` is a single upsert operation, the repository cannot by
  itself distinguish "create" from "update" failures (e.g. it will not
  reject an update to a non-existent id on its own). That responsibility is
  pushed up into the application service (`existsById` + explicit
  `BoardNotFoundException`). This is an intentional trade-off: it keeps the
  port small and technology-agnostic, at the cost of requiring every adapter
  implementation to honor the same "no business rules here" contract.
- The port does not (yet) expose `deleteById` or `findAll`, because no
  current use case needs them. Adding board deletion or listing in a later
  lab will require extending this interface; this is considered acceptable
  since the port is meant to grow with the use cases (YAGNI over
  speculative generality).

## Evidence / validation
- `BoardApplicationServiceTest` exercises all three use cases
  (`createBoard`, `getBoard`, `replaceBoard`) against `InMemoryBoardRepository`
  and asserts: unique id generation, empty elements on creation, correct
  `BoardNotFoundException` when reading or replacing a missing board, and
  identity preservation (same `id`) across a successful replace.
- `BoardRestController` never references `InMemoryBoardRepository` or any
  storage type directly — it only depends on `BoardApplicationService`,
  confirming the port boundary is respected end-to-end from HTTP down to
  storage.
- `BoardRestControllerTest` (`@WebMvcTest`) mocks `BoardApplicationService`
  and exercises the HTTP contract (201/200/400/404 with the uniform
  `ApiError` shape) without needing any repository implementation at all,
  further confirming each layer can be tested in isolation from the one
  below it.

## Sustentación — swapping `InMemoryBoardRepository` for another adapter

**Question:** If `InMemoryBoardRepository` is replaced by another adapter
tomorrow, which components must change and which must remain untouched?

**Answer, matching the delivered code:**

- **Must change:** only `infrastructure/persistence/InMemoryBoardRepository.java`
  is deleted/replaced by a new class (e.g. `JpaBoardRepository`,
  `MongoBoardRepository`) that implements `BoardRepository` and is annotated
  so Spring can discover and inject it (e.g. `@Repository`). Any
  technology-specific mapping (entities, documents, SQL, drivers) lives only
  inside that new class.
- **Must remain intact:**
    - `application/port/out/BoardRepository.java` — the port's method
      signatures do not need to change, since they are already
      technology-agnostic.
    - `application/service/BoardApplicationService.java` — it depends only on
      the `BoardRepository` interface, injected by constructor; it has zero
      references to `InMemoryBoardRepository` or to any storage type.
    - `infrastructure/web/rest/BoardRestController.java`,
      `GlobalExceptionHandler.java`, `ApiError.java`,
      `CreateBoardRequest.java`, `ReplaceBoardRequest.java` — the HTTP layer
      only talks to `BoardApplicationService` and has no knowledge of
      persistence at all.
    - `domain/model/Board.java`, `BoardElement.java`, `ElementType.java` — the
      domain model is independent of both HTTP and persistence concerns.
    - All tests in `BoardApplicationServiceTest` would keep passing unchanged
      against a fake/in-memory double if desired, and `BoardRestControllerTest`
      would keep passing unchanged since it mocks `BoardApplicationService`
      and never touches the repository at all.

This is the direct, observable consequence of applying DIP through the
`BoardRepository` port described above: the only class that needs to know
*how* boards are stored is the adapter itself.