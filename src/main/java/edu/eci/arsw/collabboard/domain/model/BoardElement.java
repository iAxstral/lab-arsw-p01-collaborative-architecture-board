package edu.eci.arsw.collabboard.domain.model;

public record BoardElement(String id, ElementType type, double x, double y, double width, double height, String text, String sourceId, String targetId) {
 public BoardElement {
   if(id==null || id.isBlank()) throw new IllegalArgumentException("Element id is required");
   if(type==null) throw new IllegalArgumentException("Element type is required");
   if(width<0 || height<0) throw new IllegalArgumentException("Element dimensions cannot be negative");
   text = text == null ? "" : text;
   if(type==ElementType.CONNECTOR){
     if(sourceId==null || sourceId.isBlank() || targetId==null || targetId.isBlank()) throw new IllegalArgumentException("Connector sourceId and targetId are required");
     if(sourceId.equals(targetId)) throw new IllegalArgumentException("Connector endpoints must be different");
   }
 }
}
