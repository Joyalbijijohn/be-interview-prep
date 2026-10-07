package com.interview.prep.order;

import com.interview.prep.common.ConflictException;
import com.interview.prep.common.InvalidRequestException;
import com.interview.prep.common.NotFoundException;
import com.interview.prep.product.ProductRepository;
import com.interview.prep.product.ProductService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final int MAX_KEY_LENGTH = 100;

    public record PlacedOrder(OrderResponse order, boolean created) {
    }

    private final OrderRepository orders;
    private final ProductRepository products;
    private final TransactionTemplate transaction;
    private final CacheManager cacheManager;

    public OrderService(OrderRepository orders, ProductRepository products, TransactionTemplate transaction,
            CacheManager cacheManager) {
        this.orders = orders;
        this.products = products;
        this.transaction = transaction;
        this.cacheManager = cacheManager;
    }

    public PlacedOrder place(Long userId, String idempotencyKey, OrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters");
        }
        Map<Long, Integer> quantities = merge(request);
        String hash = fingerprint(quantities);

        OrderResponse existing = findExisting(userId, idempotencyKey, hash);
        if (existing != null) {
            return new PlacedOrder(existing, false);
        }
        try {
            OrderResponse created = transaction.execute(status -> reserve(userId, idempotencyKey, hash, quantities));
            evictFromCache(quantities.keySet());
            return new PlacedOrder(created, true);
        } catch (DataIntegrityViolationException e) {
            OrderResponse winner = findExisting(userId, idempotencyKey, hash);
            if (winner == null) {
                throw e;
            }
            return new PlacedOrder(winner, false);
        }
    }

    public OrderResponse cancel(Long userId, Long orderId) {
        Map<Long, Integer> released = new TreeMap<>();
        OrderResponse cancelled = transaction.execute(status -> {
            Order order = orders.findByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
            Map<Long, Integer> items = new TreeMap<>();
            order.getItems().forEach(item -> items.put(item.getProductId(), item.getQuantity()));
            if (orders.transition(orderId, OrderStatus.PLACED, OrderStatus.CANCELLED) == 1) {
                items.forEach(products::incrementStock);
                released.putAll(items);
            }
            return OrderResponse.from(orders.findByIdAndUserId(orderId, userId).orElseThrow());
        });
        evictFromCache(released.keySet());
        return cancelled;
    }

    private OrderResponse reserve(Long userId, String idempotencyKey, String hash, Map<Long, Integer> quantities) {
        Order order = orders.saveAndFlush(new Order(userId, idempotencyKey, hash));
        quantities.forEach((productId, quantity) -> {
            if (products.decrementStock(productId, quantity) == 0) {
                throw products.existsById(productId)
                        ? new ConflictException("Insufficient stock for product " + productId)
                        : new NotFoundException("Product " + productId + " not found");
            }
            order.addItem(productId, quantity);
        });
        return OrderResponse.from(order);
    }

    private OrderResponse findExisting(Long userId, String idempotencyKey, String hash) {
        return transaction.execute(status -> orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(order -> {
                    if (!order.getRequestHash().equals(hash)) {
                        throw new ConflictException("Idempotency-Key was already used with a different request");
                    }
                    return OrderResponse.from(order);
                })
                .orElse(null));
    }

    private Map<Long, Integer> merge(OrderRequest request) {
        Map<Long, Integer> quantities = new TreeMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities;
    }

    private String fingerprint(Map<Long, Integer> quantities) {
        StringBuilder canonical = new StringBuilder();
        quantities.forEach((productId, quantity) -> canonical.append(productId).append(':').append(quantity).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void evictFromCache(Collection<Long> productIds) {
        Cache cache = cacheManager.getCache(ProductService.CACHE);
        if (cache != null) {
            productIds.forEach(cache::evict);
        }
    }
}
