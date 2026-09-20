package edu.eci.arsw.collabboard.domain.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BoardConnectorTest {

    @Test
    void validConnectorReferencesExistingElements() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);
        var b = new BoardElement("b", ElementType.TEXT, 200, 0, 100, 30, "B", null, null);
        var c = new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "b");

        assertDoesNotThrow(() -> new Board("board", "Demo", List.of(a, b, c)));
    }

    @Test
    void connectorCannotReferenceMissingElement() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);
        var c = new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "missing");

        assertThrows(IllegalArgumentException.class, () -> new Board("board", "Demo", List.of(a, c)));
    }

    @Test
    void connectorEndpointsMustBeDifferent() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "a"));
    }

    @Test
    void connectorCannotReferenceMissingSource() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);
        var c = new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", "missing", "a");

        var error = assertThrows(IllegalArgumentException.class, () -> new Board("board", "Demo", List.of(a, c)));
        assertTrue(error.getMessage().contains("missing source"));
    }

    @Test
    void connectorRequiresSourceId() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", null, "b"));
    }

    @Test
    void connectorRequiresTargetId() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", null));
    }

    @Test
    void connectorCannotTargetAnotherConnector() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);
        var b = new BoardElement("b", ElementType.TEXT, 200, 0, 100, 30, "B", null, null);
        var first = new BoardElement("c1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "b");
        var second = new BoardElement("c2", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "c1");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new Board("board", "Demo", List.of(a, b, first, second)));
        assertTrue(error.getMessage().contains("target cannot be another connector"));
    }

    @Test
    void connectorCannotStartFromAnotherConnector() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", null, null);
        var b = new BoardElement("b", ElementType.TEXT, 200, 0, 100, 30, "B", null, null);
        var first = new BoardElement("c1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "b");
        var second = new BoardElement("c2", ElementType.CONNECTOR, 0, 0, 0, 0, "", "c1", "b");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new Board("board", "Demo", List.of(a, b, first, second)));
        assertTrue(error.getMessage().contains("source cannot be another connector"));
    }

    @Test
    void nonConnectorCannotDefineSourceOrTarget() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", "x", null));
        assertEquals("Only CONNECTOR elements may define sourceId/targetId", error.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("t", ElementType.TEXT, 0, 0, 100, 30, "T", null, "y"));
    }

    @Test
    void nonConnectorBlankEndpointsAreNormalizedToNull() {
        var a = new BoardElement("a", ElementType.RECTANGLE, 0, 0, 100, 60, "A", "", "  ");

        assertNull(a.sourceId());
        assertNull(a.targetId());
    }
}