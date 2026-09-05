package edu.eci.arsw.collabboard.infrastructure.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-facing tests: they exercise the real contract through the whole stack
 * (controller, application service, in-memory adapter and error handler),
 * which is what the api-contract documentation must describe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createBoard(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    @Test
    void shouldCreateBoardAndReturnLocation() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Architecture Session\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/boards/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Architecture Session"))
                .andExpect(jsonPath("$.elements").isEmpty());
    }

    @Test
    void shouldRejectCreationWithoutName() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/boards"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldReturnStoredBoard() throws Exception {
        String boardId = createBoard("Readable Session");

        mockMvc.perform(get("/api/boards/{boardId}", boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(boardId))
                .andExpect(jsonPath("$.name").value("Readable Session"));
    }

    @Test
    void shouldReturnNotFoundForUnknownBoard() throws Exception {
        mockMvc.perform(get("/api/boards/{boardId}", "missing-board"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/boards/missing-board"));
    }

    @Test
    void shouldReplaceBoardContent() throws Exception {
        String boardId = createBoard("Draft");

        mockMvc.perform(put("/api/boards/{boardId}", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Final",
                                  "elements": [
                                    {"id":"e1","type":"RECTANGLE","x":10,"y":20,"width":100,"height":50,"text":""}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(boardId))
                .andExpect(jsonPath("$.name").value("Final"))
                .andExpect(jsonPath("$.elements[0].id").value("e1"))
                .andExpect(jsonPath("$.elements[0].type").value("RECTANGLE"));

        mockMvc.perform(get("/api/boards/{boardId}", boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements.length()").value(1));
    }

    @Test
    void shouldRejectReplaceWithoutElements() throws Exception {
        String boardId = createBoard("Draft");

        mockMvc.perform(put("/api/boards/{boardId}", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Final\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("elements")));
    }

    @Test
    void shouldRejectReplaceWithDuplicatedElementIds() throws Exception {
        String boardId = createBoard("Draft");

        mockMvc.perform(put("/api/boards/{boardId}", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Final",
                                  "elements": [
                                    {"id":"e1","type":"RECTANGLE","x":0,"y":0,"width":10,"height":10,"text":""},
                                    {"id":"e1","type":"TEXT","x":5,"y":5,"width":10,"height":10,"text":"hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(containsString("e1")));
    }

    @Test
    void shouldRejectReplaceWithInvalidElement() throws Exception {
        String boardId = createBoard("Draft");

        mockMvc.perform(put("/api/boards/{boardId}", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Final",
                                  "elements": [
                                    {"id":"e1","type":"RECTANGLE","x":0,"y":0,"width":-10,"height":10,"text":""}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(containsString("dimensions")));
    }

    @Test
    void shouldReturnNotFoundWhenReplacingUnknownBoard() throws Exception {
        mockMvc.perform(put("/api/boards/{boardId}", "missing-board")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Final\",\"elements\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"));
    }
}
