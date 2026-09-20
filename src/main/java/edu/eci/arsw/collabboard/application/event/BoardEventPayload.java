package edu.eci.arsw.collabboard.application.event;

import edu.eci.arsw.collabboard.domain.model.BoardElement;

public record BoardEventPayload(
        BoardElement element,
        String elementId,
        Double x,
        Double y) {
}
