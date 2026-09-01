package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BoardApplicationService {

    private final BoardRepository repository;

    public BoardApplicationService(BoardRepository repository) {
        this.repository = repository;
    }

    public Board createBoard(String name) {
        String id = UUID.randomUUID().toString();
        Board board = new Board(id, name, List.of());
        return repository.save(board);
    }

    public Board getBoard(String boardId) {
        return repository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));
    }

    public Board replaceBoard(String boardId, String name, List<BoardElement> elements) {
        if (!repository.existsById(boardId)) {
            throw new BoardNotFoundException(boardId);
        }
        Board updated = new Board(boardId, name, elements);
        return repository.save(updated);
    }
}