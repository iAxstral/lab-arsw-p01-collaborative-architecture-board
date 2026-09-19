package edu.eci.arsw.collabboard.infrastructure.web.rest;
import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.Board;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/boards")
public class BoardRestController {
 private final BoardApplicationService service;
 public BoardRestController(BoardApplicationService service){this.service=service;}
 @PostMapping public ResponseEntity<Board> create(@Valid @RequestBody CreateBoardRequest r){ return ResponseEntity.status(HttpStatus.CREATED).body(service.createBoard(r.name())); }
 @GetMapping("/{id}") public Board get(@PathVariable String id){ return service.getBoard(id); }
 @PutMapping("/{id}") public Board replace(@PathVariable String id,@Valid @RequestBody ReplaceBoardRequest r){ return service.replaceBoard(id,r.name(),r.elements()); }
}
