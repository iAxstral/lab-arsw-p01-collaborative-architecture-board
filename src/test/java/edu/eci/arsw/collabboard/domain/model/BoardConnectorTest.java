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
}