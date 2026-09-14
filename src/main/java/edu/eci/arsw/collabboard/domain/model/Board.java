package edu.eci.arsw.collabboard.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Board(String id, String name, List<BoardElement> elements) {
    public Board {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Board id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Board name is required");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);

        Set<String> elementIds = new HashSet<>();
        for (BoardElement element : elements) {
            elementIds.add(element.id());
        }
        for (BoardElement element : elements) {
            if (element.type() == ElementType.CONNECTOR) {
                if (!elementIds.contains(element.sourceId())) {
                    throw new IllegalArgumentException(
                            "Connector references missing source element: " + element.sourceId());
                }
                if (!elementIds.contains(element.targetId())) {
                    throw new IllegalArgumentException(
                            "Connector references missing target element: " + element.targetId());
                }
            }
        }
    }
}