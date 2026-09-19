package edu.eci.arsw.collabboard.infrastructure.web.rest;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(BoardNotFoundException.class) ResponseEntity<ApiError> nf(BoardNotFoundException e,HttpServletRequest r){ return error(HttpStatus.NOT_FOUND,"BOARD_NOT_FOUND",e.getMessage(),r.getRequestURI()); }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e,HttpServletRequest r){ String m=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Invalid request"); return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",m,r.getRequestURI()); }
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> domain(IllegalArgumentException e,HttpServletRequest r){ return error(HttpStatus.BAD_REQUEST,"INVALID_INPUT",e.getMessage(),r.getRequestURI()); }
 private ResponseEntity<ApiError> error(HttpStatus s,String c,String m,String p){ return ResponseEntity.status(s).body(new ApiError(Instant.now(),s.value(),c,m,p)); }
}
