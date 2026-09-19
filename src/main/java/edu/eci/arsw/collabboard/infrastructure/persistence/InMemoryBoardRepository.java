package edu.eci.arsw.collabboard.infrastructure.persistence;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import org.springframework.stereotype.Repository;
import java.util.HashMap; import java.util.Map; import java.util.Optional;
@Repository
public class InMemoryBoardRepository implements BoardRepository {
 private final Map<String,Board> boards=new HashMap<>();
 public Board save(Board b){ boards.put(b.id(),b); return b; }
 public Optional<Board> findById(String id){ return Optional.ofNullable(boards.get(id)); }
 public boolean existsById(String id){ return boards.containsKey(id); }
}
