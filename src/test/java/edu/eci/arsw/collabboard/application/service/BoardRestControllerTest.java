package edu.eci.arsw.collabboard.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.web.rest.BoardRestController;
import edu.eci.arsw.collabboard.infrastructure.web.rest.ReplaceBoardRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-facing tests for the REST contract exposed under /api/boards.
 * The application service is mocked so these tests exercise only the
 * controller + validation + GlobalExceptionHandler chain (RA-01, RA-05).
 */
@WebMvcTest(BoardRestController.class)
class BoardRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BoardApplicationService service;

    @Test
    void createShouldReturn201WithCreatedBoard() throws Exception {
        Board created = new Board("board-1", "Architecture Session", List.of());
        when(service.createBoard("Architecture Session")).thenReturn(created);

        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Architecture Session\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("board-1"))
                .andExpect(jsonPath("$.name").value("Architecture Session"))
                .andExpect(jsonPath("$.elements").isArray());
    }

    @Test
    void createShouldReturn400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getShouldReturn200WithBoardWhenItExists() throws Exception {
        Board board = new Board("board-1", "Architecture Session", List.of());
        when(service.getBoard("board-1")).thenReturn(board);

        mockMvc.perform(get("/api/boards/board-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("board-1"))
                .andExpect(jsonPath("$.name").value("Architecture Session"));
    }

    @Test
    void getShouldReturn404WithUniformApiErrorWhenBoardDoesNotExist() throws Exception {
        when(service.getBoard("missing-board")).thenThrow(new BoardNotFoundException("missing-board"));

        mockMvc.perform(get("/api/boards/missing-board"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Board not found: missing-board"))
                .andExpect(jsonPath("$.path").value("/api/boards/missing-board"));
    }

    @Test
    void replaceShouldReturn200WithUpdatedBoardWhenItExists() throws Exception {
        List<BoardElement> elements = List.of(
                new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 100, 50, "note")
        );
        Board updated = new Board("board-1", "New name", elements);
        when(service.replaceBoard(eq("board-1"), anyString(), any())).thenReturn(updated);

        String body = objectMapper.writeValueAsString(
                new ReplaceBoardRequest("New name", elements));

        mockMvc.perform(put("/api/boards/board-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("board-1"))
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.elements[0].id").value("el-1"));

        verify(service).replaceBoard("board-1", "New name", elements);
    }

    @Test
    void replaceShouldReturn404WithUniformApiErrorWhenBoardDoesNotExist() throws Exception {
        when(service.replaceBoard(eq("missing-board"), anyString(), any()))
                .thenThrow(new BoardNotFoundException("missing-board"));

        String body = objectMapper.writeValueAsString(
                new ReplaceBoardRequest("New name", List.of()));

        mockMvc.perform(put("/api/boards/missing-board")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void replaceShouldReturn400WhenElementsAreMissing() throws Exception {
        mockMvc.perform(put("/api/boards/board-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
