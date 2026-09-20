package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardEventContractTest {

    @Test
    void eventRequiresBoardAndActorIdentity() {
        var payload = new BoardEventPayload(null, "rect-1", 10.0, 20.0);
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "", BoardEventType.ELEMENT_MOVED, "client-a", Instant.now(), payload));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "", Instant.now(), payload));
    }

    @Test
    void eventKeepsExplicitTypeAndPayload() {
        var payload = new BoardEventPayload(null, "rect-1", 10.0, 20.0);
        var event = new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", Instant.parse("2026-09-01T12:30:00Z"), payload);
        assertEquals(BoardEventType.ELEMENT_MOVED, event.type());
        assertEquals("rect-1", event.payload().elementId());
    }
}
