package com.interview.prep.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductSeeder implements ApplicationRunner {

    static final int SEED_COUNT = 100;
    private static final List<String> CATEGORIES = List.of("Electronics", "Books", "Home", "Toys", "Clothing");
    private static final List<String> ADJECTIVES = List.of("Compact", "Premium", "Classic", "Smart", "Eco");
    private static final List<String> NOUNS = List.of("Lamp", "Backpack", "Speaker", "Notebook", "Mug", "Jacket");

    private final ProductRepository repository;

    public ProductSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Random random = new Random(42);
        Instant now = Instant.now();
        List<Product> products = new ArrayList<>(SEED_COUNT);
        for (int i = 0; i < SEED_COUNT; i++) {
            String name = ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " "
                    + NOUNS.get(random.nextInt(NOUNS.size())) + " " + (i + 1);
            BigDecimal price = BigDecimal.valueOf(5 + random.nextDouble() * 495).setScale(2, RoundingMode.HALF_UP);
            int stock = i % 10 == 0 ? 0 : random.nextInt(200) + 1;
            double rating = Math.round((1 + random.nextDouble() * 4) * 10) / 10.0;
            products.add(new Product(name, CATEGORIES.get(i % CATEGORIES.size()), price, stock, rating,
                    now.minus(random.nextInt(100), ChronoUnit.DAYS)));
        }
        repository.saveAll(products);
    }
}
