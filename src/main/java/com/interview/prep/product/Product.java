package com.interview.prep.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(indexes = {
        @Index(name = "idx_product_category", columnList = "category"),
        @Index(name = "idx_product_price", columnList = "price"),
        @Index(name = "idx_product_created_at", columnList = "createdAt")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private double rating;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String name, String category, BigDecimal price, int stock, double rating, Instant createdAt) {
        update(name, category, price, stock, rating);
        this.createdAt = createdAt;
    }

    public void update(String name, String category, BigDecimal price, int stock, double rating) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.rating = rating;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public double getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
