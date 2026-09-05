package edu.eci.arsw.collabboard.application.port.out;

import edu.eci.arsw.collabboard.domain.model.Board;

import java.util.Optional;

/**
 * Output port owned by the application boundary.
 *
 * <p>The port exposes only the two operations the current use cases need:
 * persisting a board and reading it back by id. An {@code existsById}
 * operation was intentionally removed: every caller that needs to know
 * whether a board exists also needs the board itself, so
 * {@link #findById(String)} already covers that question and a second
 * operation would only add a redundant round trip to the adapter.</p>
 *
 * <p>No framework type appears in this signature on purpose: the application
 * layer must remain independent of the persistence technology, so a future
 * JPA or Redis adapter can replace {@code InMemoryBoardRepository} without
 * touching the service.</p>
 */
public interface BoardRepository {

    /**
     * Stores the board under its own id, replacing any previous state
     * associated with that id (upsert semantics).
     *
     * @return the persisted board
     */
    Board save(Board board);

    /**
     * @return the board with the given id, or {@link Optional#empty()} if the
     *         id is unknown. Returning {@code Optional} keeps the "not found"
     *         decision inside the application layer instead of letting the
     *         adapter choose an exception.
     */
    Optional<Board> findById(String boardId);
}
