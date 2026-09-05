package edu.eci.arsw.collabboard.infrastructure.web.rest;

import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Single place where an exception becomes an HTTP response.
 *
 * <p>Every error leaves the application with the same {@link ApiError} shape,
 * so a client can rely on {@code code} instead of parsing free text, and no
 * stack trace is ever exposed.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<ApiError> boardNotFound(BoardNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    /**
     * Bean Validation failures on the request records. All field errors are
     * reported, not only the first one, so the client can fix the payload in a
     * single round trip.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Invalid request";
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request.getRequestURI());
    }

    /**
     * Domain invariants (see the compact constructors of Board and
     * BoardElement) and application rules such as duplicated element ids.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidDomainInput(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request.getRequestURI());
    }

    /**
     * Unreadable body: malformed JSON, an unknown ElementType, or a domain
     * invariant that failed while Jackson was instantiating a record. The
     * cause is unwrapped so a rejected invariant keeps its own message and
     * code instead of being reported as generic malformed JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException invariant) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                        invariant.getMessage(), request.getRequestURI());
            }
            cause = cause.getCause();
        }
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON", request.getRequestURI());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> notFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "No resource for " + request.getRequestURI(), request.getRequestURI());
    }

    /**
     * Last resort: an unexpected failure must not leak internals to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error while processing the request", request.getRequestURI());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, path
        ));
    }
}
