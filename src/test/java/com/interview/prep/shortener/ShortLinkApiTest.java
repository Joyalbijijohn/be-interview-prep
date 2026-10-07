package com.interview.prep.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ShortLinkApiTest {

    private static final String TARGET = "https://example.com/some/long/path?x=1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShortLinkRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void shortensUrlAndReturnsCodeAndShortUrl() throws Exception {
        String body = shorten(TARGET, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl", is(TARGET)))
                .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(body, "$.code");
        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("http://localhost/" + code);
    }

    @Test
    void generatesUrlSafeCodeOfAtMostEightCharacters() throws Exception {
        String code = codeOf(shorten(TARGET, null));
        assertThat(code).matches("[0-9A-Za-z]{1,8}");
    }

    @Test
    void shorteningSameUrlTwiceCreatesTwoIndependentLinks() throws Exception {
        String first = codeOf(shorten(TARGET, null));
        String second = codeOf(shorten(TARGET, null));
        assertThat(first).isNotEqualTo(second);

        mockMvc.perform(get("/" + first));
        mockMvc.perform(get("/api/links/" + first + "/stats")).andExpect(jsonPath("$.visitCount", is(1)));
        mockMvc.perform(get("/api/links/" + second + "/stats")).andExpect(jsonPath("$.visitCount", is(0)));
    }

    @Test
    void redirectsToOriginalUrlAndCountsVisits() throws Exception {
        String code = codeOf(shorten(TARGET, null));

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", TARGET));
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        mockMvc.perform(get("/api/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl", is(TARGET)))
                .andExpect(jsonPath("$.visitCount", is(2)))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void rejectsInvalidUrls() throws Exception {
        for (String url : List.of("not a url", "ftp://example.com/file", "javascript:alert(1)", "http://", "   ")) {
            shorten(url, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.url").exists());
        }
        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url", is("url is required")));
    }

    @Test
    void rejectsExpiryInThePast() throws Exception {
        shorten(TARGET, Instant.now().minusSeconds(60))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expiresAt", is("expiresAt must be in the future")));
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/zzzzzzz")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/links/zzzzzzz/stats")).andExpect(status().isNotFound());
    }

    @Test
    void returnsGoneForExpiredCodeAndDoesNotCountTheVisit() throws Exception {
        String code = codeOf(shorten(TARGET, Instant.now().plusSeconds(3600)));
        jdbc.update("update short_link set expires_at = ? where code = ?",
                Timestamp.from(Instant.now().minusSeconds(5)), code);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status", is(410)));
        mockMvc.perform(get("/api/links/" + code + "/stats")).andExpect(jsonPath("$.visitCount", is(0)));
    }

    @Test
    void visitCountStaysAccurateUnderConcurrentVisits() throws Exception {
        String code = codeOf(shorten(TARGET, null));
        int visits = 100;
        ExecutorService pool = Executors.newFixedThreadPool(visits);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < visits; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(get("/" + code)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(302);
        }
        pool.shutdown();

        mockMvc.perform(get("/api/links/" + code + "/stats")).andExpect(jsonPath("$.visitCount", is(visits)));
    }

    private ResultActions shorten(String url, Instant expiresAt) throws Exception {
        String expiry = expiresAt == null ? "" : ",\"expiresAt\":\"" + expiresAt + "\"";
        return mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"" + expiry + "}"));
    }

    private String codeOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.code");
    }
}
