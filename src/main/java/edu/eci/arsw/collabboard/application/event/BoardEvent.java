package edu.eci.arsw.collabboard.application.event;

import java.time.Instant;

public record BoardEvent(
        String eventId,
        String boardId,
        BoardEventType type,
        String actorId,
        Instant occurredAt,
        BoardEventPayload payload) {

    public BoardEvent {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (boardId == null || boardId.isBlank()) throw new IllegalArgumentException("boardId is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
    }
}
