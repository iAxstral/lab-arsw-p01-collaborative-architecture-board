package edu.eci.arsw.collabboard.application.service;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import org.springframework.stereotype.Service;
import java.util.List; import java.util.UUID;
@Service
public class BoardApplicationService {
 private final BoardRepository repository;
 public BoardApplicationService(BoardRepository repository){this.repository=repository;}
 public Board createBoard(String name){ return repository.save(new Board(UUID.randomUUID().toString(),name,List.of())); }
 public Board getBoard(String id){ return repository.findById(id).orElseThrow(() -> new BoardNotFoundException(id)); }
 public Board replaceBoard(String id,String name,List<BoardElement> elements){ if(!repository.existsById(id)) throw new BoardNotFoundException(id); return repository.save(new Board(id,name,elements)); }
}
