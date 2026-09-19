package edu.eci.arsw.collabboard.application.port.out;
import edu.eci.arsw.collabboard.domain.model.Board;
import java.util.Optional;
public interface BoardRepository { Board save(Board board); Optional<Board> findById(String id); boolean existsById(String id); }
