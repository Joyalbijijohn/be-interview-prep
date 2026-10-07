package com.interview.prep.task;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TaskRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsTaskWithDefaultStatusAndCreatedDate() throws Exception {
        create("""
                {"title":"Write report","description":"Q3","dueDate":"%s"}""".formatted(LocalDate.now().plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title", is("Write report")))
                .andExpect(jsonPath("$.status", is("TO_DO")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void acceptsDueDateOfToday() throws Exception {
        create("""
                {"title":"Today","dueDate":"%s"}""".formatted(LocalDate.now()))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptsTitleOfExactlyHundredCharacters() throws Exception {
        create("{\"title\":\"" + "a".repeat(100) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidInputWithFieldLevelMessages() throws Exception {
        create("""
                {"title":"%s","dueDate":"%s"}""".formatted("a".repeat(101), LocalDate.now().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.title", is("title must be at most 100 characters")))
                .andExpect(jsonPath("$.fieldErrors.dueDate", is("dueDate cannot be in the past")));
    }

    @Test
    void rejectsMissingTitle() throws Exception {
        create("{\"description\":\"no title\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title", is("title is required")));
    }

    @Test
    void rejectsUnknownStatusAndMalformedJson() throws Exception {
        create("{\"title\":\"x\",\"status\":\"BLOCKED\"}").andExpect(status().isBadRequest());
        create("{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Malformed or invalid request body")));
    }

    @Test
    void returnsNotFoundForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Task 999 not found")));
        mockMvc.perform(delete("/api/tasks/999")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/999").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConsistentErrorForInvalidIdAndUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
        mockMvc.perform(post("/api/tasks/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)));
    }

    @Test
    void getsUpdatesAndDeletesTask() throws Exception {
        long id = createAndGetId("{\"title\":\"Old\"}");

        mockMvc.perform(get("/api/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Old")));

        mockMvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("New")))
                .andExpect(jsonPath("$.status", is("DONE")));

        mockMvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void filtersListByStatus() throws Exception {
        createAndGetId("{\"title\":\"a\",\"status\":\"TO_DO\"}");
        createAndGetId("{\"title\":\"b\",\"status\":\"DONE\"}");
        createAndGetId("{\"title\":\"c\",\"status\":\"DONE\"}");

        mockMvc.perform(get("/api/tasks")).andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status", is("DONE")));
        mockMvc.perform(get("/api/tasks").param("status", "IN_PROGRESS")).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/tasks").param("status", "BOGUS")).andExpect(status().isBadRequest());
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createAndGetId(String json) throws Exception {
        String body = create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
