package dev.techmart.catalog.repository;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Bo loc cua GET /api/v1/products.
 *
 * @param spec cac cap key/value loc tren cot JSONB, vd {@code {"ram": "16GB"}}.
 *             Key da duoc whitelist o tang web truoc khi toi day.
 */
public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        Long brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Map<String, String> spec,
        Boolean inStock
) {
    public boolean hasSpecFilter() {
        return spec != null && !spec.isEmpty();
    }
}
