package com.interview.prep.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interview.prep.product.Product;
import com.interview.prep.product.ProductRepository;
import com.interview.prep.product.ProductService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    private static final long USER = 1L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    ProductRepository products;

    @Autowired
    CacheManager cacheManager;

    private Long productId;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        cacheManager.getCache(ProductService.CACHE).clear();
        productId = newProduct(10);
    }

    @Test
    void fiftySimultaneousOrdersForStockOfTenSucceedExactlyTenTimes() throws Exception {
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long user = i;
            requests.add(() -> place(user, UUID.randomUUID().toString(), body(productId, 1)).andReturn()
                    .getResponse().getStatus());
        }

        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(10);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(40);
        assertThat(stockOf(productId)).isZero();
        assertThat(orders.count()).isEqualTo(10);
    }

    @Test
    void simultaneousRetriesOfTheSameRequestCreateOneOrder() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Callable<String>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requests.add(() -> {
                var response = place(USER, key, body(productId, 2)).andReturn().getResponse();
                return response.getStatus() + ":" + (Object) JsonPath.read(response.getContentAsString(), "$.id");
            });
        }

        List<String> results = runConcurrently(requests);

        assertThat(results.stream().filter(r -> r.startsWith("201:"))).hasSize(1);
        assertThat(results.stream().filter(r -> r.startsWith("200:"))).hasSize(19);
        assertThat(results.stream().map(r -> r.substring(4)).distinct()).hasSize(1);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    void retryAfterSuccessReturnsSameOrderWithoutReservingStockAgain() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = place(USER, key, body(productId, 3)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String retry = place(USER, key, body(productId, 3)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat((Object) JsonPath.read(retry, "$.id")).isEqualTo((Object) JsonPath.read(first, "$.id"));
        assertThat(orders.count()).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(7);
    }

    @Test
    void retryOfRejectedOrderIsNotStoredAndCanSucceedLater() throws Exception {
        String key = UUID.randomUUID().toString();
        place(USER, key, body(productId, 11)).andExpect(status().isConflict());
        assertThat(orders.count()).isZero();

        place(USER, key, body(productId, 5)).andExpect(status().isCreated());
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void reusingKeyWithDifferentRequestIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        place(USER, key, body(productId, 1)).andExpect(status().isCreated());

        place(USER, key, body(productId, 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Idempotency-Key was already used with a different request")));
        assertThat(stockOf(productId)).isEqualTo(9);
    }

    @Test
    void sameKeyFromDifferentUsersAreIndependentOrders() throws Exception {
        String key = UUID.randomUUID().toString();
        place(1L, key, body(productId, 1)).andExpect(status().isCreated());
        place(2L, key, body(productId, 1)).andExpect(status().isCreated());
        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    void orderIsAllOrNothingWhenOneItemHasInsufficientStock() throws Exception {
        Long scarce = newProduct(1);

        place(USER, UUID.randomUUID().toString(), body(productId, 4, scarce, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Insufficient stock for product " + scarce)));

        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        assertThat(orders.count()).isZero();
    }

    @Test
    void multiItemOrderReservesEveryItem() throws Exception {
        Long other = newProduct(5);

        place(USER, UUID.randomUUID().toString(), body(productId, 4, other, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PLACED")))
                .andExpect(jsonPath("$.items.length()", is(2)));

        assertThat(stockOf(productId)).isEqualTo(6);
        assertThat(stockOf(other)).isEqualTo(3);
    }

    @Test
    void duplicateLinesForSameProductAreCombined() throws Exception {
        place(USER, UUID.randomUUID().toString(), body(productId, 4, productId, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()", is(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(7)));
        assertThat(stockOf(productId)).isEqualTo(3);

        place(USER, UUID.randomUUID().toString(), body(productId, 2, productId, 2)).andExpect(status().isConflict());
        assertThat(stockOf(productId)).isEqualTo(3);
    }

    @Test
    void ordersWithItemsInOppositeOrderNeverDeadlock() throws Exception {
        Long other = newProduct(1000);
        Long first = newProduct(1000);
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long user = i;
            String json = i % 2 == 0 ? body(first, 1, other, 1) : body(other, 1, first, 1);
            requests.add(() -> place(user, UUID.randomUUID().toString(), json).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).allMatch(s -> s == 201);
        assertThat(stockOf(first)).isEqualTo(960);
        assertThat(stockOf(other)).isEqualTo(960);
    }

    @Test
    void exactStockCanBeOrderedAndThenNothingMore() throws Exception {
        place(USER, UUID.randomUUID().toString(), body(productId, 10)).andExpect(status().isCreated());
        assertThat(stockOf(productId)).isZero();
        place(USER, UUID.randomUUID().toString(), body(productId, 1)).andExpect(status().isConflict());
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        place(USER, UUID.randomUUID().toString(), "{\"items\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items", is("items must not be empty")));
        place(USER, UUID.randomUUID().toString(), body(productId, 0)).andExpect(status().isBadRequest());
        place(USER, UUID.randomUUID().toString(), "{}").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body(productId, 1)))
                .andExpect(status().isBadRequest());
        place(USER, " ", body(productId, 1)).andExpect(status().isBadRequest());
        place(USER, "k".repeat(101), body(productId, 1)).andExpect(status().isBadRequest());
        assertThat(stockOf(productId)).isEqualTo(10);
    }

    @Test
    void unknownProductReturnsNotFoundAndReservesNothing() throws Exception {
        place(USER, UUID.randomUUID().toString(), body(productId, 1, 999999L, 1))
                .andExpect(status().isNotFound());
        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body(productId, 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingReturnsStock() throws Exception {
        Long orderId = placeAndGetId(USER, body(productId, 4));
        assertThat(stockOf(productId)).isEqualTo(6);

        cancel(USER, orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(stockOf(productId)).isEqualTo(10);
    }

    @Test
    void cancellingTwiceReturnsStockOnlyOnce() throws Exception {
        Long orderId = placeAndGetId(USER, body(productId, 4));

        cancel(USER, orderId).andExpect(status().isOk());
        cancel(USER, orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(stockOf(productId)).isEqualTo(10);
    }

    @Test
    void simultaneousCancellationsReturnStockOnlyOnce() throws Exception {
        Long orderId = placeAndGetId(USER, body(productId, 3));
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requests.add(() -> cancel(USER, orderId).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(stockOf(productId)).isEqualTo(10);
    }

    @Test
    void cancelledStockCanBeOrderedAgain() throws Exception {
        Long orderId = placeAndGetId(USER, body(productId, 10));
        cancel(USER, orderId).andExpect(status().isOk());

        place(2L, UUID.randomUUID().toString(), body(productId, 10)).andExpect(status().isCreated());
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    void cannotCancelAnotherUsersOrder() throws Exception {
        Long orderId = placeAndGetId(USER, body(productId, 4));

        cancel(2L, orderId).andExpect(status().isNotFound());
        cancel(USER, 999999L).andExpect(status().isNotFound());

        assertThat(stockOf(productId)).isEqualTo(6);
    }

    @Test
    void productLookupReflectsStockChangesFromOrdersAndCancellations() throws Exception {
        mockMvc.perform(get("/api/products/" + productId).with(jwt())).andExpect(jsonPath("$.stock", is(10)));

        Long orderId = placeAndGetId(USER, body(productId, 4));
        mockMvc.perform(get("/api/products/" + productId).with(jwt())).andExpect(jsonPath("$.stock", is(6)));

        cancel(USER, orderId).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/" + productId).with(jwt())).andExpect(jsonPath("$.stock", is(10)));
    }

    private Long newProduct(int stock) {
        return products.save(new Product("Item", "Test", new BigDecimal("9.99"), stock, 4.0, Instant.now())).getId();
    }

    private int stockOf(Long id) {
        return products.findById(id).orElseThrow().getStock();
    }

    private ResultActions place(long user, String key, String json) throws Exception {
        return mockMvc.perform(post("/api/orders").with(jwt().jwt(j -> j.subject(String.valueOf(user))))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cancel(long user, Long orderId) throws Exception {
        return mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                .with(jwt().jwt(j -> j.subject(String.valueOf(user)))));
    }

    private Long placeAndGetId(long user, String json) throws Exception {
        String response = place(user, UUID.randomUUID().toString(), json).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String body(Object... productAndQuantity) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"productId\":").append(productAndQuantity[i]).append(",\"quantity\":")
                    .append(productAndQuantity[i + 1]).append('}');
        }
        return "{\"items\":[" + items + "]}";
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }
}
