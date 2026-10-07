package com.interview.prep.product;

import com.interview.prep.common.InvalidRequestException;
import com.interview.prep.common.NotFoundException;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    public static final String CACHE = "products";
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "category", "price", "stock", "rating", "createdAt");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new InvalidRequestException("minPrice cannot be greater than maxPrice");
        }
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), validated(pageable.getSort()));
        return PageResponse.from(repository.findAll(ProductSpecifications.from(filter), stable)
                .map(ProductResponse::from));
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        product.update(request.name(), request.category(), request.price(), request.stock(), request.rating());
        return ProductResponse.from(repository.save(product));
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Product find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private Sort validated(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty() + "'");
            }
        }
        return sort.getOrderFor("id") == null ? sort.and(Sort.by("id")) : sort;
    }
}
