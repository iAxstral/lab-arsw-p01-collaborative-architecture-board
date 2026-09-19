package edu.eci.arsw.collabboard.application.exception;
public class BoardNotFoundException extends RuntimeException {
 public BoardNotFoundException(String id){ super("Board not found: "+id); }
}
