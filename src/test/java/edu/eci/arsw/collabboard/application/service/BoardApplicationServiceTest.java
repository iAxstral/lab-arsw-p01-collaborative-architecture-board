package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the use cases, wired against the in-memory adapter through the
 * port. No Spring context is needed, which is precisely the benefit of
 * constructor injection over field injection.
 */
class BoardApplicationServiceTest {

    private final BoardApplicationService service =
            new BoardApplicationService(new InMemoryBoardRepository());

    @Test
    void shouldCreateAndReadBoard() {
        Board created = service.createBoard("Architecture Session");
        Board loaded = service.getBoard(created.id());

        assertEquals(created, loaded);
    }

    @Test
    void shouldFailWithConcreteExceptionWhenBoardDoesNotExist() {
        assertThrows(BoardNotFoundException.class,
                () -> service.getBoard("missing-board"));
    }

    @Test
    void shouldGenerateADistinctIdentityForEachBoard() {
        Board first = service.createBoard("Session A");
        Board second = service.createBoard("Session A");

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void shouldCreateBoardWithoutElements() {
        Board created = service.createBoard("Empty Session");

        assertTrue(created.elements().isEmpty());
    }

    @Test
    void shouldRejectBlankBoardName() {
        assertThrows(IllegalArgumentException.class, () -> service.createBoard("  "));
    }

    @Test
    void shouldReplaceContentKeepingTheSameIdentity() {
        Board created = service.createBoard("Draft");
        BoardElement element = new BoardElement("e1", ElementType.RECTANGLE, 10, 20, 100, 50, "", null, null);

        Board replaced = service.replaceBoard(created.id(), "Final", List.of(element));

        assertEquals(created.id(), replaced.id());
        assertEquals("Final", replaced.name());
        assertEquals(List.of(element), replaced.elements());
        assertEquals(replaced, service.getBoard(created.id()));
    }

    @Test
    void shouldNotCreateBoardOnReplaceOfUnknownId() {
        assertThrows(BoardNotFoundException.class,
                () -> service.replaceBoard("missing-board", "Any", List.of()));
        assertThrows(BoardNotFoundException.class,
                () -> service.getBoard("missing-board"));
    }

    @Test
    void shouldRejectDuplicatedElementIds() {
        Board created = service.createBoard("Draft");
        BoardElement one = new BoardElement("e1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);
        BoardElement duplicated = new BoardElement("e1", ElementType.TEXT, 5, 5, 10, 10, "hello", null, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replaceBoard(created.id(), "Draft", List.of(one, duplicated)));

        assertTrue(error.getMessage().contains("e1"));
    }

    @Test
    void shouldLeaveBoardUntouchedWhenReplacementIsInvalid() {
        Board created = service.createBoard("Draft");

        assertThrows(IllegalArgumentException.class,
                () -> service.replaceBoard(created.id(), " ", List.of()));

        assertEquals(created, service.getBoard(created.id()));
    }
}