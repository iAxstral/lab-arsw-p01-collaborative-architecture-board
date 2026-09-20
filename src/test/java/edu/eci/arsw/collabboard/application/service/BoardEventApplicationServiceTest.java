package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the collaboration use case, wired against the in-memory
 * adapter through the port (no Spring context).
 */
class BoardEventApplicationServiceTest {

    private static final String BOARD_ID = "board-1";

    private InMemoryBoardRepository repository;
    private BoardEventApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBoardRepository();
        service = new BoardEventApplicationService(repository);
        repository.save(new Board(BOARD_ID, "Demo", List.of(
                rectangle("a", 10, 20),
                text("b", 200, 20),
                rectangle("c", 400, 20),
                connector("ab", "a", "b"),
                connector("bc", "b", "c"))));
    }

    private static BoardElement rectangle(String id, double x, double y) {
        return new BoardElement(id, ElementType.RECTANGLE, x, y, 100, 60, "rect " + id, null, null);
    }

    private static BoardElement text(String id, double x, double y) {
        return new BoardElement(id, ElementType.TEXT, x, y, 100, 30, "text " + id, null, null);
    }

    private static BoardElement connector(String id, String source, String target) {
        return new BoardElement(id, ElementType.CONNECTOR, 0, 0, 0, 0, "", source, target);
    }

    private static BoardEvent event(BoardEventType type, BoardEventPayload payload) {
        return event(BOARD_ID, type, payload);
    }

    private static BoardEvent event(String boardId, BoardEventType type, BoardEventPayload payload) {
        return new BoardEvent("evt-1", boardId, type, "client-a", Instant.parse("2026-09-01T12:30:00Z"), payload);
    }

    private static BoardEventPayload element(BoardElement element) {
        return new BoardEventPayload(element, null, null, null);
    }

    private static BoardEventPayload move(String elementId, Double x, Double y) {
        return new BoardEventPayload(null, elementId, x, y);
    }

    private static BoardEventPayload delete(String elementId) {
        return new BoardEventPayload(null, elementId, null, null);
    }

    private Board stored() {
        return repository.findById(BOARD_ID).orElseThrow();
    }

    private BoardElement storedElement(String id) {
        return stored().elements().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private boolean exists(String id) {
        return stored().elements().stream().anyMatch(e -> e.id().equals(id));
    }

    // ---- common ----

    @Test
    void unknownBoardIsRejectedWithBoardNotFound() {
        var event = event("missing-board", BoardEventType.ELEMENT_MOVED, move("a", 1.0, 2.0));

        assertThrows(BoardNotFoundException.class, () -> service.apply(event));
    }

    // ---- ELEMENT_CREATED ----

    @Test
    void createdAddsTheElementAndReturnsTheAcceptedEvent() {
        var event = event(BoardEventType.ELEMENT_CREATED, element(rectangle("d", 50, 60)));

        var accepted = service.apply(event);

        assertEquals(event, accepted);
        assertEquals(6, stored().elements().size());
        assertEquals(50, storedElement("d").x());
    }

    @Test
    void createdWithDuplicatedIdIsIgnored() {
        var duplicate = new BoardElement("a", ElementType.RECTANGLE, 999, 999, 5, 5, "other", null, null);
        var before = stored();

        var accepted = service.apply(event(BoardEventType.ELEMENT_CREATED, element(duplicate)));

        assertEquals(BoardEventType.ELEMENT_CREATED, accepted.type());
        assertEquals(before, stored());
        assertEquals(10, storedElement("a").x());
    }

    @Test
    void createdWithoutElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_CREATED, delete("d"))));
    }

    @Test
    void createdWithConnectorTypeIsRejected() {
        var before = stored();

        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_CREATED, element(connector("ac", "a", "c")))));
        assertEquals(before, stored());
    }

    // ---- ELEMENT_MOVED ----

    @Test
    void movedChangesOnlyThePosition() {
        service.apply(event(BoardEventType.ELEMENT_MOVED, move("a", 420.5, 180.0)));

        var moved = storedElement("a");
        assertEquals(420.5, moved.x());
        assertEquals(180.0, moved.y());
        assertEquals(100, moved.width());
        assertEquals("rect a", moved.text());
    }

    @Test
    void movedOnAConnectorIsRejected() {
        var before = stored();

        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_MOVED, move("ab", 1.0, 2.0))));
        assertEquals(before, stored());
    }

    @Test
    void movedUnknownElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_MOVED, move("zzz", 1.0, 2.0))));
    }

    @Test
    void movedWithoutCoordinatesIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_MOVED, move("a", null, 2.0))));
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_MOVED, move("a", 1.0, null))));
    }

    @Test
    void appliedTwiceMovedLeavesTheSameBoard() {
        var event = event(BoardEventType.ELEMENT_MOVED, move("a", 42.0, 24.0));

        service.apply(event);
        var afterFirst = stored();
        service.apply(event);

        assertEquals(afterFirst, stored());
    }

    // ---- ELEMENT_UPDATED ----

    @Test
    void updatedChangesOnlyTheTextAndReturnsTheServerElement() {
        var staleClientCopy = new BoardElement("a", ElementType.RECTANGLE, 777, 777, 1, 1, "New label", null, null);

        var accepted = service.apply(event(BoardEventType.ELEMENT_UPDATED, element(staleClientCopy)));

        var updated = storedElement("a");
        assertEquals("New label", updated.text());
        assertEquals(10, updated.x());
        assertEquals(100, updated.width());
        assertEquals(updated, accepted.payload().element());
        assertEquals("a", accepted.payload().elementId());
    }

    @Test
    void updatedUnknownElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_UPDATED, element(rectangle("zzz", 0, 0)))));
    }

    @Test
    void updatedWithoutElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_UPDATED, delete("a"))));
    }

    // ---- ELEMENT_DELETED ----

    @Test
    void deletedRemovesTheElementAndTheConnectorsThatReferenceIt() {
        service.apply(event(BoardEventType.ELEMENT_DELETED, delete("a")));

        assertTrue(!exists("a"));
        assertTrue(!exists("ab"));
        assertTrue(exists("b"));
        assertTrue(exists("bc"));
    }

    @Test
    void deletedConnectorRemovesOnlyTheConnector() {
        service.apply(event(BoardEventType.ELEMENT_DELETED, delete("ab")));

        assertTrue(!exists("ab"));
        assertTrue(exists("a"));
        assertTrue(exists("b"));
        assertTrue(exists("bc"));
    }

    @Test
    void deletedUnknownElementIsANoOp() {
        var before = stored();

        service.apply(event(BoardEventType.ELEMENT_DELETED, delete("zzz")));

        assertEquals(before, stored());
    }

    @Test
    void deletedWithoutElementIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.ELEMENT_DELETED, element(rectangle("a", 0, 0)))));
    }

    // ---- CONNECTOR_CREATED ----

    @Test
    void connectorCreatedAddsAValidConnector() {
        var accepted = service.apply(event(BoardEventType.CONNECTOR_CREATED, element(connector("ac", "a", "c"))));

        assertEquals("ac", accepted.payload().element().id());
        assertEquals(ElementType.CONNECTOR, storedElement("ac").type());
        assertEquals("a", storedElement("ac").sourceId());
        assertEquals("c", storedElement("ac").targetId());
    }

    @Test
    void connectorCreatedWithMissingEndpointIsRejectedAndNothingIsSaved() {
        var before = stored();

        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.CONNECTOR_CREATED, element(connector("ax", "a", "missing")))));
        assertEquals(before, stored());
    }

    @Test
    void connectorCreatedPointingToAnotherConnectorIsRejected() {
        var before = stored();

        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.CONNECTOR_CREATED, element(connector("x", "a", "ab")))));
        assertEquals(before, stored());
    }

    @Test
    void connectorCreatedWithANonConnectorElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(event(BoardEventType.CONNECTOR_CREATED, element(rectangle("d", 0, 0)))));
    }

    @Test
    void connectorCreatedWithDuplicatedIdIsIgnored() {
        var before = stored();

        service.apply(event(BoardEventType.CONNECTOR_CREATED, element(connector("ab", "a", "c"))));

        assertEquals(before, stored());
        assertEquals("b", storedElement("ab").targetId());
    }

    @Test
    void acceptedEventKeepsItsIdentityFields() {
        var event = event(BoardEventType.ELEMENT_MOVED, move("a", 1.0, 2.0));

        var accepted = service.apply(event);

        assertSame(event, accepted);
    }
}
