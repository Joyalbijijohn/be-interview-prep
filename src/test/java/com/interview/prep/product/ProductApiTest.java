package com.interview.prep.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ProductApiTest {

    private static final String VALID_UPDATE = """
            {"name":"Updated Lamp","category":"Home","price":99.99,"stock":8,"rating":4.2}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductRepository repository;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    ProductSeeder seeder;

    private Product blueLamp;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        cacheManager.getCache(ProductService.CACHE).clear();
        Instant now = Instant.now();
        blueLamp = repository.save(new Product("Blue Lamp", "Home", price("20.00"), 5, 4.5, now.minus(3, ChronoUnit.DAYS)));
        repository.save(new Product("Red Lamp", "Home", price("35.00"), 0, 3.0, now.minus(2, ChronoUnit.DAYS)));
        repository.save(new Product("Java Book", "Books", price("45.50"), 10, 4.9, now.minus(1, ChronoUnit.DAYS)));
        repository.save(new Product("Smart Speaker", "Electronics", price("120.00"), 3, 4.0, now.minus(10, ChronoUnit.DAYS)));
        repository.save(new Product("100%_Cotton Shirt", "Clothing", price("25.00"), 7, 2.5, now.minus(5, ChronoUnit.DAYS)));
        repository.save(new Product("Cotton Socks", "Clothing", price("5.00"), 1, 3.5, now.minus(4, ChronoUnit.DAYS)));
    }

    @Test
    void paginatesAndReportsTotals() throws Exception {
        list("size=2&page=1&sort=id,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(6)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.content[0].name", is("Java Book")));
    }

    @Test
    void pageBeyondLastReturnsEmptyContentWithTotals() throws Exception {
        list("size=10&page=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(6)));
    }

    @Test
    void capsPageSizeAtOneHundred() throws Exception {
        List<Product> more = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            more.add(new Product("Bulk " + i, "Toys", price("1.00"), 1, 1.0, Instant.now()));
        }
        repository.saveAll(more);

        list("size=1000")
                .andExpect(jsonPath("$.size", is(100)))
                .andExpect(jsonPath("$.content", hasSize(100)))
                .andExpect(jsonPath("$.totalElements", is(106)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void sortsByPriceDescending() throws Exception {
        list("sort=price,desc")
                .andExpect(jsonPath("$.content[*].name",
                        contains("Smart Speaker", "Java Book", "Red Lamp", "100%_Cotton Shirt", "Blue Lamp", "Cotton Socks")));
    }

    @Test
    void sortsByMultipleFieldsWithStableTieBreak() throws Exception {
        list("sort=category,asc&sort=price,desc")
                .andExpect(jsonPath("$.content[*].name",
                        contains("Java Book", "100%_Cotton Shirt", "Cotton Socks", "Smart Speaker", "Red Lamp", "Blue Lamp")));
    }

    @Test
    void sortsByEveryProductField() throws Exception {
        for (String field : List.of("id", "name", "category", "price", "stock", "rating", "createdAt")) {
            list("sort=" + field + ",desc").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(6)));
        }
    }

    @Test
    void rejectsSortByUnknownField() throws Exception {
        list("sort=password,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cannot sort by 'password'")));
    }

    @Test
    void filtersByCategory() throws Exception {
        list("category=Home&sort=id").andExpect(jsonPath("$.content[*].name", contains("Blue Lamp", "Red Lamp")))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void filtersByInclusivePriceRange() throws Exception {
        list("minPrice=20&maxPrice=45.50")
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Blue Lamp", "Red Lamp", "Java Book", "100%_Cotton Shirt")));
        list("minPrice=100").andExpect(jsonPath("$.content[*].name", contains("Smart Speaker")));
        list("maxPrice=5").andExpect(jsonPath("$.content[*].name", contains("Cotton Socks")));
    }

    @Test
    void inStockOnlyExcludesZeroStock() throws Exception {
        list("inStock=true").andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Red Lamp"))));
        list("inStock=false").andExpect(jsonPath("$.totalElements", is(6)));
    }

    @Test
    void searchesByNameCaseInsensitively() throws Exception {
        list("q=LAMP").andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Blue Lamp", "Red Lamp")));
        list("q=  lamp  ").andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void nameSearchTreatsWildcardsLiterally() throws Exception {
        list("q=%").andExpect(jsonPath("$.content[*].name", contains("100%_Cotton Shirt")));
        list("q=_").andExpect(jsonPath("$.content[*].name", contains("100%_Cotton Shirt")));
    }

    @Test
    void combinesAllFiltersInOneRequest() throws Exception {
        list("category=Clothing&minPrice=10&maxPrice=30&inStock=true&q=cotton&sort=price,asc&size=5")
                .andExpect(jsonPath("$.content[*].name", contains("100%_Cotton Shirt")))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)));
        list("category=Home&inStock=true&maxPrice=30").andExpect(jsonPath("$.content[*].name", contains("Blue Lamp")));
        list("category=Home&q=speaker").andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    void rejectsInvalidFilterValues() throws Exception {
        list("minPrice=50&maxPrice=10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("minPrice cannot be greater than maxPrice")));
        list("minPrice=abc").andExpect(status().isBadRequest());
    }

    @Test
    void repeatedLookupsOfSameProductQueryDatabaseOnce() throws Exception {
        Statistics statistics = statistics();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/products/" + blueLamp.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Blue Lamp")));
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void withoutCacheEveryLookupQueriesDatabase() throws Exception {
        Statistics statistics = statistics();

        for (int i = 0; i < 5; i++) {
            cacheManager.getCache(ProductService.CACHE).clear();
            mockMvc.perform(get("/api/products/" + blueLamp.getId())).andExpect(status().isOk());
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void neverServesStaleDataAfterUpdate() throws Exception {
        String url = "/api/products/" + blueLamp.getId();
        mockMvc.perform(get(url)).andExpect(jsonPath("$.name", is("Blue Lamp")));

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Lamp")));

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.name", is("Updated Lamp")))
                .andExpect(jsonPath("$.price", is(99.99)))
                .andExpect(jsonPath("$.stock", is(8)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void neverServesDeletedProductFromCache() throws Exception {
        String url = "/api/products/" + blueLamp.getId();
        mockMvc.perform(get(url)).andExpect(status().isOk());

        mockMvc.perform(delete(url)).andExpect(status().isNoContent());

        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void unknownProductReturnsNotFoundEveryTime() throws Exception {
        mockMvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Product 999999 not found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsInvalidUpdateAndUnknownTargets() throws Exception {
        mockMvc.perform(put("/api/products/" + blueLamp.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"category\":\"Home\",\"price\":-1,\"stock\":-2,\"rating\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name", is("name is required")))
                .andExpect(jsonPath("$.fieldErrors.price", is("price cannot be negative")))
                .andExpect(jsonPath("$.fieldErrors.stock", is("stock cannot be negative")))
                .andExpect(jsonPath("$.fieldErrors.rating", is("rating must be between 0 and 5")));
        mockMvc.perform(put("/api/products/999999").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotModifyProducts() throws Exception {
        mockMvc.perform(put("/api/products/" + blueLamp.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/" + blueLamp.getId())).andExpect(status().isForbidden());
        assertThat(repository.existsById(blueLamp.getId())).isTrue();
    }

    @Test
    void seederCreatesOneHundredProductsOnlyOnce() throws Exception {
        repository.deleteAll();

        seeder.run(null);
        seeder.run(null);

        assertThat(repository.count()).isEqualTo(100);
        assertThat(repository.findAll()).anyMatch(p -> p.getStock() == 0).anyMatch(p -> p.getStock() > 0);
        assertThat(repository.findAll().stream().map(Product::getCategory).distinct().count()).isGreaterThan(1);
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/products?" + query));
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private BigDecimal price(String value) {
        return new BigDecimal(value);
    }
}
