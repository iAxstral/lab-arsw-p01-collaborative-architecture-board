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

---

## Lab 5 — Interactive Board

**Herramienta:** Claude Code (asistente de IA en la terminal).
**Tests al cierre del laboratorio:** 33, todos en verde.

| Entregable | Actividad y propósito | Resultado | Validación del equipo | Modificaciones del equipo |
|---|---|---|---|---|
| Backend CONNECTOR (`Board`, `BoardElement`) | Apoyo para extender el dominio (records inmutables) sin romper las invariantes existentes, y para decidir dónde vivía la validación de `sourceId`/`targetId` (`BoardElement` vs. `Board`) | `ElementType.CONNECTOR`, campos `sourceId`/`targetId` y validación de referencias en `Board` | `mvn test` después de cada cambio | Aplicamos las reglas de la sección 4 del enunciado (obligatorios, distintos, referencian elementos existentes en el mismo `Board`). Un test quedó en `src/main` en vez de `src/test`; lo detectamos por el log de compilación y lo movimos a mano |
| Tests (33) | Apoyo para adaptar los tests existentes a la nueva firma de `BoardElement` sin perder cobertura y para estructurar los casos de CONNECTOR | Suite de 33 tests, con casos de conector válido, referencia inexistente, extremos iguales, conector hacia otro conector y `sourceId` en un rectángulo | Corrimos `mvn test` nosotros mismos y confirmamos los 33 en verde antes de cerrar cada parte | Decidimos qué escenarios de conector cubrir |
| Cliente JS (api-client, state, view, app) | Apoyo para construir los 4 módulos siguiendo la arquitectura objetivo del enunciado (`BoardApp` → `BoardApiClient` + `BoardState` + `BoardView`) y para mantener el estado inmutable de forma consistente | Cuatro módulos ES Modules con SVG nativo | Probamos el flujo completo en navegador real (crear, cargar, agregar, mover, conectar, eliminar, guardar, recargar, error/retry) y verificamos con búsqueda de texto que `fetch` solo exista en `board-api-client.js` | El enunciado indicaba una ruta incorrecta para `index.html`; se corrigió a `static/index.html` |
| ADR-002 | Claude Code redactó el ADR a partir del código y de la separación de módulos planteada en la sección 3 del enunciado | `docs/ADR-002-client-boundaries.md` | Nosotros lo revisamos y verificamos que el trade-off (modo "connect" sin cancelación) fuera una limitación real del código | — |
| ArchiMate `.puml` (Lab 4 y Lab 5) | Aclarar la sintaxis de PlantUML/ArchiMate, que no conocíamos, para representar la vista de aplicación | `application-view-lab4.puml` y `application-view-lab5.puml` | Confirmamos que los diagramas compilaran y que las relaciones correspondieran al código real | Corregimos que fuera `BoardApiClient` y no `BoardView` quien se conecta a la API, porque la vista no hace peticiones HTTP |
| `class-diagram.md` | Apoyo para estructurar el diagrama en sintaxis Mermaid | `docs/architecture/class-diagram.md` | Confirmamos que la sintaxis renderizara bien | Decidimos qué clases/módulos incluir para explicar dependencias sin hacer un inventario del código |
| Hardening de backend y cliente (PR 1 y 2) | Claude Code implementó las reglas de CONNECTOR (rechazo de conector hacia otro conector y de `sourceId`/`targetId` en elementos que no son conector), los tests HTTP, el bloqueo del canvas mientras hay una petición en curso y el guard de `parse()` para respuestas 2xx sin JSON válido | Cambios en `Board`, `BoardElement`, `app.js`, `app.css` y `board-api-client.js`; 11 tests nuevos | `mvn test` y revisión del diff por el equipo | — |
| Prueba manual end-to-end | No se usó IA | Flujo completo verificado | Ejecutamos nosotros, en navegador, crear, cargar, agregar, mover, conectar, eliminar (incluido un elemento con un conector asociado), guardar, recargar y error/retry | — |
