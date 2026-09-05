# AI Usage Declaration

Declaring AI use does not reduce the grade. You must be able to explain and validate every submitted decision.

| Tool | Activity | Prompt / purpose | How I validated the result | What I changed / rejected |
|---|---|---|---|---|
| Claude (Anthropic) | Wrote `BoardRestControllerTest.java`, the HTTP-facing test class for the `/api/boards` contract | Asked for `@WebMvcTest` tests covering create (201), get (200/404), replace (200/404), and validation failures (400), with `BoardApplicationService` mocked | Ran the tests against the implemented controller and `GlobalExceptionHandler`, and checked every `jsonPath` assertion against the actual field names returned by `Board` and `ApiError` | Kept the mock at the service boundary (not the repository) so the test stays focused on the HTTP layer only |
| Claude (Anthropic) | Helped redraft `README.md` (root) and `docs/architecture/README.md` for clarity | Asked for clearer wording/structure of the existing instructions and evidence sections | Re-read both files against the project structure to confirm the instructions match what is actually in the repository | Wording and organization only; no new architectural claims were introduced |

## What was not AI-generated

The domain model, the application service, the repository port and its
in-memory adapter, the REST controller, the error handling, the ADR, and the
architecture diagrams were produced by the team without AI assistance.
 