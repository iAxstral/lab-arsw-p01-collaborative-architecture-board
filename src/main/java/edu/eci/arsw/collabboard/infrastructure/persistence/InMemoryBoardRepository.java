package edu.eci.arsw.collabboard.infrastructure.persistence;

import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory adapter for {@link BoardRepository}.
 *
 * <p>Semantics of {@link #save(Board)}: the board id is the primary key and
 * the operation is an upsert. Creation and replacement therefore share the
 * same adapter operation, and it is the application service — not the
 * adapter — that decides whether a missing board is an error.</p>
 *
 * <p>Defensive copying is not required here: {@link Board} is an immutable
 * record whose compact constructor already runs {@code List.copyOf} over its
 * elements, so neither the caller nor the map can mutate stored state.</p>
 */
@Repository
public class InMemoryBoardRepository implements BoardRepository {

    /*
     * Intentionally simple for Lab 04.
     * Thread-safety is NOT the focus of this lab. Do not redesign this yet only
     * because you remember concurrency from previous weeks; that concern will
     * return in a later evolution of the same application.
     */
    private final Map<String, Board> boards = new HashMap<>();

    @Override
    public Board save(Board board) {
        boards.put(board.id(), board);
        return board;
    }

    @Override
    public Optional<Board> findById(String boardId) {
        if (boardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(boards.get(boardId));
    }
}
