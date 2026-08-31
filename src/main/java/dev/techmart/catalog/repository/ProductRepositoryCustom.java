package dev.techmart.catalog.repository;

import dev.techmart.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);
}
