package edu.eci.arsw.collabboard.infrastructure.web.rest;

import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.Board;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * HTTP adapter for the board use cases.
 *
 * <p>The controller stays thin on purpose: it validates the incoming
 * representation, delegates to {@link BoardApplicationService} and shapes the
 * HTTP response. It contains no business rule, and it never handles errors
 * itself — that is centralized in {@link GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/boards")
public class BoardRestController {

    private final BoardApplicationService service;

    public BoardRestController(BoardApplicationService service) {
        this.service = service;
    }

    /**
     * @return 201 Created with the new board and its Location header
     */
    @PostMapping
    public ResponseEntity<Board> create(@Valid @RequestBody CreateBoardRequest request,
                                        UriComponentsBuilder uriBuilder) {
        Board created = service.createBoard(request.name());
        URI location = uriBuilder.path("/api/boards/{boardId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * @return 200 OK, or 404 BOARD_NOT_FOUND if the id is unknown
     */
    @GetMapping("/{boardId}")
    public Board get(@PathVariable String boardId) {
        return service.getBoard(boardId);
    }

    /**
     * Full replacement of an existing board.
     *
     * @return 200 OK with the replaced board, or 404 BOARD_NOT_FOUND
     */
    @PutMapping("/{boardId}")
    public Board replace(@PathVariable String boardId,
                         @Valid @RequestBody ReplaceBoardRequest request) {
        return service.replaceBoard(boardId, request.name(), request.elements());
    }
}
