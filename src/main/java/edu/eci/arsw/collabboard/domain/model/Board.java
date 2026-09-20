package edu.eci.arsw.collabboard.domain.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Board(String id, String name, List<BoardElement> elements) {
    public Board {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Board id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Board name is required");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);

        Map<String, ElementType> typesById = new HashMap<>();
        for (BoardElement element : elements) {
            typesById.put(element.id(), element.type());
        }
        for (BoardElement element : elements) {
            if (element.type() == ElementType.CONNECTOR) {
                if (!typesById.containsKey(element.sourceId())) {
                    throw new IllegalArgumentException(
                            "Connector references missing source element: " + element.sourceId());
                }
                if (!typesById.containsKey(element.targetId())) {
                    throw new IllegalArgumentException(
                            "Connector references missing target element: " + element.targetId());
                }
                if (typesById.get(element.sourceId()) == ElementType.CONNECTOR) {
                    throw new IllegalArgumentException(
                            "Connector source cannot be another connector: " + element.sourceId());
                }
                if (typesById.get(element.targetId()) == ElementType.CONNECTOR) {
                    throw new IllegalArgumentException(
                            "Connector target cannot be another connector: " + element.targetId());
                }
            }
        }
    }
}