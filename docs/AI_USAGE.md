# AI Usage Declaration

Declaring AI use does not reduce the grade. You must be able to explain and validate every submitted decision.

**Tool used:** Claude Code (Claude Opus 5), used as an assistant on the backend implementation of Lab 04.
**Scope:** it was used on the code (`src/main`, `src/test`) and on this file. The API contract, the architecture diagrams and ADR-001 were not produced with it.

| Tool | Activity | Prompt / purpose | How I validated the result | What I changed / rejected |
|---|---|---|---|---|
| Claude Code (Opus 5) | Review of the `BoardRepository` port | Ask whether the three starter operations are the minimum required by the use cases | I traced every caller: no use case needs to know that a board exists without also reading it | I accepted removing `existsById`; I kept `save` + `findById` because the service needs both. I rejected adding query methods for elements: nothing consumes them in Lab 04 |
| Claude Code (Opus 5) | `InMemoryBoardRepository` semantics | Ask whether defensive copying is needed and how to document `save` | I re-read the `Board` compact constructor: it already runs `List.copyOf`, and the record is immutable, so stored state cannot be mutated from outside | I documented `save` as an upsert keyed by the board id and left the `HashMap` untouched, since thread-safety is explicitly out of scope for this lab |
| Claude Code (Opus 5) | Implementation of the three use cases | Implement `createBoard`, `getBoard` and `replaceBoard` respecting the ports-and-adapters boundary | `mvn test` with the unit tests, plus manual reading of each method to confirm no Spring or persistence type crosses into the application layer | I decided the id is generated server-side (`UUID`) so create can never overwrite a board, and that `replaceBoard` is a strict replace instead of an upsert; I added the duplicated-element-id rule myself, thinking of the per-element updates that Lab 05 will need |
| Claude Code (Opus 5) | Error mapping in `GlobalExceptionHandler` | Ask which failures were escaping the `ApiError` contract | Reproduced each case as an HTTP test: malformed JSON, unknown `ElementType`, negative dimensions, unknown board | I added the `HttpMessageNotReadableException` handler unwrapping the cause, so a domain invariant rejected during deserialization keeps its own message (`INVALID_INPUT`) instead of being reported as generic malformed JSON. I removed the starter `LAB_NOT_IMPLEMENTED` handler because no use case throws `UnsupportedOperationException` any more |
| Claude Code (Opus 5) | Test suite | Enable the disabled tests and propose additional cases for the use cases and the HTTP contract | Ran `mvn test`: 19 tests, 0 failures. I checked each assertion against the contract I intend to document | I kept the cases that assert observable behaviour (identity preserved, no implicit creation on PUT, state untouched after an invalid replace) and discarded the suggestions that only re-tested Jackson or Spring itself |
| Claude Code (Opus 5) | Repository hygiene | Ask why `target/` appeared in every diff | Verified with `git status` that the build output was versioned in the starter | I accepted ignoring and untracking `target/` and the `.DS_Store` files |

## What was not delegated

- The architectural decision recorded in ADR-001 (why the port is owned by the application layer and not by the infrastructure).
- The API contract documentation and the ArchiMate / class diagrams.
- The choice of HTTP status codes and error codes for the contract, which I fixed first and then covered with tests.

## Verification

```bash
mvn test
```

Result at the time of submission: 19 tests, 0 failures, 0 errors.
