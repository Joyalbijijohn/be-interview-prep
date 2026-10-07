package com.interview.prep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interview.prep.user.Role;
import com.interview.prep.user.User;
import com.interview.prep.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    private static final String PASSWORD = "S3curePassw0rd";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @Value("${app.jwt.secret}")
    String secret;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void registersUserWithRoleUserAndNeverReturnsPassword() throws Exception {
        register("Alice@Example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesPasswordAsBcryptHash() throws Exception {
        register("alice@example.com", PASSWORD).andExpect(status().isCreated());

        User stored = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void ignoresRoleSuppliedByClientOnRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mallory@example.com\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() throws Exception {
        register("alice@example.com", PASSWORD).andExpect(status().isCreated());
        register("ALICE@example.com", PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void rejectsInvalidRegistrationInput() throws Exception {
        register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", is("email must be valid")))
                .andExpect(jsonPath("$.fieldErrors.password", is("password must be between 8 and 72 characters")));
    }

    @Test
    void onlyOneOfManyConcurrentRegistrationsForSameEmailSucceeds() throws Exception {
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return register("race@example.com", PASSWORD).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 201) {
                created++;
            } else if (code == 409) {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(attempts - 1);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void loginIssuesFifteenMinuteToken() throws Exception {
        saveUser("alice@example.com", Role.USER);

        String body = login("alice@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(900)))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(900);
        assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("USER");
    }

    @Test
    void loginFailuresReturnSameGenericUnauthorizedForWrongPasswordAndUnknownUser() throws Exception {
        saveUser("alice@example.com", Role.USER);

        login("alice@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    void registeredUserCanLoginAndViewOwnProfile() throws Exception {
        register("alice@example.com", PASSWORD).andExpect(status().isCreated());
        String token = tokenFor("alice@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void requestWithoutTokenGetsJsonUnauthorized() throws Exception {
        for (String path : List.of("/api/users/me", "/api/users", "/api/tasks", "/api/links/abc/stats")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status", is(401)));
        }
    }

    @Test
    void invalidTokensGetJsonUnauthorized() throws Exception {
        saveUser("alice@example.com", Role.USER);
        Long id = users.findByEmail("alice@example.com").orElseThrow().getId();
        String expired = encode(jwtEncoder, id, Instant.now().minusSeconds(120), Instant.now().minusSeconds(30));
        String wrongKey = encode(new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))), id,
                Instant.now(), Instant.now().plusSeconds(600));
        String valid = tokenFor("alice@example.com");
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");

        for (String token : List.of("garbage", expired, wrongKey, tampered)) {
            mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status", is(401)));
        }
    }

    @Test
    void userRoleCannotAccessAdminEndpoint() throws Exception {
        saveUser("alice@example.com", Role.USER);

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor("alice@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", is("You do not have permission to access this resource")));
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        saveUser("alice@example.com", Role.USER);
        saveUser("root@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor("root@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private void saveUser(String email, Role role) {
        users.save(new User(email, passwordEncoder.encode(PASSWORD), role));
    }

    private String tokenFor(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String encode(JwtEncoder encoder, Long userId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
