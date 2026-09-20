package edu.eci.arsw.collabboard.infrastructure.web.ws;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.service.BoardEventApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * STOMP adapter for collaboration events. Like the REST controller it stays
 * thin: it checks the destination, delegates to the application service and
 * publishes only what the service accepted. A rejected event is logged and
 * dropped; nothing is sent back to subscribers.
 */
@Controller
public class BoardWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(BoardWebSocketController.class);

    private final BoardEventApplicationService service;
    private final SimpMessagingTemplate messagingTemplate;

    public BoardWebSocketController(BoardEventApplicationService service,
                                    SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/boards/{boardId}/events")
    public void handle(@DestinationVariable String boardId, BoardEvent event) {
        if (!boardId.equals(event.boardId())) {
            throw new IllegalArgumentException(
                    "Destination boardId '" + boardId + "' does not match event boardId '" + event.boardId() + "'");
        }
        BoardEvent accepted = service.apply(event);
        messagingTemplate.convertAndSend("/topic/boards/" + boardId, accepted);
    }

    @MessageExceptionHandler({IllegalArgumentException.class, BoardNotFoundException.class,
            MessageConversionException.class})
    public void onRejectedEvent(Exception exception) {
        log.warn("Rejected board event: {}", exception.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    public void onUnexpectedFailure(Exception exception) {
        log.error("Unexpected failure while handling a board event", exception);
    }
}
