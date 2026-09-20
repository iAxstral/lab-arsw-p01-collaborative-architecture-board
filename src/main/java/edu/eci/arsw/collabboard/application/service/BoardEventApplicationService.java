package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies collaboration events to the authoritative Board state.
 *
 * <p>Every event is applied to the stored Board and validated by the same
 * invariants as the REST path (they live in {@link Board} and
 * {@link BoardElement}); an event that violates them throws and nothing is
 * saved, so it is never broadcast. Protocol concerns stay out of the domain.</p>
 *
 * <p>Lab 7 material, intentionally not solved here: {@link #apply} is an
 * unsynchronized read-modify-write over a non-thread-safe repository.</p>
 */
@Service
public class BoardEventApplicationService {

    private final BoardRepository repository;

    public BoardEventApplicationService(BoardRepository repository) {
        this.repository = repository;
    }

    /**
     * @return the normalized event that may be broadcast to subscribers
     * @throws BoardNotFoundException   if the board does not exist
     * @throws IllegalArgumentException if the event is malformed or breaks a Board invariant
     */
    public BoardEvent apply(BoardEvent event) {
        Board board = repository.findById(event.boardId())
                .orElseThrow(() -> new BoardNotFoundException(event.boardId()));
        BoardEventPayload payload = event.payload();

        return switch (event.type()) {
            case ELEMENT_CREATED -> {
                BoardElement element = requireElement(payload, event);
                if (element.type() == ElementType.CONNECTOR) {
                    throw new IllegalArgumentException("Use CONNECTOR_CREATED to create connectors");
                }
                add(board, element);
                yield event;
            }
            case CONNECTOR_CREATED -> {
                BoardElement element = requireElement(payload, event);
                if (element.type() != ElementType.CONNECTOR) {
                    throw new IllegalArgumentException("CONNECTOR_CREATED requires a CONNECTOR element");
                }
                add(board, element);
                yield event;
            }
            case ELEMENT_MOVED -> {
                move(board, payload);
                yield event;
            }
            case ELEMENT_UPDATED -> updateText(board, event);
            case ELEMENT_DELETED -> {
                delete(board, requireElementId(payload, event));
                yield event;
            }
        };
    }

    private void add(Board board, BoardElement element) {
        if (find(board, element.id()) != null) {
            return;
        }
        List<BoardElement> elements = new ArrayList<>(board.elements());
        elements.add(element);
        repository.save(new Board(board.id(), board.name(), elements));
    }

    private void move(Board board, BoardEventPayload payload) {
        if (payload.elementId() == null || payload.elementId().isBlank()) {
            throw new IllegalArgumentException("ELEMENT_MOVED requires payload.elementId");
        }
        if (payload.x() == null || payload.y() == null) {
            throw new IllegalArgumentException("ELEMENT_MOVED requires payload.x and payload.y");
        }
        BoardElement current = requireExisting(board, payload.elementId());
        if (current.type() == ElementType.CONNECTOR) {
            throw new IllegalArgumentException("Connectors cannot be moved: " + current.id());
        }
        replace(board, new BoardElement(current.id(), current.type(), payload.x(), payload.y(),
                current.width(), current.height(), current.text(), current.sourceId(), current.targetId()));
    }

    private BoardEvent updateText(Board board, BoardEvent event) {
        BoardElement requested = requireElement(event.payload(), event);
        BoardElement current = requireExisting(board, requested.id());
        BoardElement updated = new BoardElement(current.id(), current.type(), current.x(), current.y(),
                current.width(), current.height(), requested.text(), current.sourceId(), current.targetId());
        replace(board, updated);
        return new BoardEvent(event.eventId(), event.boardId(), event.type(), event.actorId(), event.occurredAt(),
                new BoardEventPayload(updated, updated.id(), null, null));
    }

    private void delete(Board board, String elementId) {
        if (find(board, elementId) == null) {
            return;
        }
        List<BoardElement> remaining = board.elements().stream()
                .filter(e -> !e.id().equals(elementId)
                        && !elementId.equals(e.sourceId())
                        && !elementId.equals(e.targetId()))
                .toList();
        repository.save(new Board(board.id(), board.name(), remaining));
    }

    private void replace(Board board, BoardElement next) {
        List<BoardElement> elements = board.elements().stream()
                .map(e -> e.id().equals(next.id()) ? next : e)
                .toList();
        repository.save(new Board(board.id(), board.name(), elements));
    }

    private BoardElement find(Board board, String elementId) {
        return board.elements().stream().filter(e -> e.id().equals(elementId)).findFirst().orElse(null);
    }

    private BoardElement requireExisting(Board board, String elementId) {
        BoardElement element = find(board, elementId);
        if (element == null) {
            throw new IllegalArgumentException("Element not found: " + elementId);
        }
        return element;
    }

    private BoardElement requireElement(BoardEventPayload payload, BoardEvent event) {
        if (payload.element() == null) {
            throw new IllegalArgumentException(event.type() + " requires payload.element");
        }
        return payload.element();
    }

    private String requireElementId(BoardEventPayload payload, BoardEvent event) {
        if (payload.elementId() == null || payload.elementId().isBlank()) {
            throw new IllegalArgumentException(event.type() + " requires payload.elementId");
        }
        return payload.elementId();
    }
}
