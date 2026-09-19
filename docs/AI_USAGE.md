# AI Usage Declaration

| Tool | Activity | Purpose | Output used? | Validation performed | Changes made by team |
|---|---|---|---|---|---|
| Claude Code (Anthropic) | Reviewed the starter and listed the pending `TODO LAB-05` items | Gap analysis against the lab acceptance criteria | Yes, as a checklist | Compared against the assignment text and the repository | Prioritised the work |
| Claude Code (Anthropic) | Implemented `BoardApiClient`, `BoardState`, `BoardView`, `BoardApp` (drag offset, connector rules, Loading/Success/Error/Retry) | Complete the interactive SVG client keeping the four boundaries | Yes, reviewed and adjusted | Ran the app; exercised create/load/save, unknown id (404) and invalid connector (400) over REST; ran `BoardState` rules in Node; `mvn test` | Reviewed every module and manually tested the UI flow |
| Claude Code (Anthropic) | Backend: connectors must reference non-connector elements; new unit test | Match the API contract | Yes | `mvn test` and a `PUT` with a dangling connector returned `400 INVALID_INPUT` | Reviewed the domain rule |
| Claude Code (Anthropic) | Drafted ADR-002, API contract examples, ArchiMate Open Exchange model and Mermaid class diagram | Architecture evidence | Yes, edited | Checked names/relations against the code | Reviewed the diagrams; re-export the ArchiMate view as an image from Archi if required |

**Responsibility:** the team reviewed, understands and is accountable for all submitted content. AI output was not accepted without running and reading it.
