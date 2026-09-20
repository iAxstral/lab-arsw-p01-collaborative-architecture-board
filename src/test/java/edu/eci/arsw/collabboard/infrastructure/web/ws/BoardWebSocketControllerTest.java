package edu.eci.arsw.collabboard.infrastructure.web.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real STOMP round trip over a random port. Frames are exchanged as raw JSON
 * strings so the tests also document exactly what travels on the wire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class BoardWebSocketControllerTest {

    private static final String MOVE_TIME = "2026-09-01T12:30:00Z";
    private static final String PROBE = "{\"probe\":true}";

    @LocalServerPort
    private int port;

    @Autowired
    private BoardApplicationService boards;

    @Autowired
    private BoardRepository repository;

    private final ObjectMapper json = new ObjectMapper();
    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        client.stop();
    }

    private StompSession connect() throws Exception {
        return connectFrom("http://localhost:" + port);
    }

    private StompSession connectFrom(String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    /** SUBSCRIBE has no reliable receipt on the simple broker: probe the topic until it echoes back. */
    private BlockingQueue<String> subscribe(StompSession session, String boardId) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch ready = new CountDownLatch(1);
        String topic = "/topic/boards/" + boardId;
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (PROBE.equals(payload)) {
                    ready.countDown();
                } else {
                    received.add((String) payload);
                }
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ready.await(100, TimeUnit.MILLISECONDS)) {
            assertTrue(System.nanoTime() < deadline, "the subscription never became active");
            StompHeaders probe = new StompHeaders();
            probe.setDestination(topic);
            probe.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(probe, PROBE);
        }
        return received;
    }

    private void send(StompSession session, String destinationBoardId, String body) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/boards/" + destinationBoardId + "/events");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, body);
    }

    private static String event(String eventId, String boardId, String type, String payload) {
        return """
                {"eventId":"%s","boardId":"%s","type":"%s","actorId":"client-a","occurredAt":"%s","payload":%s}
                """.formatted(eventId, boardId, type, MOVE_TIME, payload);
    }

    private static String created(String elementId) {
        return """
                {"element":{"id":"%s","type":"RECTANGLE","x":10,"y":20,"width":100,"height":60,"text":"hi","sourceId":null,"targetId":null},"elementId":null,"x":null,"y":null}
                """.formatted(elementId);
    }

    private static String move(String elementId, double x, double y) {
        return """
                {"element":null,"elementId":"%s","x":%s,"y":%s}
                """.formatted(elementId, x, y);
    }

    private JsonNode next(BlockingQueue<String> queue) throws Exception {
        String body = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(body, "no event was broadcast");
        return json.readTree(body);
    }

    private Board newBoard() {
        return boards.createBoard("Demo");
    }

    private Board boardWithConnector() {
        Board board = newBoard();
        return boards.replaceBoard(board.id(), "Demo", List.of(
                new BoardElement("a", ElementType.RECTANGLE, 10, 20, 100, 60, "a", null, null),
                new BoardElement("b", ElementType.RECTANGLE, 300, 20, 100, 60, "b", null, null),
                new BoardElement("ab", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "b")));
    }

    @Test
    void acceptedEventIsAppliedAndBroadcastToSubscribers() throws Exception {
        Board board = newBoard();
        StompSession subscriber = connect();
        StompSession sender = connect();
        BlockingQueue<String> received = subscribe(subscriber, board.id());

        send(sender, board.id(), event("evt-1", board.id(), "ELEMENT_CREATED", created("r1")));

        JsonNode broadcast = next(received);
        assertEquals("evt-1", broadcast.get("eventId").asText());
        assertEquals(board.id(), broadcast.get("boardId").asText());
        assertEquals("ELEMENT_CREATED", broadcast.get("type").asText());
        assertEquals("client-a", broadcast.get("actorId").asText());
        assertEquals("r1", broadcast.get("payload").get("element").get("id").asText());
        assertEquals(1, repository.findById(board.id()).orElseThrow().elements().size());
    }

    @Test
    void occurredAtIsBroadcastAsAnIsoString() throws Exception {
        Board board = newBoard();
        StompSession session = connect();
        BlockingQueue<String> received = subscribe(session, board.id());

        send(session, board.id(), event("evt-1", board.id(), "ELEMENT_CREATED", created("r1")));

        JsonNode occurredAt = next(received).get("occurredAt");
        assertTrue(occurredAt.isTextual(), "occurredAt was serialized as " + occurredAt.getNodeType() + ": " + occurredAt);
        assertEquals(MOVE_TIME, occurredAt.asText());
    }

    @Test
    void rejectedEventIsNotBroadcastAndTheSessionKeepsWorking(CapturedOutput output) throws Exception {
        Board board = boardWithConnector();
        StompSession session = connect();
        BlockingQueue<String> received = subscribe(session, board.id());

        send(session, board.id(), event("evt-bad", board.id(), "ELEMENT_MOVED", move("ab", 1, 2)));
        send(session, board.id(), event("evt-good", board.id(), "ELEMENT_MOVED", move("a", 42, 24)));

        assertEquals("evt-good", next(received).get("eventId").asText());
        assertNull(received.poll(500, TimeUnit.MILLISECONDS), "the rejected event must not be broadcast");
        Board stored = repository.findById(board.id()).orElseThrow();
        assertEquals(42, stored.elements().get(0).x());
        assertTrue(output.getOut().contains("Rejected board event") || output.getErr().contains("Rejected board event"));
    }

    @Test
    void eventWhoseBoardIdDiffersFromTheDestinationIsNotBroadcast() throws Exception {
        Board destination = newBoard();
        Board other = newBoard();
        StompSession session = connect();
        BlockingQueue<String> destinationQueue = subscribe(session, destination.id());
        BlockingQueue<String> otherQueue = subscribe(session, other.id());

        send(session, destination.id(), event("evt-mismatch", other.id(), "ELEMENT_CREATED", created("r1")));
        send(session, destination.id(), event("evt-ok", destination.id(), "ELEMENT_CREATED", created("r2")));

        assertEquals("evt-ok", next(destinationQueue).get("eventId").asText());
        assertNull(otherQueue.poll(500, TimeUnit.MILLISECONDS));
        assertTrue(repository.findById(other.id()).orElseThrow().elements().isEmpty());
    }

    @Test
    void eventForAnUnknownBoardIsNotBroadcast() throws Exception {
        Board board = newBoard();
        StompSession session = connect();
        BlockingQueue<String> ghostQueue = subscribe(session, "ghost-board");
        BlockingQueue<String> boardQueue = subscribe(session, board.id());

        send(session, "ghost-board", event("evt-ghost", "ghost-board", "ELEMENT_CREATED", created("r1")));
        send(session, board.id(), event("evt-ok", board.id(), "ELEMENT_CREATED", created("r2")));

        assertEquals("evt-ok", next(boardQueue).get("eventId").asText());
        assertNull(ghostQueue.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void malformedEventIsRejectedWithoutBreakingTheSession(CapturedOutput output) throws Exception {
        Board board = newBoard();
        StompSession session = connect();
        BlockingQueue<String> received = subscribe(session, board.id());

        send(session, board.id(), event("", board.id(), "ELEMENT_CREATED", created("r1")));
        send(session, board.id(), event("evt-ok", board.id(), "ELEMENT_CREATED", created("r2")));

        assertEquals("evt-ok", next(received).get("eventId").asText());
        assertNull(received.poll(500, TimeUnit.MILLISECONDS));
        assertTrue(output.getOut().contains("Rejected board event") || output.getErr().contains("Rejected board event"));
    }

    @Test
    void handshakeFromAForeignOriginIsRefused() {
        assertThrows(ExecutionException.class, () -> connectFrom("http://evil.example"));
    }
}
